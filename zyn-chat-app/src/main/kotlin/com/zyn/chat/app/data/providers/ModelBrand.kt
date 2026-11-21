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
 * The model family a slug belongs to.
 *
 * This is **recognition, not a model list**: nothing here says a model exists or is available, it only
 * says who makes it, so the UI can show the right mark for whatever a provider reports. A slug that
 * matches nothing is [Generic], which the UI falls back to initials for.
 *
 * @param displayName Name of the family, used as a label and as the initials fallback's source.
 */
public enum class ModelBrand(public val displayName: String) {
    OpenAI("OpenAI"),
    Anthropic("Anthropic"),
    Google("Google"),
    Meta("Meta"),
    Mistral("Mistral AI"),
    DeepSeek("DeepSeek"),
    Qwen("Qwen"),
    XAI("xAI"),
    Cohere("Cohere"),
    Perplexity("Perplexity"),
    Nvidia("NVIDIA"),
    Amazon("Amazon"),
    Ai2("AI2"),
    Microsoft("Microsoft"),
    Generic(""),
}

/**
 * Maps a model slug to its family.
 *
 * Providers namespace their models in incompatible ways, so the model segment is taken first: the
 * part after the last `/` is the model, and everything before it is vendor or path noise. That one
 * rule covers OpenAI-compatible slugs (`gpt-4o`), OpenRouter's `vendor/model` form
 * (`openai/gpt-4o`) and Vertex's `publishers/google/models/gemini-2.0-flash` alike.
 *
 * Matching is most-specific first: a distinguishing word anywhere in the name, then a leading
 * prefix, then the provider's own family. The word check has to come first, otherwise a compound name
 * is claimed by whichever family its prefix happens to match — `llama-3.1-nemotron` starts with
 * `llama` but is NVIDIA's model, not Meta's.
 */
public object ModelBrands {

    /**
     * Family of a model.
     *
     * @param providerId Id of the provider that offers it, used as a last fallback.
     * @param slug Provider-side model identifier.
     */
    public fun of(providerId: String, slug: String): ModelBrand {
        val name = slug.modelSegment()
        return BRAND_SUBSTRINGS.firstOrNull { (needle, _) -> needle in name }?.second
            ?: BRAND_PREFIXES.firstOrNull { (prefix, _) -> name.startsWith(prefix) }?.second
            ?: PROVIDER_BRANDS[providerId.lowercase()]
            ?: ModelBrand.Generic
    }
}

/**
 * The model part of a slug, lowercased.
 *
 * `Publishers/Google/Models/Gemini-2.0-Flash` becomes `gemini-2.0-flash`, and `openai/gpt-4o` becomes
 * `gpt-4o`, so every provider's namespace is stripped the same way.
 */
internal fun String.modelSegment(): String = lowercase().substringAfterLast('/')

/** Slug prefixes, checked in order, mapped to the family they belong to. */
private val BRAND_PREFIXES: List<Pair<String, ModelBrand>> = listOf(
    "chatgpt" to ModelBrand.OpenAI,
    "gpt" to ModelBrand.OpenAI,
    "o1" to ModelBrand.OpenAI,
    "o3" to ModelBrand.OpenAI,
    "o4" to ModelBrand.OpenAI,
    "claude" to ModelBrand.Anthropic,
    "gemini" to ModelBrand.Google,
    "gemma" to ModelBrand.Google,
    "palm" to ModelBrand.Google,
    "llama" to ModelBrand.Meta,
    "meta-llama" to ModelBrand.Meta,
    "mistral" to ModelBrand.Mistral,
    "mixtral" to ModelBrand.Mistral,
    "codestral" to ModelBrand.Mistral,
    "pixtral" to ModelBrand.Mistral,
    "magistral" to ModelBrand.Mistral,
    "deepseek" to ModelBrand.DeepSeek,
    "qwen" to ModelBrand.Qwen,
    "qwq" to ModelBrand.Qwen,
    "grok" to ModelBrand.XAI,
    "command" to ModelBrand.Cohere,
    "sonar" to ModelBrand.Perplexity,
    "nemotron" to ModelBrand.Nvidia,
    "amazon" to ModelBrand.Amazon,
    "nova" to ModelBrand.Amazon,
    "titan" to ModelBrand.Amazon,
    "olmo" to ModelBrand.Ai2,
    "phi" to ModelBrand.Microsoft,
)

/**
 * Words that identify a family when they appear anywhere in the model name, checked in order.
 *
 * Order matters: `nemotron` is listed before `llama` so NVIDIA's Llama-based model is not claimed by
 * Meta, which is the case this list exists for.
 */
private val BRAND_SUBSTRINGS: List<Pair<String, ModelBrand>> = listOf(
    "nemotron" to ModelBrand.Nvidia,
    "llama" to ModelBrand.Meta,
    "gemma" to ModelBrand.Google,
    "mixtral" to ModelBrand.Mistral,
    "qwen" to ModelBrand.Qwen,
    "claude" to ModelBrand.Anthropic,
    "command" to ModelBrand.Cohere,
)

/** Fallback for providers whose models carry no recognisable name of their own. */
private val PROVIDER_BRANDS: Map<String, ModelBrand> = mapOf(
    "openai" to ModelBrand.OpenAI,
    "anthropic" to ModelBrand.Anthropic,
    "gemini" to ModelBrand.Google,
    "mistral" to ModelBrand.Mistral,
    "deepseek" to ModelBrand.DeepSeek,
    "xai" to ModelBrand.XAI,
    "cohere" to ModelBrand.Cohere,
    "perplexity" to ModelBrand.Perplexity,
    "nvidia" to ModelBrand.Nvidia,
    "amazon" to ModelBrand.Amazon,
    "ai2" to ModelBrand.Ai2,
    "moonshot" to ModelBrand.Qwen,
    "zai" to ModelBrand.Qwen,
)
