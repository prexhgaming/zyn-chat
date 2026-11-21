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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins how a slug is turned into a family.
 *
 * Recognition is the part people notice when it is wrong: a mark that points at the wrong company is
 * worse than no mark at all. These cases cover the namespaces providers actually use, so a change to
 * the tables cannot quietly re-attribute a model.
 */
class ModelBrandTest {

    private fun brandOf(slug: String, providerId: String = "openai"): ModelBrand =
        ModelBrands.of(providerId = providerId, slug = slug)

    @Test
    fun `recognises plain OpenAI-compatible slugs`() {
        assertEquals(ModelBrand.OpenAI, brandOf("gpt-4o-mini"))
        assertEquals(ModelBrand.OpenAI, brandOf("o3-mini"))
        assertEquals(ModelBrand.Anthropic, brandOf("claude-sonnet-4"))
        assertEquals(ModelBrand.Google, brandOf("gemini-2.0-flash"))
        assertEquals(ModelBrand.DeepSeek, brandOf("deepseek-r1"))
    }

    @Test
    fun `strips OpenRouter's vendor namespace`() {
        assertEquals(ModelBrand.OpenAI, brandOf("openai/gpt-4o"))
        assertEquals(ModelBrand.Anthropic, brandOf("anthropic/claude-3.5-sonnet"))
        assertEquals(ModelBrand.Google, brandOf("google/gemini-2.5-pro"))
        assertEquals(ModelBrand.Meta, brandOf("meta-llama/llama-3.1-70b-instruct"))
    }

    @Test
    fun `strips vertex path namespaces`() {
        assertEquals(
            ModelBrand.Google,
            brandOf("publishers/google/models/gemini-2.0-flash", providerId = "gemini"),
        )
        assertEquals(ModelBrand.Google, brandOf("models/gemini-1.5-pro"))
    }

    @Test
    fun `a distinguishing word beats a coincidental one`() {
        // NVIDIA's model is named after Llama but is not Meta's.
        assertEquals(ModelBrand.Nvidia, brandOf("llama-3.1-nemotron-70b"))
        assertEquals(ModelBrand.Nvidia, brandOf("nemotron-4-340b"))
        assertEquals(ModelBrand.Meta, brandOf("llama-3.3-70b-versatile", providerId = "groq"))
    }

    @Test
    fun `recognises the newer families`() {
        assertEquals(ModelBrand.Cohere, brandOf("command-r-plus", providerId = "cohere"))
        assertEquals(ModelBrand.Perplexity, brandOf("sonar-pro", providerId = "perplexity"))
        assertEquals(ModelBrand.Amazon, brandOf("amazon.nova-pro-v1", providerId = "amazon"))
        assertEquals(ModelBrand.Amazon, brandOf("titan-text-premier", providerId = "amazon"))
        assertEquals(ModelBrand.Ai2, brandOf("olmo-2-13b", providerId = "ai2"))
        assertEquals(ModelBrand.XAI, brandOf("grok-2-1212", providerId = "xai"))
    }

    @Test
    fun `falls back to the provider when the slug says nothing`() {
        assertEquals(ModelBrand.DeepSeek, brandOf("some-internal-codename", providerId = "deepseek"))
        assertEquals(ModelBrand.Cohere, brandOf("some-internal-codename", providerId = "cohere"))
    }

    @Test
    fun `an unidentifiable model is generic rather than mis-attributed`() {
        assertEquals(ModelBrand.Generic, brandOf("mystery-model-9000", providerId = "openrouter"))
        assertEquals(ModelBrand.Generic, brandOf("openrouter/auto", providerId = "openrouter"))
    }

    @Test
    fun `the model segment is taken regardless of case`() {
        assertEquals(ModelBrand.OpenAI, brandOf("OpenAI/GPT-4o"))
        assertEquals(ModelBrand.Anthropic, brandOf("Anthropic/Claude-3-Opus"))
    }
}
