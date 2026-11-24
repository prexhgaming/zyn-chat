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
import com.zyn.chat.app.data.chat.StoredAttachment
import com.zyn.chat.app.data.chat.StoredConversation
import com.zyn.chat.app.data.chat.StoredTurn
import com.zyn.chat.app.data.chat.TitleSource
import com.zyn.chat.app.data.chat.toStorageName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * A fresh id for a conversation that is being started.
 *
 * Each new chat gets its own id, so two chats started in the same session never share stored
 * history — a single shared fallback key would let one chat see another's messages.
 */
public fun newConversationId(): String = UUID.randomUUID().toString()

/**
 * Whether [value] is a Stream channel id, which is the only kind of id the client accepts.
 *
 * A channel id is `type:id`; a stored conversation id is a bare uuid. This check exists because
 * passing the latter to the client throws, and it is deliberately lenient about the id itself.
 */
public fun isChannelId(value: String?): Boolean = value != null && value.contains(CHANNEL_ID_SEPARATOR)

private const val CHANNEL_ID_SEPARATOR = ':'

/**
 * What the drawer needs to know about a conversation, without its messages.
 *
 * Kept separate from the stored conversation so history cannot leak into list state by accident.
 */
public data class ConversationMeta(
    val id: String,
    val title: String,
    val titleSource: TitleSource = TitleSource.Local,
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
public class ConversationRepository internal constructor(
    private val store: ConversationStore,
    private val picturesRoot: File? = null,
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
     * The stored turns of one conversation, oldest first, including their attachments.
     *
     * This is what the screen rebuilds from when a conversation is reopened: unlike [history], which
     * exists to be sent to a provider and carries text only, it keeps the pictures the exchange was
     * sent with.
     */
    public suspend fun turns(conversationId: String): List<StoredTurn> = withContext(Dispatchers.IO) {
        store.get(conversationId)?.turns.orEmpty()
    }

    /** One conversation's title and model, or `null` when nothing is stored under that id. */
    public suspend fun conversation(conversationId: String): ConversationMeta? = withContext(Dispatchers.IO) {
        store.get(conversationId)?.toMeta()
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
        questionAttachments: List<StoredAttachment> = emptyList(),
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
            turns = previous?.turns.orEmpty() +
                StoredTurn(USER_ROLE, question, questionAttachments) +
                StoredTurn(ASSISTANT_ROLE, answer),
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

    /** Forgets a conversation, everything it asked, and the pictures kept for it. */
    public suspend fun delete(conversationId: String) = withContext(Dispatchers.IO) {
        store.delete(conversationId)
        // The copies are only reachable through the conversation, so they go with it.
        picturesRoot?.let { root -> File(root, conversationId.toStorageName()).deleteRecursively() }
        bump()
    }

    /**
     * Forgets the last question and answer of a conversation.
     *
     * A regenerated answer replaces the exchange it supersedes, so the pair is dropped first and the
     * new one is recorded normally; without this the conversation would keep both answers.
     *
     * @return `true` when an exchange was removed.
     */
    public suspend fun dropLastExchange(conversationId: String): Boolean = withContext(Dispatchers.IO) {
        val conversation = store.get(conversationId) ?: return@withContext false
        if (conversation.turns.size < EXCHANGE_TURNS) return@withContext false
        store.save(conversation.copy(turns = conversation.turns.dropLast(EXCHANGE_TURNS)))
        bump()
        true
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    private fun StoredConversation.toMeta(): ConversationMeta = ConversationMeta(
        id = id,
        title = title,
        titleSource = titleSource,
        providerId = providerId,
        modelId = modelId,
        updatedAt = updatedAt,
    )

    private companion object {
        const val USER_ROLE = "user"
        const val ASSISTANT_ROLE = "assistant"

        /** A question and its answer make one exchange. */
        const val EXCHANGE_TURNS = 2
    }
}

private fun String.toRole(): ChatRole = when (lowercase()) {
    "assistant" -> ChatRole.Assistant
    "system" -> ChatRole.System
    else -> ChatRole.User
}
