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

package com.zyn.chat.app.presentation.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyn.chat.app.data.chat.TitleSource
import com.zyn.chat.app.domain.chat.ConversationMeta
import com.zyn.chat.app.domain.chat.ConversationRepository
import com.zyn.chat.app.domain.chat.isChannelId
import io.getstream.chat.android.client.ChatClient
import io.getstream.log.taggedLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the conversation list shown in the drawer.
 *
 * Recents are the conversations **this device has actually used**, read from local storage, newest
 * first. They are deliberately not read from Stream's channel list: the app signs in to a shared
 * demo account, so its channels are other people's seeded sample chats rather than this person's.
 * A chat that does have a Stream channel is stored under that channel's id, so it still lists here
 * and still opens its channel — only channels this device never used are absent.
 *
 * @param chatClient The Stream Chat client, used to delete a channel when its chat is deleted.
 * @param conversations The store of this device's conversations.
 */
public class ConversationListViewModel(
    private val chatClient: ChatClient,
    private val conversations: ConversationRepository,
) : ViewModel() {

    private val logger by taggedLogger()

    private val _uiState = MutableStateFlow(ConversationListState())
    public val uiState: StateFlow<ConversationListState> = _uiState.asStateFlow()

    init {
        observeStored()
    }

    /**
     * Renames a conversation.
     *
     * The stored title is the drawer's source of truth, and a manual title is never replaced by an
     * automatic one, so renaming a channel-backed chat sticks too.
     *
     * @param conversationId The conversation to rename.
     * @param title The new title; blank input is ignored.
     */
    public fun rename(conversationId: String, title: String) {
        viewModelScope.launch {
            conversations.setTitle(conversationId, title, TitleSource.Manual)
        }
    }

    /**
     * Deletes a conversation: its stored history, and its Stream channel when it has one.
     *
     * @param conversationId The conversation to delete.
     */
    public fun delete(conversationId: String) {
        viewModelScope.launch {
            conversations.delete(conversationId)

            // A stored chat keyed by a channel id still has a channel to remove; a locally answered
            // one has none, and its id must never be handed to the client.
            if (isChannelId(conversationId)) {
                chatClient.channel(conversationId).delete().enqueue { result ->
                    result.onError { error ->
                        logger.e { "Failed to delete channel $conversationId: ${error.message}" }
                    }
                }
            }
        }
    }

    private fun observeStored() {
        conversations.revision
            .onEach { stored ->
                _uiState.update { state ->
                    state.copy(
                        conversations = conversations.metadata().map(ConversationMeta::toConversation),
                        isLoading = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }
}

/**
 * A stored conversation as the drawer shows it.
 *
 * The storage id stays the identity, and the channel id is carried separately, because only the
 * latter may be given to Stream.
 */
private fun ConversationMeta.toConversation(): Conversation = Conversation(
    id = id,
    title = title,
    updatedAt = updatedAt,
    channelId = id.takeIf(::isChannelId),
)
