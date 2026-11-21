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
 * Local knowledge about which model families accept images, used **only** when a provider does not
 * say anything itself.
 *
 * A provider's own report always wins (see [ModelCapabilities.orElse]), because it is describing the
 * exact model it is serving. This catalog exists for the many OpenAI-compatible endpoints that only
 * send model ids — Ollama, llama.cpp and friends — where the alternative would be to know nothing.
 *
 * Two rules keep this honest:
 * 1. Every entry is a documented family or an explicit marker, never a guess from a vendor name.
 * 2. Anything not listed stays [ModelCapabilities.UNKNOWN], so the UI warns rather than pretending.
 *
 * Tool support is deliberately **not** inferred here: no name reliably implies it, so it is only ever
 * taken from a provider's `supported_parameters`.
 */
public object ModelCapabilityCatalog {

    /**
     * What is known about a model from its slug alone.
     *
     * @param slug Provider-side model identifier, e.g. `meta-llama/llama-4-scout:free`.
     * @return The known capabilities, or `null` when this slug says nothing.
     */
    public fun of(slug: String): ModelCapabilities? {
        val name = slug.normalizedModelName()
        return when {
            // Explicit exceptions are checked first: `o1-mini` must not inherit vision support just
            // because its name contains `o1`, which does have it.
            TEXT_ONLY_MARKERS.any(name::contains) -> ModelCapabilities.textOnly()
            VISION_MARKERS.any(name::contains) -> ModelCapabilities.textAndImages()
            else -> null
        }
    }
}

/**
 * Reduces a slug to something the marker lists can match: lowercased, without the vendor namespace,
 * without OpenRouter's `:free` suffix, and without a trailing date stamp such as `-2024-08-06`.
 */
private fun String.normalizedModelName(): String = lowercase()
    .substringAfterLast('/')
    .substringBefore(':')
    .replace(Regex("-\\d{4}-\\d{2}-\\d{2}$"), "")
    .replace(Regex("-\\d{6,}$"), "")

/**
 * Markers of families that take images. Each entry is a documented multimodal family or the marker a
 * vision variant puts in its own name — so `qwen2-vl` matches on `-vl`, and `pixtral` on its name.
 */
private val VISION_MARKERS: List<String> = listOf(
    // OpenAI: the -o/-turbo/-1/-5 lines and the o-series are multimodal.
    "gpt-4o",
    "gpt-4.1",
    "gpt-4-turbo",
    "gpt-4-vision",
    "gpt-5",
    "chatgpt-4o",
    "o1",
    "o3",
    "o4-",
    // Anthropic: every Claude 3 and later takes images.
    "claude-3",
    "claude-4",
    // Google: Gemini is multimodal, and Gemma 3 added vision.
    "gemini",
    "gemma-3",
    "palm",
    // Meta: the vision and 4 lines.
    "llama-3.2-vision",
    "llama-4",
    "llama-guard-3-vision",
    // Mistral: Pixtral, and Mistral Small 3 which added vision.
    "pixtral",
    "mistral-small-3",
    // xAI
    "grok-2-vision",
    "grok-4",
    // Everything else that says so in its own name.
    "-vl",
    "vision",
    "llava",
    "internvl",
    "minicpm-v",
    "glm-4v",
    "cogvlm",
    "molmo",
    "idefics",
    "smolvlm",
    "granite-vision",
    "step-1v",
    "yi-vl",
)

/**
 * Markers of families known to be text only. These exist so a model cannot be mistaken for a
 * multimodal one by a loose substring: `o1-mini` is text only even though `o1` takes images.
 */
private val TEXT_ONLY_MARKERS: List<String> = listOf(
    "o1-mini",
    "o1-preview",
    "gpt-3.5",
    "davinci",
    "babbage",
    "text-embedding",
    "text-moderation",
    "whisper",
    "tts-",
    "gpt-4-base",
)
