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
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins brand recognition.
 *
 * The rules are heuristics over names other people choose, so they are worth testing: a wrong answer
 * puts someone else's logo on a model, which is worse than showing initials. These cases are the ones
 * that were actually wrong once — compound names, short words buried inside longer ones, and providers
 * whose models are named after nobody.
 */
class ModelBrandTest {

    private fun brandIdOf(slug: String, providerId: String = "openai"): String? =
        ModelBrands.of(providerId = providerId, slug = slug)?.id

    @Test
    fun `recognises the family from the model's own name`() {
        assertEquals("openai", brandIdOf("gpt-4o-mini"))
        assertEquals("openai", brandIdOf("o3-mini"))
        assertEquals("openai", brandIdOf("chatgpt-4o-latest"))
        assertEquals("anthropic", brandIdOf("claude-sonnet-4"))
        assertEquals("google", brandIdOf("gemini-2.0-flash"))
        assertEquals("google", brandIdOf("gemma-2-9b-it"))
        assertEquals("meta", brandIdOf("llama-3.3-70b-instruct"))
        assertEquals("mistral", brandIdOf("mistral-large-latest"))
        assertEquals("mistral", brandIdOf("pixtral-12b"))
        assertEquals("deepseek", brandIdOf("deepseek-r1"))
        assertEquals("qwen", brandIdOf("qwen2.5-72b-instruct"))
        assertEquals("qwen", brandIdOf("qwq-32b"))
        assertEquals("xai", brandIdOf("grok-2-1212"))
        assertEquals("cohere", brandIdOf("command-r-plus"))
        assertEquals("perplexity", brandIdOf("sonar-pro"))
        assertEquals("nvidia", brandIdOf("nemotron-4-340b"))
        assertEquals("ai2", brandIdOf("olmo-2-13b"))
        assertEquals("microsoft", brandIdOf("phi-4"))
    }

    @Test
    fun `recognises the wider set of families, not just the first few`() {
        assertEquals("upstage", brandIdOf("solar-pro"))
        assertEquals("ibm", brandIdOf("granite-3.1-8b-instruct"))
        assertEquals("rwkv", brandIdOf("rwkv-5-world"))
        assertEquals("dbrx", brandIdOf("dbrx-instruct"))
        assertEquals("tii", brandIdOf("falcon-180b"))
        assertEquals("internlm", brandIdOf("internlm2-20b"))
        assertEquals("zeroone", brandIdOf("yi-34b-chat"))
        assertEquals("minimax", brandIdOf("abab6.5s-chat"))
        assertEquals("bytedance", brandIdOf("doubao-pro-32k"))
        assertEquals("baidu", brandIdOf("ernie-4.0-8k"))
        assertEquals("tencent", brandIdOf("hunyuan-lite"))
        assertEquals("zhipu", brandIdOf("glm-4-plus"))
        assertEquals("moonshot", brandIdOf("kimi-k2-instruct"))
        assertEquals("baichuan", brandIdOf("baichuan2-13b"))
        assertEquals("ai21", brandIdOf("jamba-1.5-large"))
        assertEquals("stepfun", brandIdOf("step-2-16k"))
        assertEquals("reka", brandIdOf("reka-core"))
        assertEquals("llava", brandIdOf("llava-1.6-34b"))
    }

    @Test
    fun `strips every provider's namespacing the same way`() {
        assertEquals("openai", brandIdOf("openai/gpt-4o"))
        assertEquals("anthropic", brandIdOf("anthropic/claude-3.5-sonnet"))
        assertEquals("google", brandIdOf("google/gemini-2.5-pro"))
        assertEquals("meta", brandIdOf("meta-llama/llama-3.1-70b-instruct"))
        assertEquals("google", brandIdOf("publishers/google/models/gemini-2.5-flash"))
        assertEquals("nvidia", brandIdOf("nvidia/llama-3.1-nemotron-70b-instruct"))
    }

    @Test
    fun `a more specific name wins over a family the model also mentions`() {
        // NVIDIA's model starts with `llama`; the second one names its fine-tuner and its base alike.
        assertEquals("nvidia", brandIdOf("llama-3.1-nemotron-70b"))
        assertEquals("dolphin", brandIdOf("dolphin-2.9-llama3-8b"))
    }

    @Test
    fun `short words only count as whole words`() {
        // `dolphin` contains `phi`, `pixtral` contains `pi` and `dolphin` contains `pan`-less noise;
        // none of them may claim those families.
        assertEquals("dolphin", brandIdOf("dolphin-2.9.1-yi-34b"))
        assertEquals("microsoft", brandIdOf("phi-3.5-mini"))
        assertEquals("mistral", brandIdOf("pixtral-large"))
        assertEquals("zeroone", brandIdOf("yi-34b"))
        // Run-together versions still match, which is why a digit ends a word as well.
        assertEquals("meta", brandIdOf("llama3-8b"))
        assertEquals("openai", brandIdOf("gpt4o"))
    }

    @Test
    fun `the provider identifies the family when the model name does not`() {
        assertEquals("deepseek", brandIdOf("some-internal-codename", providerId = "deepseek"))
        assertEquals("groq", brandIdOf("some-internal-codename", providerId = "groq"))
        assertEquals("openrouter", brandIdOf("openrouter/auto", providerId = "openrouter"))
        assertEquals("cerebras", brandIdOf("some-internal-codename", providerId = "cerebras"))
        assertEquals("microsoft", brandIdOf("some-internal-codename", providerId = "azure"))
        assertEquals("google", brandIdOf("some-internal-codename", providerId = "vertex"))
        assertEquals("amazon", brandIdOf("some-internal-codename", providerId = "bedrock"))
        assertEquals("ollama", brandIdOf("some-internal-codename", providerId = "ollama"))
        assertEquals("huggingface", brandIdOf("some-internal-codename", providerId = "huggingface"))
        assertEquals("together", brandIdOf("some-internal-codename", providerId = "together"))
    }

    @Test
    fun `a model that names its family is not branded as its host`() {
        assertEquals("meta", brandIdOf("llama-3.3-70b-versatile", providerId = "groq"))
        assertEquals("mistral", brandIdOf("mixtral-8x7b-32768", providerId = "groq"))
        assertEquals("google", brandIdOf("gemini-2.0-flash", providerId = "openrouter"))
    }

    @Test
    fun `nothing recognisable stays unrecognised rather than guessing`() {
        // The provider has to be unrecognisable too: when the name says nothing, the provider id is
        // the deliberate fallback — an unnamed model on OpenAI is still OpenAI's (see the test above).
        assertNull(brandIdOf("mystery-model-9000", providerId = "some-local-gateway"))
        assertNull(brandIdOf("", providerId = "some-local-gateway"))
        assertNull(brandIdOf("   ", providerId = "some-local-gateway"))
    }

    @Test
    fun `recognition ignores case`() {
        assertEquals("openai", brandIdOf("OpenAI/GPT-4o"))
        assertEquals("anthropic", brandIdOf("Anthropic/Claude-3-Opus"))
        assertEquals("google", brandIdOf("Google/Gemini-2.5-Pro"))
    }

    @Test
    fun `every brand can be turned into a drawable name`() {
        BrandCatalog.all.forEach { brand ->
            assertEquals("ids are lowercase", brand.id, brand.id.lowercase())
            assertEquals(
                "ids are safe as file names",
                true,
                brand.id.all { char -> char.isLetterOrDigit() || char == '_' },
            )
            assertEquals("display names are set", false, brand.displayName.isBlank())
        }
    }
}
