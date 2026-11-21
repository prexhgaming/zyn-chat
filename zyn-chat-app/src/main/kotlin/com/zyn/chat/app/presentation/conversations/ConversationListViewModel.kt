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

package com.zyn.chat.app.presentation.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyn.chat.app.data.chat.TitleSource
import com.zyn.chat.app.domain.chat.ConversationMeta
import com.zyn.chat.app.domain.chat.ConversationRepository
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.api.models.QueryChannelsRequest
import io.getstream.chat.android.client.api.state.queryChannelsAsState
import io.getstream.chat.android.models.Channel
import io.getstream.chat.android.models.Filters
import io.getstream.chat.android.models.querysort.QuerySortByField
import io.getstream.log.taggedLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the conversation list shown in the drawer.
 *
 * The list is the union of two sources: channels from Stream, and the conversations this device
 * answers with the person's own provider. A stored title or model always wins for a conversation,
 * which is what makes a rename stick.
 *
 * @param chatClient The Stream Chat client instance.
 * @param conversations The store of locally answered conversations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class ConversationListViewModel(
    private val chatClient: ChatClient,
    private val conversations: ConversationRepository,
) : ViewModel() {

    private val logger by taggedLogger()

    private val channelConversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val storedConversations = MutableStateFlow<List<ConversationMeta>>(emptyList())

    private val _uiState = MutableStateFlow(ConversationListState())
    public val uiState: StateFlow<ConversationListState> = _uiState.asStateFlow()

    init {
        observeChannels()
        observeStored()
        observeMerged()
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

            if (conversationId.contains(CID_SEPARATOR)) {
                chatClient.channel(conversationId).delete().enqueue { result ->
                    result.onError { error ->
                        logger.e { "Failed to delete channel $conversationId: ${error.message}" }
                    }
                }
            }
        }
    }

    private fun observeChannels() {
        val currentUserId = chatClient.getCurrentUser()?.id ?: run {
            logger.d { "No current user, cannot load channels" }
            return
        }

        val request = QueryChannelsRequest(
            filter = Filters.and(
                Filters.eq("type", "messaging"),
                Filters.`in`("members", listOf(currentUserId)),
                Filters.or(
                    Filters.notExists("draft"),
                    Filters.eq("draft", false),
                ),
            ),
            querySort = QuerySortByField.descByName("last_updated"),
            limit = CHANNEL_LIMIT,
            messageLimit = 1,
        )

        chatClient.queryChannelsAsState(request = request, coroutineScope = viewModelScope)
            .filterNotNull()
            .flatMapLatest { queryChannelsState -> queryChannelsState.channels.filterNotNull() }
            .onEach { channels ->
                logger.d { "Loaded ${channels.size} channels from Stream Chat" }
                channelConversations.value = channels.map(Channel::toConversation)
            }
            .launchIn(viewModelScope)
    }

    private fun observeStored() {
        conversations.revision
            .onEach { storedConversations.value = conversations.metadata() }
            .launchIn(viewModelScope)
    }

    private fun observeMerged() {
        combine(channelConversations, storedConversations, ::merge)
            .onEach { merged ->
                _uiState.update { state ->
                    state.copy(conversations = merged, isLoading = false)
                }
            }
            .launchIn(viewModelScope)
    }

    /** Channels plus locally answered conversations, with stored titles taking precedence. */
    private fun merge(
        channels: List<Conversation>,
        stored: List<ConversationMeta>,
    ): List<Conversation> {
        val overrides = stored.associateBy(ConversationMeta::id)

        val fromChannels = channels.map { channel ->
            val storedMeta = overrides[channel.id]
            channel.copy(
                title = storedMeta?.title?.takeIf(String::isNotBlank) ?: channel.title,
                updatedAt = channel.updatedAt ?: storedMeta?.updatedAt,
                modelSlug = storedMeta?.modelId?.substringAfterLast(CID_SEPARATOR),
            )
        }

        val channelIds = fromChannels.mapTo(mutableSetOf(), Conversation::id)
        val localOnly = stored
            .filterNot { meta -> meta.id in channelIds }
            .map { meta ->
                Conversation(
                    id = meta.id,
                    title = meta.title,
                    updatedAt = meta.updatedAt,
                    modelSlug = meta.modelId?.substringAfterLast(CID_SEPARATOR),
                )
            }

        return (fromChannels + localOnly).sortedByDescending { conversation -> conversation.updatedAt ?: 0L }
    }

    private companion object {
        const val CHANNEL_LIMIT = 30
        const val CID_SEPARATOR = ':'
    }
}

private fun Channel.toConversation(): Conversation {
    val title = name.takeIf(String::isNotBlank) ?: "Unnamed Chat"

    return Conversation(
        id = cid,
        title = title,
        updatedAt = lastUpdated?.time,
    )
}
