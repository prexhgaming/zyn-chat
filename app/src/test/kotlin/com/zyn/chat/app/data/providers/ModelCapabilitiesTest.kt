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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the capability rules, especially the difference between "no" and "not known", and the
 * precedence that stops a vision family from swallowing its own text-only variants.
 */
class ModelCapabilitiesTest {

    @Test
    fun `what the provider says beats local knowledge`() {
        val model = ModelCatalog.enrich(
            providerId = "openrouter",
            reported = ReportedModel(
                slug = "gpt-4o",
                capabilities = ModelCapabilities.textOnly(),
            ),
        )

        assertEquals(false, model.capabilities.imageSupport)
        assertFalse(model.acceptsImages)
    }

    @Test
    fun `a name that looks like vision buys nothing without a provider report`() {
        val model = ModelCatalog.enrich("ollama", "qwen2-vl-7b-instruct")

        assertNull(model.capabilities.imageSupport)
        assertFalse(model.acceptsImages)
    }

    @Test
    fun `an unknown model stays unknown instead of being guessed`() {
        val model = ModelCatalog.enrich("local", "some-internal-model-v3")

        assertNull(model.capabilities.imageSupport)
        assertFalse(model.acceptsImages)
        assertFalse(model.capabilities.isKnown)
    }

    @Test
    fun `a name that looks text-only blocks nothing on its own`() {
        val textOnlyLooking = ModelCatalog.enrich("openai", "o1-mini")

        // Whether pictures work is the provider's answer to give, so an unanswered question stays
        // unanswered: the composer warns and the request is still tried.
        assertNull(textOnlyLooking.capabilities.imageSupport)
        assertEquals(
            ImageAttachmentDecision.Warn,
            textOnlyLooking.capabilities.imageAttachmentDecision(),
        )
    }

    @Test
    fun `a vendor namespace and a free suffix do not hide the model`() {
        val model = ModelCatalog.enrich("openrouter", "meta-llama/llama-4-scout:free")

        assertTrue(model.isFree)
        assertNull(model.capabilities.imageSupport)
    }

    @Test
    fun `a dated snapshot still matches its family`() {
        val model = ModelCatalog.enrich("openai", "gpt-4o-2024-08-06")

        // The family hint still fills in what a name can legitimately carry, but it no longer
        // decides whether pictures work: that answer belongs to the provider.
        assertNull(model.capabilities.imageSupport)
    }

    @Test
    fun `the provider's context window wins over the local table`() {
        val model = ModelCatalog.enrich(
            providerId = "openrouter",
            reported = ReportedModel(
                slug = "gpt-4o",
                capabilities = ModelCapabilities(contextWindowTokens = 999),
            ),
        )

        assertEquals(999, model.contextWindowTokens)
    }

    @Test
    fun `a context window is filled in locally when the provider is silent`() {
        val model = ModelCatalog.enrich("openai", "gpt-4o")

        assertEquals(128_000, model.contextWindowTokens)
    }

    @Test
    fun `tool support is only ever taken from the provider`() {
        val reported = ModelCatalog.enrich(
            providerId = "openrouter",
            reported = ReportedModel(
                slug = "gpt-4o",
                capabilities = ModelCapabilities(supportsTools = true),
            ),
        )
        val silent = ModelCatalog.enrich("openai", "gpt-4o")

        assertEquals(true, reported.capabilities.supportsTools)
        assertNull(silent.capabilities.supportsTools)
    }

    @Test
    fun `unknown fields are filled from the fallback and known ones are kept`() {
        val merged = ModelCapabilities(supportsTools = true)
            .orElse(ModelCapabilities.textOnly(contextWindowTokens = 4_000))

        assertEquals(true, merged.supportsTools)
        assertEquals(false, merged.imageSupport)
        assertEquals(4_000, merged.contextWindowTokens)
    }

    @Test
    fun `provider modality spellings are read, and unknown ones are ignored`() {
        assertEquals(Modality.Image, Modality.of("image"))
        assertEquals(Modality.Image, Modality.of("vision"))
        assertEquals(Modality.Text, Modality.of(" TEXT "))
        assertNull(Modality.of("smell"))
    }

    @Test
    fun `a prompt price of zero marks a model free`() {
        val priced = ModelCatalog.enrich(
            providerId = "openrouter",
            reported = ReportedModel(slug = "gpt-4o", isFree = true),
        )
        val paid = ModelCatalog.enrich("openai", "gpt-4o")

        assertTrue(priced.isFree)
        assertFalse(paid.isFree)
    }

    @Test
    fun `a model known to take pictures attaches them without a word`() {
        assertEquals(
            ImageAttachmentDecision.Allowed,
            ModelCapabilities.textAndImages().imageAttachmentDecision(),
        )
    }

    @Test
    fun `a text-only model is blocked from pictures rather than merely warned`() {
        assertEquals(
            ImageAttachmentDecision.Blocked,
            ModelCapabilities.textOnly().imageAttachmentDecision(),
        )
    }

    @Test
    fun `an unknown model is allowed with a warning, never blocked on a guess`() {
        assertEquals(
            ImageAttachmentDecision.Warn,
            ModelCapabilities.UNKNOWN.imageAttachmentDecision(),
        )
    }
}
