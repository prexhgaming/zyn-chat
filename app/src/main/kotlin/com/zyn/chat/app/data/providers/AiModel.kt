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
 * A single model offered by an [AiProvider].
 *
 * The model carries everything the picker needs to render it and everything a request will need
 * later, so no UI copy is derived from the model's display name.
 *
 * @param id Stable identifier, unique across all providers.
 * @param displayName Name shown to the person.
 * @param slug Provider-side model identifier used in requests.
 * @param providerId Id of the [AiProvider] this model belongs to.
 * @param contextWindowTokens Size of the context window in tokens, or `null` when the provider does
 *   not report one.
 * @param isFree Whether the model can be used without the person's own API key.
 * @param available Whether the model can currently be used.
 * @param capabilities What the model can do. Every field is `null` when it is merely unknown, so the
 *   UI can warn instead of asserting something the app has not established.
 */
public data class AiModel(
    public val id: String,
    public val displayName: String,
    public val slug: String,
    public val providerId: String,
    public val contextWindowTokens: Int? = null,
    public val isFree: Boolean = false,
    public val available: Boolean = true,
    public val capabilities: ModelCapabilities = ModelCapabilities.UNKNOWN,
) {
    /** Context window formatted for display, e.g. `128K` or `1.0M`, or `null` when unknown. */
    public val contextWindowLabel: String?
        get() = contextWindowTokens?.let(::formatContextWindow)

    /** Whether this model is known to accept images. */
    public val acceptsImages: Boolean
        get() = capabilities.acceptsImages
}

/**
 * Selection id of a model, unique across providers: the provider id and the provider-side slug.
 *
 * Model ids are persisted (the selection, each conversation's model), so they must stay unique even
 * when two providers offer the same slug.
 *
 * @param providerId Id of the provider that offers the model.
 * @param slug Provider-side model identifier.
 */
public fun modelKey(providerId: String, slug: String): String = "$providerId$MODEL_KEY_SEPARATOR$slug"

/**
 * Provider-side slug of a selection id produced by [modelKey].
 *
 * @param key A model selection id.
 */
public fun modelSlug(key: String): String = key.substringAfter(MODEL_KEY_SEPARATOR)

/**
 * Formats a token count the way model pickers usually show it: `8K`, `128K`, `1.0M`.
 *
 * @param tokens Token count to format.
 */
public fun formatContextWindow(tokens: Int): String = when {
    tokens >= MILLION -> "${(tokens * 10 / MILLION) / 10.0}M"
    tokens >= THOUSAND -> "${tokens / THOUSAND}K"
    else -> tokens.toString()
}

private const val MODEL_KEY_SEPARATOR = ":"
private const val THOUSAND = 1_000
private const val MILLION = 1_000_000
