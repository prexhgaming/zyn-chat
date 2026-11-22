/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-chat-android-ai/blob/main/LICENSE
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the model selection id, which is persisted per conversation, and the context-window label the
 * picker prints. A stored id has to survive a round trip through [modelKey] and [modelSlug], because
 * the slug is what a request is actually sent with.
 */
class AiModelTest {

    @Test
    fun `a selection id carries the provider and the slug`() {
        assertEquals("openai:gpt-4o", modelKey(providerId = "openai", slug = "gpt-4o"))
    }

    @Test
    fun `the slug comes back out of the selection id`() {
        assertEquals("gpt-4o", modelSlug(modelKey(providerId = "openai", slug = "gpt-4o")))
    }

    @Test
    fun `the same slug on two providers gives two different ids`() {
        assertNotEquals(
            modelKey(providerId = "openai", slug = "gpt-4o"),
            modelKey(providerId = "openrouter", slug = "gpt-4o"),
        )
    }

    @Test
    fun `a vendor-namespaced slug survives the round trip`() {
        val slug = "meta-llama/llama-3.3-70b-instruct"

        assertEquals(slug, modelSlug(modelKey(providerId = "openrouter", slug = slug)))
    }

    @Test
    fun `only the first separator is a separator, so the rest of the slug is kept`() {
        assertEquals("b:c", modelSlug("a:b:c"))
    }

    @Test
    fun `an id with no provider prefix is returned unchanged`() {
        assertEquals("gpt-4o", modelSlug("gpt-4o"))
    }

    @Test
    fun `a small context window is printed as plain tokens`() {
        assertEquals("0", formatContextWindow(tokens = 0))
        assertEquals("999", formatContextWindow(tokens = 999))
    }

    @Test
    fun `thousands are shortened`() {
        assertEquals("1K", formatContextWindow(tokens = 1_000))
        assertEquals("8K", formatContextWindow(tokens = 8_000))
        assertEquals("128K", formatContextWindow(tokens = 128_000))
        assertEquals("999K", formatContextWindow(tokens = 999_999))
    }

    @Test
    fun `millions keep one decimal`() {
        assertEquals("1.0M", formatContextWindow(tokens = 1_000_000))
        assertEquals("1.5M", formatContextWindow(tokens = 1_500_000))
        assertEquals("2.0M", formatContextWindow(tokens = 2_000_000))
    }

    @Test
    fun `a power-of-two window is still printed the way a model picker would`() {
        assertEquals("1.0M", formatContextWindow(tokens = 1_048_576))
    }

    @Test
    fun `an unknown context window has no label rather than a fake one`() {
        assertNull(model(contextWindowTokens = null).contextWindowLabel)
    }

    @Test
    fun `a known context window is labelled on the model`() {
        assertEquals("128K", model(contextWindowTokens = 128_000).contextWindowLabel)
    }

    @Test
    fun `a model with no capability record is unknown, not unable to read pictures`() {
        val model = model()

        assertNull(model.capabilities.imageSupport)
        assertFalse(model.capabilities.isKnown)
        assertFalse(model.acceptsImages)
    }

    @Test
    fun `acceptsImages follows the capability record instead of the model's name`() {
        assertTrue(model(capabilities = ModelCapabilities.textAndImages()).acceptsImages)
        assertFalse(model(capabilities = ModelCapabilities.textOnly()).acceptsImages)
    }

    @Test
    fun `a model is available unless a provider says otherwise`() {
        assertTrue(model().available)
    }
}

/** A model that only carries what the rules under test read. */
private fun model(
    contextWindowTokens: Int? = null,
    capabilities: ModelCapabilities = ModelCapabilities.UNKNOWN,
): AiModel = AiModel(
    id = modelKey(providerId = "test", slug = "test-slug"),
    displayName = "Test",
    slug = "test-slug",
    providerId = "test",
    contextWindowTokens = contextWindowTokens,
    capabilities = capabilities,
)
