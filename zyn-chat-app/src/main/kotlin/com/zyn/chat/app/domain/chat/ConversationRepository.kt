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

package com.zyn.chat.app.domain.chat

import com.zyn.chat.app.data.chat.ConversationStore
import com.zyn.chat.app.data.chat.StoredConversation
import com.zyn.chat.app.data.chat.StoredTurn
import com.zyn.chat.app.data.chat.TitleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Key used for a conversation that does not exist yet, so a first exchange is still stored under a
 * stable id instead of being dropped.
 */
public const val UNSAVED_CONVERSATION_ID: String = "local"

/**
 * What the drawer needs to know about a conversation, without its messages.
 *
 * Kept separate from the stored conversation so history cannot leak into list state by accident.
 */
public data class ConversationMeta(
    val id: String,
    val title: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val updatedAt: Long = 0L,
)

/**
 * The one place a conversation's history is read from or written to.
 *
 * A request's messages are always built by [history] for that one conversation id — there is no
 * shared buffer and no cross-conversation cache to grow stale, so an answer can never be asked with,
 * or written into, another chat's history. Callers observe [revision] to know that titles or
 * conversations changed.
 */
public class ConversationRepository(
    private val store: ConversationStore,
) {

    private val _revision = MutableStateFlow(0L)

    /** Bumped whenever a conversation is stored, renamed or deleted. */
    public val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Conversation titles and models for the drawer, most recently used first. */
    public suspend fun metadata(): List<ConversationMeta> = withContext(Dispatchers.IO) {
        store.list().map { conversation -> conversation.toMeta() }
    }

    /** The turns of one conversation, oldest first, ready to be sent as a request. */
    public suspend fun history(conversationId: String): List<ChatTurn> = withContext(Dispatchers.IO) {
        store.get(conversationId)?.turns.orEmpty().map { turn ->
            ChatTurn(role = turn.role.toRole(), content = turn.content)
        }
    }

    /**
     * Appends a question and its answer to one conversation, naming it on the first exchange.
     *
     * @return The stored conversation, so the caller can tell whether a title already existed.
     */
    public suspend fun record(
        conversationId: String,
        question: String,
        answer: String,
        providerId: String?,
        modelId: String?,
    ): StoredConversation = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val previous = store.get(conversationId)
        val localTitle = ConversationTitles.local(question)
        val stored = (previous ?: StoredConversation(id = conversationId, createdAt = now)).copy(
            title = previous?.title?.ifBlank { localTitle } ?: localTitle,
            titleSource = previous?.titleSource ?: TitleSource.Local,
            providerId = providerId ?: previous?.providerId,
            modelId = modelId ?: previous?.modelId,
            updatedAt = now,
            turns = previous?.turns.orEmpty() + StoredTurn(USER_ROLE, question) + StoredTurn(ASSISTANT_ROLE, answer),
        )
        store.save(stored)
        bump()
        stored
    }

    /**
     * Sets a conversation's title.
     *
     * A [TitleSource.Manual] title is final: an automatic title never replaces it, which is what makes
     * renaming stick.
     *
     * @return `true` when the title was written.
     */
    public suspend fun setTitle(
        conversationId: String,
        title: String,
        source: TitleSource,
    ): Boolean = withContext(Dispatchers.IO) {
        val conversation = store.get(conversationId) ?: return@withContext false
        val cleaned = if (source == TitleSource.Manual) title.trim() else ConversationTitles.fromModel(title)
        val isProtected = conversation.titleSource == TitleSource.Manual && source != TitleSource.Manual
        if (cleaned.isBlank() || isProtected) return@withContext false

        store.save(conversation.copy(title = cleaned, titleSource = source))
        bump()
        true
    }

    /** Forgets a conversation and everything it asked. */
    public suspend fun delete(conversationId: String) = withContext(Dispatchers.IO) {
        store.delete(conversationId)
        bump()
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    private fun StoredConversation.toMeta(): ConversationMeta = ConversationMeta(
        id = id,
        title = title,
        providerId = providerId,
        modelId = modelId,
        updatedAt = updatedAt,
    )

    private companion object {
        const val USER_ROLE = "user"
        const val ASSISTANT_ROLE = "assistant"
    }
}

private fun String.toRole(): ChatRole = when (lowercase()) {
    "assistant" -> ChatRole.Assistant
    "system" -> ChatRole.System
    else -> ChatRole.User
}
