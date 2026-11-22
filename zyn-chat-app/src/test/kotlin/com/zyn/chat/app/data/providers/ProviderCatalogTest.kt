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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the catalog the person picks from: what the search field matches, and the invariants that
 * make an entry safe to add — no duplicate ids, no key sent over plain http, and an honest note on
 * anything this app cannot talk to.
 */
class ProviderCatalogTest {

    @Test
    fun `ids are unique, so a key can never be stored under the wrong provider`() {
        val ids = ProviderCatalog.all.map(KnownProvider::id)

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `an empty query offers every provider rather than nothing`() {
        assertEquals(ProviderCatalog.all, ProviderCatalog.search(query = ""))
        assertEquals(ProviderCatalog.all, ProviderCatalog.search(query = "   "))
    }

    @Test
    fun `search ignores case`() {
        assertEquals(listOf("openai"), ProviderCatalog.search(query = "OPENAI").map(KnownProvider::id))
    }

    @Test
    fun `search matches the display name as well as the id`() {
        assertEquals(
            listOf("perplexity"),
            ProviderCatalog.search(query = "Perplexity").map(KnownProvider::id),
        )
    }

    @Test
    fun `search matches an id whose display name is spelled differently`() {
        assertEquals(listOf("zai"), ProviderCatalog.search(query = "zai").map(KnownProvider::id))
    }

    @Test
    fun `search matches part of a name`() {
        assertEquals(listOf("github"), ProviderCatalog.search(query = "hub").map(KnownProvider::id))
    }

    @Test
    fun `surrounding spaces in the query are ignored`() {
        assertEquals(listOf("groq"), ProviderCatalog.search(query = "  groq ").map(KnownProvider::id))
    }

    @Test
    fun `results keep catalog order instead of being ranked`() {
        val matches = ProviderCatalog.search(query = "ai")

        assertTrue(matches.size > 1)
        assertEquals(ProviderCatalog.all.filter { provider -> provider in matches }, matches)
    }

    @Test
    fun `a query that matches nothing returns nothing rather than everything`() {
        assertEquals(emptyList<KnownProvider>(), ProviderCatalog.search(query = "no-such-provider"))
    }

    @Test
    fun `an entry is looked up by id, and an unknown id is null`() {
        assertEquals("OpenAI", ProviderCatalog.byId("openai")?.displayName)
        assertNull(ProviderCatalog.byId("no-such-provider"))
    }

    @Test
    fun `the custom endpoint is offered last and its url is editable and unknown up front`() {
        val custom = requireNotNull(ProviderCatalog.byId(ProviderCatalog.CUSTOM_ID))

        assertEquals(ProviderCatalog.CUSTOM_ID, ProviderCatalog.all.last().id)
        assertTrue(custom.editableBaseUrl)
        assertTrue(custom.baseUrl.isEmpty())
    }

    @Test
    fun `a hosted provider is always reached over https`() {
        val cloud = ProviderCatalog.all.filter { it.kind == ProviderKind.Cloud }

        assertTrue(cloud.isNotEmpty())
        assertTrue(cloud.all { it.baseUrl.startsWith("https://") })
    }

    @Test
    fun `a local server needs no key and stays on localhost`() {
        val local = ProviderCatalog.all.filter { it.kind == ProviderKind.Local }

        assertTrue(local.isNotEmpty())
        assertTrue(local.all { it.auth == ProviderAuth.None && !it.keyRequired })
        assertTrue(local.all { it.baseUrl.startsWith("http://localhost") })
    }

    @Test
    fun `whether a key is needed always agrees with how that key is sent`() {
        assertTrue(ProviderCatalog.all.all { it.keyRequired == (it.auth != ProviderAuth.None) })
    }

    @Test
    fun `every hosted provider says where to get a key`() {
        val cloud = ProviderCatalog.all.filter { it.kind == ProviderKind.Cloud && it.supported && it.keyRequired }

        assertTrue(cloud.isNotEmpty())
        assertTrue(cloud.all { !it.docsUrl.isNullOrBlank() })
    }

    @Test
    fun `a provider this app cannot talk to stays listed and says why`() {
        val anthropic = requireNotNull(ProviderCatalog.byId("anthropic"))

        assertFalse(anthropic.supported)
        assertFalse(anthropic.unsupportedReason.isNullOrBlank())
        assertEquals(ProviderAuth.ApiKeyHeader, anthropic.auth)
    }

    @Test
    fun `the catalog is not one-sided: it offers both supported and unsupported providers`() {
        assertTrue(ProviderCatalog.all.any { it.supported })
        assertTrue(ProviderCatalog.all.any { !it.supported })
    }
}
