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
 * Recognises who makes a model, so the UI can show the right mark.
 *
 * This is **recognition, not a model list**: nothing here says a model exists or is available, only
 * who makes it, so a model that appears tomorrow is still identified correctly. A name that matches
 * nothing is left unrecognised ([ModelBrands.of] returns `null`) and the UI falls back to initials,
 * which is a better answer than a confident wrong logo.
 */
public object ModelBrands {

    /**
     * Brand of a model, or `null` when nothing identifies it.
     *
     * The model's own name is tried first and the provider's id second, so a provider's own models are
     * still branded when they carry no recognisable name — but a model that names its family always
     * wins, which is why Google's own `gemini` under a third-party provider is still Google's.
     *
     * @param providerId Id of the provider offering the model.
     * @param slug Provider-side model identifier.
     */
    public fun of(providerId: String, slug: String): BrandLogo? =
        brandIn(slug.modelSegment()) ?: brandIn(providerId.lowercase())

    /** First brand whose word appears in [text], most identifying word first. */
    private fun brandIn(text: String): BrandLogo? =
        if (text.isBlank()) {
            null
        } else {
            WORDS.firstOrNull { (word, _) -> text.hasWord(word) }?.second
        }

    /**
     * Every brand's identifying words with the brand, longest first.
     *
     * Longest first is what keeps compound names straight: `llama-3.1-nemotron` contains both Meta's
     * word and NVIDIA's, and the longer, more specific one has to win.
     */
    private val WORDS: List<Pair<String, BrandLogo>> = BrandCatalog.all
        .flatMap { brand -> (brand.tokens + brand.id).map { word -> word.lowercase() to brand } }
        .sortedByDescending { (word, _) -> word.length }
}

/**
 * The model part of a slug, lowercased.
 *
 * Providers namespace their models incompatibly, and this collapses them to one shape:
 * `OpenAI/GPT-4o` becomes `gpt-4o`, `meta-llama/Llama-3.1-70b` becomes `llama-3.1-70b` and Vertex's
 * `publishers/google/models/gemini-2.0-flash` becomes `gemini-2.0-flash`.
 */
internal fun String.modelSegment(): String = lowercase().substringAfterLast('/')

/**
 * Whether [word] appears in this string as a word rather than as part of a longer one.
 *
 * Boundaries are what stop a short word from claiming an unrelated name: `phi` matches `phi-4` but
 * not `dolphin`, and `pi` matches `pi-3.1` but neither `pixtral` nor `pika`. A digit counts as a
 * boundary on the right so that run-together versions like `llama3` and `gpt4o` still match.
 *
 * @param word Lowercase word to look for.
 */
internal fun String.hasWord(word: String): Boolean {
    var from = 0
    while (from <= length - word.length) {
        val start = indexOf(word, from)
        if (start < 0) return false

        val end = start + word.length
        val startsClean = start == 0 || !this[start - 1].isLetter()
        val endsClean = end == length || !this[end].isLetter()

        if (startsClean && endsClean) return true
        from = start + 1
    }
    return false
}
