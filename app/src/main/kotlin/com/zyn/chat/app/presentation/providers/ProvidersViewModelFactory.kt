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
import com.zyn.chat.app.data.providers.AiProviderClient
import com.zyn.chat.app.data.providers.ApiKeyStore
import com.zyn.chat.app.data.providers.ProviderRepository

/** Builds a [ProvidersViewModel] with its repository, provider client and key store. */
public class ProvidersViewModelFactory(
    private val repository: ProviderRepository,
    private val client: AiProviderClient,
    private val keyStore: ApiKeyStore,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ProvidersViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return ProvidersViewModel(repository, client, keyStore) as T
    }
}
