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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins how an API key is attached to a request.
 *
 * Every style here is a header: a key in the URL would travel through `HttpLoggingInterceptor`,
 * `okhttp` tags and any proxy log, where header redaction cannot reach it. The last test pins the
 * set of styles, so adding a query-parameter style is a deliberate change rather than a silent one.
 */
class ProviderAuthTest {

    @Test
    fun `each style names the header it uses`() {
        assertEquals("Authorization", ProviderAuth.Bearer.headerName)
        assertEquals("x-api-key", ProviderAuth.ApiKeyHeader.headerName)
    }

    @Test
    fun `a bearer key is prefixed and an api key is sent bare`() {
        assertEquals("Bearer sk-test", ProviderAuth.Bearer.headerValue(key = "sk-test"))
        assertEquals("sk-test", ProviderAuth.ApiKeyHeader.headerValue(key = "sk-test"))
    }

    @Test
    fun `a style without a key sends nothing at all`() {
        assertNull(ProviderAuth.None.headerValue(key = "sk-test"))
        assertTrue(ProviderAuth.None.headerName.isEmpty())
    }

    @Test
    fun `every style that sends a key puts it in a named header`() {
        val sendsAKey = ProviderAuth.entries.filter { it != ProviderAuth.None }

        assertTrue(sendsAKey.isNotEmpty())
        assertTrue(sendsAKey.all { it.headerName.isNotBlank() })
        assertTrue(sendsAKey.all { it.headerValue(key = "sk-test") != null })
    }

    @Test
    fun `only header styles exist, so a key can never be moved into the url`() {
        assertEquals(
            listOf("Authorization", "x-api-key", ""),
            ProviderAuth.entries.map(ProviderAuth::headerName),
        )
    }
}
