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

package com.zyn.chat.app.presentation.chat

import android.content.Context
import com.zyn.chat.app.R
import com.zyn.chat.app.data.providers.AiModel
import com.zyn.chat.app.data.providers.AiProvider
import com.zyn.chat.app.data.providers.ApiKeyStore
import com.zyn.chat.app.data.providers.ProviderError
import com.zyn.chat.app.data.providers.ProviderException
import com.zyn.chat.app.data.providers.ProviderRepository
import com.zyn.chat.app.domain.chat.ChatChunk
import com.zyn.chat.app.domain.chat.ChatEngine
import com.zyn.chat.app.domain.chat.ChatRequest
import com.zyn.chat.app.domain.chat.ChatTurn
import com.zyn.chat.app.domain.chat.ContextBudget
import com.zyn.chat.app.domain.chat.ContextBudget.estimateTokens
import io.getstream.log.taggedLogger
import kotlinx.coroutines.flow.Flow

/** The provider and model an answer will be asked from. */
internal data class ProviderSelection(
    val provider: AiProvider,
    val model: AiModel,
)

/**
 * Answers questions with the person's own provider instead of the demo backend.
 *
 * It keeps the bring-your-own-key path in one place: which provider to use, how to read its key back,
 * and how to turn a failure into a sentence. The conversation is passed in as [ChatTurn]s, built by the
 * caller from a single conversation, so nothing here is shared between chats and no history can leak
 * from one into another.
 *
 * @param repository Where the selection lives.
 * @param apiKeyStore Encrypted key storage; the only place a key is read.
 * @param chatEngine Engine that talks to the provider.
 * @param context Used to resolve the failure copy.
 */
internal class ProviderAnswerSource(
    private val repository: ProviderRepository,
    private val apiKeyStore: ApiKeyStore,
    private val chatEngine: ChatEngine,
    private val context: Context,
) {

    private val logger by taggedLogger("ContextBudget")

    /** The provider and model to answer with, or `null` when the app must use the legacy path. */
    fun currentSelection(): ProviderSelection? {
        val state = repository.state.value
        val provider = state.selectedProvider ?: return null
        val model = state.selectedModel ?: return null
        return if (provider.isCallable) ProviderSelection(provider, model) else null
    }

    /**
     * Streams an answer for one conversation.
     *
     * @param selection Provider and model to ask.
     * @param conversationId Conversation being answered, or `null` for one not created yet.
     * @param history The conversation so far, oldest first, ending with the new question.
     */
    fun stream(
        selection: ProviderSelection,
        conversationId: String?,
        history: List<ChatTurn>,
    ): Flow<ChatChunk> = chatEngine.stream(request(selection, conversationId, history))

    /** One sentence explaining a failed answer, in the app's language. */
    fun failureText(failure: Throwable): String {
        val reason = (failure as? ProviderException)?.reason ?: ProviderError.Unknown
        return context.getString(
            when (reason) {
                ProviderError.InvalidKey -> R.string.zyn_provider_error_invalid_key
                ProviderError.Forbidden -> R.string.zyn_provider_error_forbidden
                ProviderError.RateLimited -> R.string.zyn_provider_error_rate_limited
                ProviderError.ModelNotFound -> R.string.zyn_provider_error_model_not_found
                ProviderError.ContextTooLarge -> R.string.zyn_provider_error_context_too_large
                ProviderError.ProviderDown -> R.string.zyn_provider_error_provider_down
                ProviderError.NoConnection -> R.string.zyn_provider_error_no_connection
                ProviderError.BadResponse -> R.string.zyn_provider_error_bad_response
                ProviderError.Unknown -> R.string.zyn_provider_error_unknown
            },
        )
    }

    private fun request(
        selection: ProviderSelection,
        conversationId: String?,
        history: List<ChatTurn>,
    ): ChatRequest = ChatRequest(
        conversationId = conversationId ?: LOCAL_CONVERSATION,
        provider = selection.provider,
        model = selection.model,
        apiKey = apiKeyStore.get(selection.provider.id),
        systemPrompt = SYSTEM_PROMPT,
        messages = fitted(selection.model, history),
    )

    /**
     * The conversation trimmed to the model's window, so a long chat keeps working instead of being
     * rejected for being too large.
     */
    private fun fitted(model: AiModel, history: List<ChatTurn>): List<ChatTurn> {
        val budgeted = ContextBudget.fit(
            turns = history,
            contextWindow = model.contextWindowTokens?.minus(estimateTokens(SYSTEM_PROMPT)),
        )
        if (budgeted.isTruncated) {
            logger.d { "Trimmed ${budgeted.droppedTurns} older turns to fit the context window" }
        }
        return budgeted.turns
    }

    private companion object {
        const val LOCAL_CONVERSATION = "local"

        /**
         * Plain, provider-agnostic instructions. Deliberately short: the app is a chat client, so it
         * does not pretend to give the model a persona beyond answering the question it was asked.
         */
        const val SYSTEM_PROMPT = "You are a helpful assistant. Answer clearly and concisely. " +
            "Use Markdown for code and lists."
    }
}
