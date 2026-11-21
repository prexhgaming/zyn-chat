/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-chat-android-ai/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zyn.chat.app.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zyn.chat.app.data.providers.ApiKeyStore
import com.zyn.chat.app.data.providers.ProviderRepository
import com.zyn.chat.app.data.repository.ChatAiRepository
import com.zyn.chat.app.domain.chat.ChatEngine
import com.zyn.chat.app.domain.chat.ConversationRepository
import com.zyn.chat.app.presentation.chat.ChatViewModel
import io.getstream.chat.android.client.ChatClient

/**
 * Factory for creating ChatViewModel with dependencies.
 *
 * @param chatAiRepository Repository that powers the legacy Stream AI path.
 * @param providerRepository Where the configured provider and model selection live.
 * @param apiKeyStore Encrypted key storage for bring-your-own-key answers.
 * @param chatEngine Engine that talks to the person's provider.
 * @param conversations The only source of a conversation's stored history and its title.
 * @param appContext Application context used to resolve picker URIs into attachments.
 * @param chatClient The Stream Chat client instance. Defaults to [ChatClient.instance] if not provided.
 * @param conversationId The optional channel ID (CID) for an existing conversation.
 * If null, a new conversation will be created when the first message is sent.
 */
class ChatViewModelFactory(
    private val chatAiRepository: ChatAiRepository,
    private val providerRepository: ProviderRepository,
    private val apiKeyStore: ApiKeyStore,
    private val chatEngine: ChatEngine,
    private val conversations: ConversationRepository,
    private val localConversationId: String,
    private val appContext: Context,
    private val chatClient: ChatClient = ChatClient.instance(),
    private val conversationId: String?,
) : ViewModelProvider.Factory {

    /**
     * Creates a ChatViewModel instance.
     *
     * @param modelClass The class of the ViewModel to create
     * @return An instance of ChatViewModel
     * @throws IllegalArgumentException if the ViewModel type is not ChatViewModel
     */
    @Suppress("UNCHECKED_CAST", "OutdatedDocumentation")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return ChatViewModel(
            chatClient = chatClient,
            chatAiRepository = chatAiRepository,
            providerRepository = providerRepository,
            apiKeyStore = apiKeyStore,
            chatEngine = chatEngine,
            conversations = conversations,
            localConversationId = localConversationId,
            appContext = appContext,
            conversationId = conversationId,
        ) as T
    }
}
