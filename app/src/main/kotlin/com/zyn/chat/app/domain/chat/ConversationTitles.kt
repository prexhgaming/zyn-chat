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

/**
 * Conversation titles.
 *
 * A title is never the first message repeated verbatim: [local] condenses the question on the device
 * so the list can update the moment someone sends, and [INSTRUCTION] is the prompt used to ask the
 * model for a shorter one afterwards. The local result stays if that request fails, so a title is
 * always available without spending a call.
 */
public object ConversationTitles {

    /** Longest title the app keeps when deriving one on the device. */
    public const val MAX_LOCAL_TITLE: Int = 48

    /**
     * The instruction used for the one-shot call that names a conversation.
     *
     * The question is appended to it, and the answer is taken as the title.
     */
    public const val INSTRUCTION: String =
        "Write a title for this conversation in 3 to 6 words. " +
            "Answer with the title only: no quotes, no punctuation at the end, no explanation."

    /**
     * A readable title taken from [text] without asking a model.
     *
     * Uses the first line that has content, strips Markdown markers, collapses whitespace and caps
     * the length, so a long paragraph still produces a list-sized title.
     *
     * @param text Usually the first thing the person asked in the conversation.
     */
    public fun local(text: String): String {
        val line = text.lineSequence()
            .map { line -> line.trim() }
            .firstOrNull { line -> line.isNotEmpty() }
            .orEmpty()

        val cleaned = line
            .replace(MARKDOWN, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .trim(*TRIM_CHARACTERS)

        return when {
            cleaned.isEmpty() -> ""
            cleaned.length <= MAX_LOCAL_TITLE -> cleaned
            else -> cleaned.take(MAX_LOCAL_TITLE - 1).trimEnd() + ELLIPSIS
        }
    }

    /** The prompt that asks a model to name a conversation started by [question]. */
    public fun prompt(question: String): String = "$INSTRUCTION\n\n$question"

    /**
     * Cleans a title a model returned, so an explanation or stray quotes never reach the list.
     *
     * A model that ignores the instruction and answers in code — the usual failure for this prompt,
     * where the model writes a snippet instead of a name — is rejected: the caller then keeps the
     * device-made title instead of showing code as the conversation's name.
     */
    public fun fromModel(answer: String): String {
        val title = local(answer)
        // A model that ignored the instruction may have answered in several lines; the first is the title.
        val candidate = title.take(MAX_LOCAL_TITLE)
        return candidate.takeIf { it.isNotBlank() && !CODE_INDICATORS.any { marker -> it.contains(marker) } }
            ?: ""
    }

    private const val ELLIPSIS = "\u2026"

    /** Markers that mean a model answered in code rather than with a name. */
    private val CODE_INDICATORS = listOf("package ", "import ", "class ", "fun ", "val ", "var ", "=", "{", "}", "(", ")")

    private val MARKDOWN = Regex("[#*_`>~]+")
    private val WHITESPACE = Regex("\\s+")

    private val TRIM_CHARACTERS = charArrayOf(
        '.', ',', ':', ';', '-', '!', '?', '"', '\'', '(', ')', '[', ']', '\u201C', '\u201D',
    )
}
