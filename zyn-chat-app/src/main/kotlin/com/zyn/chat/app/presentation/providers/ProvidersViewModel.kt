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

package com.zyn.chat.app.presentation.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyn.chat.app.data.providers.AiModel
import com.zyn.chat.app.data.providers.AiProvider
import com.zyn.chat.app.data.providers.AiProviderClient
import com.zyn.chat.app.data.providers.ApiKeyStore
import com.zyn.chat.app.data.providers.KnownProvider
import com.zyn.chat.app.data.providers.ProviderConnection
import com.zyn.chat.app.data.providers.ProviderEndpoint
import com.zyn.chat.app.data.providers.ProviderError
import com.zyn.chat.app.data.providers.ProviderException
import com.zyn.chat.app.data.providers.ProviderRepository
import com.zyn.chat.app.data.providers.ProvidersState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Progress and outcome of a provider operation that talks to the network (adding a provider, or
 * syncing its model list).
 *
 * These are kept apart from [ProvidersState] because they describe one in-flight action rather than
 * the saved configuration, so the UI can show inline progress or a snackbar.
 *
 * @param inProgress Whether an operation is running.
 * @param providerId Provider the operation targets.
 * @param error Why the last operation failed.
 * @param modelsFound How many models the last successful sync returned.
 */
public data class ProviderSyncState(
    public val inProgress: Boolean = false,
    public val providerId: String? = null,
    public val error: ProviderError? = null,
    public val modelsFound: Int? = null,
) {
    /** Whether anything is running. */
    public val isBusy: Boolean get() = inProgress
}

/**
 * Exposes the provider configuration and drives the operations that touch the network.
 *
 * Adding a provider is **validated before it is saved**: the key is used against the provider's real
 * `/models` endpoint, so a typo is reported immediately instead of failing on the first message. A
 * provider that needs no key (a local server) is validated the same way, with a `null` key.
 *
 * Read access to a stored key goes through [ApiKeyStore], because [ProviderRepository] deliberately
 * only ever writes keys: nothing else in the app needs to read one back.
 *
 * @param repository Saved providers and the current selection.
 * @param client HTTP client for provider endpoints.
 * @param keyStore Encrypted key storage.
 * @param now Clock, injectable so tests can pin the sync timestamp.
 */
public class ProvidersViewModel(
    private val repository: ProviderRepository,
    private val client: AiProviderClient,
    private val keyStore: ApiKeyStore,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** The provider configuration and selection, observed by the UI. */
    public val uiState: StateFlow<ProvidersState> = repository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = repository.state.value,
    )

    private val _syncState = MutableStateFlow(ProviderSyncState())

    /** Progress of the current network operation. */
    public val syncState: StateFlow<ProviderSyncState> = _syncState.asStateFlow()

    /**
     * Validates a provider against its endpoint, then saves it with the models it reported.
     *
     * @param known Catalog entry describing the endpoint.
     * @param name Display name for the provider.
     * @param baseUrl Endpoint root; editable for local and custom providers.
     * @param key The person's key, or `null` when the provider needs none.
     */
    public fun addProvider(
        known: KnownProvider,
        name: String,
        baseUrl: String,
        key: String?,
    ) {
        if (_syncState.value.inProgress) return

        val candidate = AiProvider(
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
            connection = ProviderConnection(),
        )

        viewModelScope.launch {
            val models = validate(candidate, key) ?: return@launch
            repository.addProvider(known, candidate.name, candidate.endpoint.baseUrl, key)
                .onFailure { _syncState.update { ProviderSyncState(error = ProviderError.Unknown) } }
                .onSuccess { saveModels(candidate.id, models, selectFirstModel = true) }
        }
    }

    /**
     * Enables or disables a provider without losing its key or its models.
     *
     * @param providerId Provider to toggle.
     * @param enabled Whether it takes part in selection.
     */
    public fun setEnabled(providerId: String, enabled: Boolean) {
        repository.setEnabled(providerId, enabled)
    }

    /**
     * Removes a provider, its models and its stored key.
     *
     * @param providerId Provider to remove.
     */
    public fun removeProvider(providerId: String) {
        repository.removeProvider(providerId)
    }

    /**
     * Replaces a provider's key, validating it against the endpoint first.
     *
     * @param providerId Provider the key belongs to.
     * @param key The new key.
     */
    public fun replaceKey(providerId: String, key: String) {
        val provider = repository.state.value.providers.firstOrNull { it.id == providerId } ?: return
        if (_syncState.value.inProgress) return

        viewModelScope.launch {
            val models = validate(provider, key, targetId = providerId) ?: return@launch
            repository.replaceKey(providerId, key)
            saveModels(providerId, models, selectFirstModel = false)
        }
    }

    /**
     * Re-syncs the models of one provider.
     *
     * @param providerId Provider to refresh.
     */
    public fun refreshModels(providerId: String) {
        val provider = repository.state.value.providers.firstOrNull { it.id == providerId } ?: return
        if (_syncState.value.inProgress) return

        viewModelScope.launch {
            val models = validate(provider, keyStore.get(providerId), targetId = providerId)
                ?: return@launch
            saveModels(providerId, models, selectFirstModel = false)
        }
    }

    /**
     * Shows the models of [providerId] in the picker.
     *
     * @param providerId An enabled provider.
     */
    public fun selectProvider(providerId: String) {
        repository.selectProvider(providerId)
    }

    /**
     * Makes [modelId] the model the application uses.
     *
     * @param modelId Id of the model to select.
     */
    public fun selectModel(modelId: String) {
        repository.selectModel(modelId)
    }

    /** Clears the outcome once the UI has shown it. */
    public fun consumeResult() {
        _syncState.update { state -> state.copy(error = null, modelsFound = null) }
    }

    /** Runs the real `/models` call, reporting progress. `null` means the operation failed. */
    private suspend fun validate(
        provider: AiProvider,
        key: String?,
        targetId: String = provider.id,
    ): List<AiModel>? {
        _syncState.update { ProviderSyncState(inProgress = true, providerId = targetId) }

        return client.listModels(provider, key)
            .onSuccess { models ->
                _syncState.update {
                    ProviderSyncState(modelsFound = models.size, providerId = targetId)
                }
            }
            .onFailure { failure ->
                val reason = (failure as? ProviderException)?.reason ?: ProviderError.Unknown
                _syncState.update { ProviderSyncState(providerId = targetId, error = reason) }
            }
            .getOrNull()
    }

    /** Stores a synced model list, optionally adopting its first model as the selection. */
    private fun saveModels(providerId: String, models: List<AiModel>, selectFirstModel: Boolean) {
        repository.setModels(providerId, models, now())
        if (selectFirstModel && repository.state.value.selectedModelId == null) {
            models.firstOrNull()?.let { model -> repository.selectModel(model.id) }
        }
    }
}
