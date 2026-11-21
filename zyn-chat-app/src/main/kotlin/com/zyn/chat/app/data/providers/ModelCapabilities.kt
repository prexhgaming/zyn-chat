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

/** A kind of input a model can accept. */
public enum class Modality {
    /** Plain text, which every chat model takes. */
    Text,

    /** Images, which only some models take. */
    Image,

    /** Audio input. */
    Audio,

    /** Video input. */
    Video,

    ;

    public companion object {
        /**
         * Reads a provider's spelling of a modality.
         *
         * Providers are inconsistent here: OpenRouter reports `input_modalities` as words, and its
         * `modality` summary writes combinations like `text+image->text`. Anything unrecognised
         * becomes `null` rather than being forced into a bucket.
         */
        public fun of(value: String): Modality? = when (value.trim().lowercase()) {
            "text" -> Text
            "image", "images", "vision" -> Image
            "audio" -> Audio
            "video" -> Video
            else -> null
        }
    }
}

/**
 * What a model can do, and whether that is even known.
 *
 * Every field is deliberately tri-state: `null` means **unknown**, never "no". That distinction is
 * the whole point — a model whose abilities are not established must not be treated as if it cannot
 * take images, and must not be treated as if it can either. The UI can then warn instead of guessing,
 * and a request is only ever built with images when image support is positively known.
 *
 * @param inputModalities What the model accepts, or `null` when not established.
 * @param supportsTools Whether it can call tools, or `null` when not established. Tool support is
 *   reported by providers that describe it (`supported_parameters`); nothing here infers it from a
 *   model's name.
 * @param contextWindowTokens Size of the context window, or `null` when not established.
 */
public data class ModelCapabilities(
    public val inputModalities: Set<Modality>? = null,
    public val supportsTools: Boolean? = null,
    public val contextWindowTokens: Int? = null,
) {

    /**
     * Whether images may be sent, or `null` when that is not established.
     *
     * `null` and `false` are different answers and are never collapsed: `false` blocks an attachment
     * with an explanation, `null` warns.
     */
    public val imageSupport: Boolean?
        get() = inputModalities?.contains(Modality.Image)

    /** Whether images are known to be accepted. */
    public val acceptsImages: Boolean
        get() = imageSupport == true

    /** Whether this record says anything at all about the model. */
    public val isKnown: Boolean
        get() = this != UNKNOWN

    public companion object {
        /** Nothing is established about the model. */
        public val UNKNOWN: ModelCapabilities = ModelCapabilities()

        /** A model known to take text and images. */
        public fun textAndImages(contextWindowTokens: Int? = null): ModelCapabilities = ModelCapabilities(
            inputModalities = setOf(Modality.Text, Modality.Image),
            contextWindowTokens = contextWindowTokens,
        )

        /** A model known to take text only. */
        public fun textOnly(contextWindowTokens: Int? = null): ModelCapabilities = ModelCapabilities(
            inputModalities = setOf(Modality.Text),
            contextWindowTokens = contextWindowTokens,
        )
    }
}

/**
 * Fills each field this record does not know from [fallback].
 *
 * Known values always win, which is what keeps a provider's own report ahead of local knowledge no
 * matter which order they are combined in.
 *
 * @param fallback Values to use where this record is silent.
 */
public fun ModelCapabilities.orElse(fallback: ModelCapabilities?): ModelCapabilities {
    if (fallback == null) return this
    return ModelCapabilities(
        inputModalities = inputModalities ?: fallback.inputModalities,
        supportsTools = supportsTools ?: fallback.supportsTools,
        contextWindowTokens = contextWindowTokens ?: fallback.contextWindowTokens,
    )
}
