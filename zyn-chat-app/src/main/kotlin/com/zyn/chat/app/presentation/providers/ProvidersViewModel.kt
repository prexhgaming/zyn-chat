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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zyn.chat.app.data.providers.AiProvider
import com.zyn.chat.app.data.providers.ProviderRepository
import com.zyn.chat.app.data.providers.ProvidersState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the provider configuration and the model selection to the UI.
 *
 * The drawer and the model picker both read this state and call these actions, so provider
 * management has exactly one implementation and one source of truth.
 *
 * @param repository Backing store for providers and the persisted selection.
 */
public class ProvidersViewModel(
    private val repository: ProviderRepository,
) : ViewModel() {

    /** The provider configuration and selection, observed by the UI. */
    public val uiState: StateFlow<ProvidersState> = repository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = repository.state.value,
    )

    /** Registers a provider, or replaces the existing one with the same id. */
    public fun registerProvider(provider: AiProvider) {
        repository.registerProvider(provider)
    }

    /** Enables or disables a provider without unregistering it. */
    public fun setProviderEnabled(providerId: String, enabled: Boolean) {
        repository.setProviderEnabled(providerId, enabled)
    }

    /** Removes a provider and the models it offered. */
    public fun removeProvider(providerId: String) {
        repository.removeProvider(providerId)
    }

    /** Shows the models of [providerId] in the picker. */
    public fun selectProvider(providerId: String) {
        repository.selectProvider(providerId)
    }

    /** Makes [modelId] the model the application uses. */
    public fun selectModel(modelId: String) {
        repository.selectModel(modelId)
    }
}

/** Builds a [ProvidersViewModel] with its repository. */
public class ProvidersViewModelFactory(
    private val repository: ProviderRepository,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ProvidersViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return ProvidersViewModel(repository) as T
    }
}
