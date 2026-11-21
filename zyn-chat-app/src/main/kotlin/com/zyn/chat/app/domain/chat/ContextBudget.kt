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

package com.zyn.chat.app.domain.chat

/**
 * A conversation trimmed to what actually fits a model's context window.
 *
 * @param turns The turns to send, oldest first. Always ends with the newest question.
 * @param droppedTurns How many older turns were left out.
 * @param estimatedTokens Estimated size of [turns].
 * @param limit The model's context window, or `null` when it is unknown.
 */
public data class BudgetedHistory(
    public val turns: List<ChatTurn>,
    public val droppedTurns: Int,
    public val estimatedTokens: Int,
    public val limit: Int?,
) {
    /** Whether older turns had to be left out of the request. */
    public val isTruncated: Boolean get() = droppedTurns > 0

    /** Share of the window the request is expected to use, between 0 and 1, or `null` if unknown. */
    public val usageFraction: Double?
        get() = limit?.takeIf { window -> window > 0 }
            ?.let { window -> (estimatedTokens.toDouble() / window).coerceIn(0.0, 1.0) }

    /** Whether the conversation is close enough to the window to warn about it. */
    public val isNearLimit: Boolean get() = (usageFraction ?: 0.0) >= NEAR_LIMIT

    private companion object {
        const val NEAR_LIMIT = 0.8
    }
}

/**
 * Decides how much of a conversation can be sent.
 *
 * Models reject a request that exceeds their context window, which turns a long chat into an error the
 * person cannot do anything about. Trimming here — before the call — keeps the newest part of the
 * conversation, which is the part a question is most likely to depend on, and leaves room for the
 * answer itself.
 *
 * The estimate is deliberately crude: exact tokenisation differs per model and is not available
 * locally, so this is a guard rail, not a measurement. A model with an unknown window is never
 * trimmed — guessing a limit would silently drop history.
 */
public object ContextBudget {

    /** Output tokens reserved when a request does not ask for a specific amount. */
    public const val DEFAULT_OUTPUT_TOKENS: Int = 1_024

    /** Smallest reserve worth keeping, so the model always has room to answer. */
    public const val MIN_OUTPUT_TOKENS: Int = 256

    /** Rough characters per token for ordinary prose; English averages a little under four. */
    private const val CHARACTERS_PER_TOKEN = 4

    /** Per-message overhead for role markers and delimiters. */
    private const val MESSAGE_OVERHEAD_TOKENS = 4

    /** Estimated tokens of one piece of text. */
    public fun estimateTokens(text: String): Int =
        if (text.isEmpty()) 0 else (text.length + CHARACTERS_PER_TOKEN - 1) / CHARACTERS_PER_TOKEN

    /** Estimated tokens of one turn. */
    public fun estimateTokens(turn: ChatTurn): Int =
        estimateTokens(turn.content) + MESSAGE_OVERHEAD_TOKENS

    /** Estimated tokens of a conversation. */
    public fun estimateTokens(turns: List<ChatTurn>): Int =
        turns.sumOf(::estimateTokens)

    /**
     * Trims [turns] to what fits a context window, keeping the most recent ones.
     *
     * The last turn is always kept even when it alone exceeds the budget: dropping the question would
     * leave nothing to answer, and the provider's own error is more honest than a silent empty request.
     *
     * @param turns Conversation so far, oldest first, ending with the new question.
     * @param contextWindow Model's window in tokens, or `null` when unknown.
     * @param maxOutputTokens Tokens to keep free for the answer, or `null` for [DEFAULT_OUTPUT_TOKENS].
     */
    public fun fit(
        turns: List<ChatTurn>,
        contextWindow: Int?,
        maxOutputTokens: Int? = null,
    ): BudgetedHistory {
        val estimatedTokens = estimateTokens(turns)
        val window = contextWindow?.takeIf { limit -> limit > 0 }
            ?: return BudgetedHistory(turns, droppedTurns = 0, estimatedTokens, limit = null)

        val available = (window - reserve(window, maxOutputTokens)).coerceAtLeast(1)
        val kept = newestThatFit(turns, available)
        return BudgetedHistory(
            turns = kept,
            droppedTurns = turns.size - kept.size,
            estimatedTokens = estimateTokens(kept),
            limit = window,
        )
    }

    /** Tokens held back for the answer: whatever is asked for, within what the window can spare. */
    private fun reserve(window: Int, maxOutputTokens: Int?): Int {
        val asked = maxOutputTokens ?: DEFAULT_OUTPUT_TOKENS
        val ceiling = (window / HALF_OF_WINDOW).coerceAtLeast(MIN_OUTPUT_TOKENS)
        return asked.coerceIn(MIN_OUTPUT_TOKENS, ceiling)
    }

    private const val HALF_OF_WINDOW = 2

    /** The longest tail of [turns] that fits [availableTokens], and never an empty list. */
    private fun newestThatFit(turns: List<ChatTurn>, availableTokens: Int): List<ChatTurn> {
        if (turns.isEmpty()) return turns

        var used = 0
        var start = turns.size
        for (index in turns.indices.reversed()) {
            val cost = estimateTokens(turns[index])
            if (used + cost > availableTokens && start < turns.size) break
            used += cost
            start = index
        }
        return turns.subList(start, turns.size)
    }
}
