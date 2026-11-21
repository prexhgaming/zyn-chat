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

package com.zyn.chat.app.data.providers

/**
 * Describes models that providers report, without ever adding one.
 *
 * A provider's `/models` response is the only source of models: this catalog only decorates what came
 * back with a friendlier name, the context window the family is known for, and whether the slug marks
 * itself as free. When something is not known, the value stays `null` and the UI simply shows less —
 * guessing a context window would produce a wrong "out of context" warning later.
 */
public object ModelCatalog {

    /**
     * Builds the app's model record from what a provider reported about it.
     *
     * What the provider said always wins; local knowledge fills the gaps; whatever is left is kept
     * unknown rather than guessed, so nothing downstream has to unlearn a fiction.
     *
     * @param providerId Provider that reported the model.
     * @param reported The facts the provider sent for this model.
     */
    public fun enrich(providerId: String, reported: ReportedModel): AiModel {
        val capabilities = reported.capabilities
            .orElse(ModelCapabilityCatalog.of(reported.slug))
            .orElse(ModelCapabilities(contextWindowTokens = contextWindowOf(reported.slug)))

        return AiModel(
            id = modelKey(providerId, reported.slug),
            displayName = reported.displayName?.takeIf(String::isNotBlank) ?: prettyName(reported.slug),
            slug = reported.slug,
            providerId = providerId,
            contextWindowTokens = capabilities.contextWindowTokens,
            isFree = reported.isFree ?: isFreeModel(reported.slug),
            capabilities = capabilities,
        )
    }

    /**
     * Builds the app's model record for a slug nothing else is known about.
     *
     * @param providerId Provider that reported the slug.
     * @param slug Provider-side model identifier, e.g. `gpt-4o-mini`.
     */
    public fun enrich(providerId: String, slug: String): AiModel = enrich(
        providerId = providerId,
        reported = ReportedModel(slug = slug),
    )

    /**
     * Context window of a known model family.
     *
     * @param slug Provider-side model identifier.
     * @return The window in tokens, or `null` when it is not known.
     */
    public fun contextWindowOf(slug: String): Int? {
        val lower = slug.lowercase()
        return CONTEXT_WINDOWS.firstOrNull { (prefix, _) -> lower.startsWith(prefix) }?.second
    }

    /**
     * Whether a slug announces itself as free, which OpenRouter does with a `:free` suffix.
     *
     * @param slug Provider-side model identifier.
     */
    public fun isFreeModel(slug: String): Boolean = slug.lowercase().endsWith(FREE_SUFFIX)
}

/**
 * What a provider reported about one model.
 *
 * This mirrors the fields the app understands from an OpenAI-compatible `/models` response, with
 * every field optional because most endpoints send nothing beyond an id. It carries raw facts and no
 * display concerns, and it is the input to [ModelCatalog.enrich].
 *
 * @param slug Provider-side model identifier, e.g. `gpt-4o-mini`.
 * @param displayName Name the provider gave, when it gave one.
 * @param capabilities Capabilities the provider stated; anything unstated stays unknown.
 * @param isFree Whether the provider says the model costs nothing, or `null` when it did not say.
 */
public data class ReportedModel(
    public val slug: String,
    public val displayName: String? = null,
    public val capabilities: ModelCapabilities = ModelCapabilities.UNKNOWN,
    public val isFree: Boolean? = null,
)

/**
 * Readable name for a slug, derived from the slug itself: `gpt-4o-mini` reads as `GPT-4o-mini` and
 * `gemini-2.0-flash` as `Gemini 2.0-flash`. Anything unrecognised is shown exactly as the provider
 * reported it.
 */
private fun prettyName(slug: String): String {
    val lower = slug.lowercase()
    val prefix = NAME_PREFIXES.firstOrNull { (prefix, _) -> lower.startsWith(prefix) } ?: return slug
    return prefix.second + lower.removePrefix(prefix.first)
}

/** Known context windows, longest prefix first so specific families win over broader ones. */
private val CONTEXT_WINDOWS: List<Pair<String, Int>> = listOf(
    "gpt-4.1" to 1_047_576,
    "gpt-4o" to 128_000,
    "gpt-4-turbo" to 128_000,
    "gpt-4" to 8_192,
    "gpt-3.5" to 16_385,
    "o1" to 200_000,
    "o3" to 200_000,
    "o4" to 200_000,
    "claude" to 200_000,
    "gemini-1.5-pro" to 2_097_152,
    "gemini" to 1_048_576,
    "gemma" to 8_192,
    "llama-3" to 131_072,
    "mixtral-8x" to 32_768,
    "mistral-large" to 131_072,
    "deepseek-chat" to 65_536,
    "deepseek-r1" to 65_536,
    "grok-4" to 256_000,
    "grok-3" to 131_072,
    "grok-2" to 131_072,
    "phi-4" to 16_384,
)

/** Prefixes replaced by a readable family name, matched in order. */
private val NAME_PREFIXES: List<Pair<String, String>> = listOf(
    "gpt-" to "GPT-",
    "claude-" to "Claude ",
    "gemini-" to "Gemini ",
    "gemma-" to "Gemma ",
    "llama-" to "Llama ",
    "meta-llama-" to "Llama ",
    "mistral-" to "Mistral ",
    "mixtral-" to "Mixtral ",
    "codestral-" to "Codestral ",
    "deepseek-" to "DeepSeek ",
    "qwen" to "Qwen ",
    "grok-" to "Grok ",
    "phi-" to "Phi ",
    "o1" to "o1",
    "o3" to "o3",
    "o4" to "o4",
)

/** Suffix OpenRouter uses for models that cost nothing. */
private const val FREE_SUFFIX = ":free"
