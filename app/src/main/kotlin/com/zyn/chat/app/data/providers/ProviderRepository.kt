/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://github.com/GetStream/stream-chat-android-ai/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zyn.chat.app.data.providers

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for the provider configuration, its stored keys and the current selection.
 *
 * The application starts with **no providers**. Nothing here invents a provider or a model list: the
 * only way in is [addProvider], driven by an explicit user action, and models only appear through a
 * real sync against the provider ([setModels]).
 *
 * Keys are written through to [ApiKeyStore] and only a masked hint is kept in the provider record, so
 * the plain key never enters the persisted state, the UI or a log.
 *
 * @param context Any context; the application context is used.
 * @param keyStore Where keys are encrypted. Injectable for tests.
 * @param imageAbilities Where refusals of pictures are remembered. Injectable for tests.
 */
public class ProviderRepository(
    context: Context,
    private val keyStore: ApiKeyStore = ApiKeyStore(context),
    private val imageAbilities: ImageAbilityStore = ImageAbilityStore(context),
) {

    private val storage = ProviderStorage(context)

    private val _state = MutableStateFlow(
        ProvidersState(
            providers = storage.loadProviders(),
            selectedProviderId = storage.loadProviderId(),
            selectedModelId = storage.loadModelId(),
        ).withValidSelection(),
    )

    /** The current provider configuration and selection. */
    public val state: StateFlow<ProvidersState> = _state.asStateFlow()

    /**
     * Adds a provider from the catalog, storing its key when one was supplied.
     *
     * @param known Catalog entry that describes the endpoint.
     * @param name Display name; matches the catalog unless the person renamed it.
     * @param baseUrl Endpoint root, editable for local and custom providers.
     * @param key The person's API key, or `null` for providers that need none.
     * @return Success, or a failure when the key could not be stored.
     */
    public fun addProvider(
        known: KnownProvider,
        name: String,
        baseUrl: String,
        key: String?,
    ): Result<Unit> {
        if (!key.isNullOrBlank() && keyStore.save(known.id, key) != true) {
            return Result.failure(IllegalStateException("Could not store the key securely"))
        }

        val provider = AiProvider(
            id = known.id,
            name = name.ifBlank { known.displayName },
            endpoint = ProviderEndpoint(
                baseUrl = baseUrl.ifBlank { known.baseUrl },
                auth = known.auth,
                kind = known.kind,
                keyRequired = known.keyRequired,
                editableBaseUrl = known.editableBaseUrl,
                docsUrl = known.docsUrl,
            ),
            connection = ProviderConnection(keyHint = keyStore.hint(known.id)),
        )
        update { it.copy(providers = it.providers.replacing(provider)) }
        return Result.success(Unit)
    }

    /** Replaces a provider with an edited copy of itself. */
    public fun saveProvider(provider: AiProvider) {
        update { current -> current.copy(providers = current.providers.replacing(provider)) }
    }

    /**
     * Enables or disables a provider without losing its key or its models.
     *
     * @param providerId Provider to toggle.
     * @param enabled Whether it takes part in selection.
     */
    public fun setEnabled(providerId: String, enabled: Boolean) {
        updateProvider(providerId) { provider ->
            provider.copy(connection = provider.connection.copy(enabled = enabled))
        }
    }

    /**
     * Removes a provider, its models and its stored key.
     *
     * @param providerId Provider to remove.
     */
    public fun removeProvider(providerId: String) {
        keyStore.delete(providerId)
        update { current -> current.copy(providers = current.providers.without(providerId)) }
    }

    /**
     * Stores a new key for a provider, replacing the previous one.
     *
     * @param providerId Provider the key belongs to.
     * @param key The key as the person typed it.
     * @return Success, or a failure when the key could not be stored.
     */
    public fun replaceKey(providerId: String, key: String): Result<Unit> {
        if (key.isBlank() || keyStore.save(providerId, key) != true) {
            return Result.failure(IllegalStateException("Could not store the key securely"))
        }
        updateProvider(providerId) { provider ->
            provider.copy(connection = provider.connection.copy(keyHint = keyStore.hint(providerId)))
        }
        return Result.success(Unit)
    }

    /**
     * Records the models a provider reported, and when.
     *
     * @param providerId Provider the models belong to.
     * @param models Models from the provider, in display order.
     * @param syncedAt When the sync happened.
     */
    public fun setModels(providerId: String, models: List<AiModel>, syncedAt: Long) {
        updateProvider(providerId) { provider ->
            provider.copy(
                models = models.map { model -> model.withLearnedAbilities(imageAbilities, providerId) },
                connection = provider.connection.copy(lastSyncedAt = syncedAt),
            )
        }
    }

    /**
     * Remembers that a provider refused a picture for one of its models, and stops offering the model
     * pictures from now on.
     *
     * The refusal is folded into the model's abilities, which is what the composer and the send path
     * both read, so they cannot disagree about it.
     *
     * @param providerId Provider that refused.
     * @param modelId Model that was refused.
     */
    public fun rememberRefusedPictures(providerId: String, modelId: String) {
        imageAbilities.refuse(providerId = providerId, modelId = modelId)
        updateProvider(providerId) { provider ->
            provider.copy(
                models = provider.models.map { model ->
                    model.withLearnedAbilities(imageAbilities, providerId)
                },
            )
        }
    }

    /**
     * Shows the models of [providerId] in the picker.
     *
     * @param providerId An enabled provider.
     */
    public fun selectProvider(providerId: String) {
        update { current -> current.copy(selectedProviderId = providerId) }
    }

    /**
     * Selects the model the application will use, and the provider that owns it.
     *
     * @param modelId Id of the model to select.
     */
    public fun selectModel(modelId: String) {
        val model = _state.value.allModels.firstOrNull { it.id == modelId } ?: return
        update { current ->
            current.copy(selectedModelId = model.id, selectedProviderId = model.providerId)
        }
    }

    /** Applies an edit to one provider, leaving the rest untouched. */
    private fun updateProvider(providerId: String, transform: (AiProvider) -> AiProvider) {
        val provider = _state.value.providers.firstOrNull { it.id == providerId } ?: return
        saveProvider(transform(provider))
    }

    /**
     * Applies a change, keeps the selection valid and writes it to storage. Every mutation goes
     * through here, so the persisted state can never drift from the observed state.
     */
    private fun update(transform: (ProvidersState) -> ProvidersState) {
        val next = transform(_state.value).withValidSelection()
        _state.update { next }
        storage.saveProviders(next.providers)
        storage.saveSelection(next.selectedProviderId, next.selectedModelId)
    }
}
