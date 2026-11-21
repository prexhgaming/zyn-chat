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
 * Single source of truth for the provider configuration and the current model selection.
 *
 * The application starts with **no providers registered**: this class never invents providers or
 * models, it only stores what has been registered through [registerProvider]. Provider integrations
 * register their models here once they are wired up.
 *
 * The selection is persisted in `SharedPreferences` so the person's choice survives restarts.
 *
 * @param context Any context; the application context is used.
 */
public class ProviderRepository(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        ProvidersState(
            selectedProviderId = preferences.getString(KEY_PROVIDER_ID, null),
            selectedModelId = preferences.getString(KEY_MODEL_ID, null),
        ),
    )

    /** The current provider configuration and selection. */
    public val state: StateFlow<ProvidersState> = _state.asStateFlow()

    /**
     * Registers a provider, or replaces the one with the same id.
     *
     * @param provider The provider to register.
     */
    public fun registerProvider(provider: AiProvider) {
        _state.update { current ->
            val providers = current.providers.replace(provider)
            current.copy(
                providers = providers,
                selectedProviderId = current.selectedProviderId.orDefault(providers),
            ).withValidSelection()
        }
        persistSelection()
    }

    /** Registers several providers in one go. */
    public fun registerProviders(providers: List<AiProvider>) {
        providers.forEach(::registerProvider)
    }

    /** Replaces an already registered provider, e.g. after its model list changed. */
    public fun updateProvider(provider: AiProvider) {
        registerProvider(provider)
    }

    /**
     * Enables or disables a provider without unregistering it.
     *
     * @param providerId Id of the provider to toggle.
     * @param enabled Whether the provider takes part in selection.
     */
    public fun setProviderEnabled(providerId: String, enabled: Boolean) {
        _state.update { current ->
            val providers = current.providers.map { provider ->
                if (provider.id == providerId) provider.copy(enabled = enabled) else provider
            }
            current.copy(providers = providers).withValidSelection()
        }
        persistSelection()
    }

    /**
     * Removes a provider and everything it offered.
     *
     * @param providerId Id of the provider to remove.
     */
    public fun removeProvider(providerId: String) {
        _state.update { current ->
            val providers = current.providers.filterNot { it.id == providerId }
            current.copy(
                providers = providers,
                selectedProviderId = current.selectedProviderId.orDefault(providers),
            ).withValidSelection()
        }
        persistSelection()
    }

    /**
     * Makes [providerId] the provider shown in the model picker.
     *
     * @param providerId Id of an enabled provider.
     */
    public fun selectProvider(providerId: String) {
        _state.update { current -> current.copy(selectedProviderId = providerId) }
        persistSelection()
    }

    /**
     * Selects the model the application will use, and the provider that owns it.
     *
     * @param modelId Id of the model to select.
     */
    public fun selectModel(modelId: String) {
        _state.update { current ->
            val model = current.allModels.firstOrNull { it.id == modelId } ?: return@update current
            current.copy(
                selectedModelId = model.id,
                selectedProviderId = model.providerId,
            )
        }
        persistSelection()
    }

    private fun persistSelection() {
        val selection = _state.value
        preferences.edit()
            .putString(KEY_PROVIDER_ID, selection.selectedProviderId)
            .putString(KEY_MODEL_ID, selection.selectedModelId)
            .apply()
    }

    /** A selection is kept only while it still resolves to a model that can be used. */
    private fun ProvidersState.withValidSelection(): ProvidersState =
        if (selectedModel == null) copy(selectedModelId = null) else this

    private fun List<AiProvider>.replace(provider: AiProvider): List<AiProvider> {
        val index = indexOfFirst { it.id == provider.id }
        return if (index == -1) this + provider else toMutableList().apply { set(index, provider) }
    }

    private fun String?.orDefault(providers: List<AiProvider>): String? =
        this?.takeIf { id -> providers.any { it.id == id } } ?: providers.firstOrNull()?.id

    private companion object {
        const val PREFERENCES_NAME = "zyn_provider_selection"
        const val KEY_PROVIDER_ID = "selected_provider_id"
        const val KEY_MODEL_ID = "selected_model_id"
    }
}
