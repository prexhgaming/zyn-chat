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
 * matches nothing is [Generic], which the UI renders as initials.
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
    Microsoft("Microsoft"),
    Generic(""),
}

/**
 * Maps a model slug to its family.
 *
 * Matching is by slug prefix because providers namespace their models with it (`gpt-4o`,
 * `claude-sonnet-4`, `accounts/...` excluded). The provider id is used as a fallback: a provider that
 * serves its own models under unhelpful slugs still gets the right mark.
 */
public object ModelBrands {

    /**
     * Family of a model.
     *
     * @param providerId Id of the provider that offers it, used as a fallback.
     * @param slug Provider-side model identifier.
     */
    public fun of(providerId: String, slug: String): ModelBrand {
        val lower = slug.lowercase()
        return BRAND_PREFIXES.firstOrNull { (prefix, _) -> lower.startsWith(prefix) }?.second
            ?: PROVIDER_BRANDS[providerId]
            ?: ModelBrand.Generic
    }
}

/** Slug prefixes, checked in order, mapped to the family they belong to. */
private val BRAND_PREFIXES: List<Pair<String, ModelBrand>> = listOf(
    "gpt" to ModelBrand.OpenAI,
    "chatgpt" to ModelBrand.OpenAI,
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
    "phi" to ModelBrand.Microsoft,
)

/** Fallback for providers whose models carry no recognisable prefix. */
private val PROVIDER_BRANDS: Map<String, ModelBrand> = mapOf(
    "openai" to ModelBrand.OpenAI,
    "anthropic" to ModelBrand.Anthropic,
    "gemini" to ModelBrand.Google,
    "mistral" to ModelBrand.Mistral,
    "deepseek" to ModelBrand.DeepSeek,
    "xai" to ModelBrand.XAI,
    "moonshot" to ModelBrand.Qwen,
    "zai" to ModelBrand.Qwen,
)
