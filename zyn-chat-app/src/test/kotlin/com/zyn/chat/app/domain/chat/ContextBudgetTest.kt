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

package com.zyn.chat.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgetTest {

    private fun turns(count: Int, content: String = "x".repeat(40)): List<ChatTurn> =
        (1..count).map { index -> ChatTurn(ChatRole.User, "$content-$index") }

    @Test
    fun `estimates roughly four characters per token`() {
        assertEquals(0, ContextBudget.estimateTokens(""))
        assertEquals(1, ContextBudget.estimateTokens("abcd"))
        assertEquals(2, ContextBudget.estimateTokens("abcde"))
    }

    @Test
    fun `an unknown window never trims the conversation`() {
        val conversation = turns(count = 500)

        val budgeted = ContextBudget.fit(conversation, contextWindow = null)

        assertEquals(conversation, budgeted.turns)
        assertEquals(0, budgeted.droppedTurns)
        assertFalse(budgeted.isTruncated)
        assertNull(budgeted.limit)
        assertNull(budgeted.usageFraction)
    }

    @Test
    fun `a conversation that fits is sent whole`() {
        val conversation = turns(count = 3)

        val budgeted = ContextBudget.fit(conversation, contextWindow = 10_000)

        assertEquals(conversation, budgeted.turns)
        assertEquals(0, budgeted.droppedTurns)
        assertFalse(budgeted.isTruncated)
    }

    @Test
    fun `an oversized conversation keeps its newest turns`() {
        val conversation = turns(count = 100)

        val budgeted = ContextBudget.fit(conversation, contextWindow = 600, maxOutputTokens = 256)

        assertTrue(budgeted.isTruncated)
        assertEquals(conversation.last(), budgeted.turns.last())
        assertTrue("kept more than the window allows", budgeted.estimatedTokens <= 600 - 256)
        assertTrue(budgeted.droppedTurns > 0)
    }

    @Test
    fun `the newest question is kept even when it alone exceeds the window`() {
        val huge = ChatTurn(ChatRole.User, "y".repeat(4_000))
        val conversation = listOf(ChatTurn(ChatRole.User, "hello"), huge)

        val budgeted = ContextBudget.fit(conversation, contextWindow = 100)

        assertEquals(listOf(huge), budgeted.turns)
    }

    @Test
    fun `reports how full the window is`() {
        val conversation = turns(count = 1)

        val budgeted = ContextBudget.fit(conversation, contextWindow = 1_000)

        val fraction = requireNotNull(budgeted.usageFraction)
        assertTrue(fraction in 0.0..1.0)
        assertFalse(budgeted.isNearLimit)

        // A large window with the smallest reserve is what pushes usage close to the limit.
        val tight = ContextBudget.fit(turns(count = 1_000), contextWindow = 5_000, maxOutputTokens = 256)
        assertTrue(requireNotNull(tight.usageFraction) >= 0.8)
        assertTrue(tight.isNearLimit)
    }

    @Test
    fun `an empty conversation stays empty`() {
        val budgeted = ContextBudget.fit(emptyList(), contextWindow = 100)

        assertTrue(budgeted.turns.isEmpty())
        assertEquals(0, budgeted.droppedTurns)
    }
}
