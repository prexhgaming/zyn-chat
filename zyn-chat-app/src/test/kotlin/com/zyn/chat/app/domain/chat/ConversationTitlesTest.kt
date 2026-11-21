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
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTitlesTest {

    @Test
    fun `uses the first line that has content`() {
        val title = ConversationTitles.local("\n\n   \nHow do I sort a map?\nAnd something else")

        // The second line is not part of the title, and the trailing question mark is dropped.
        assertEquals("How do I sort a map", title)
    }

    @Test
    fun `strips Markdown markers so a heading reads as a sentence`() {
        val title = ConversationTitles.local("### **Bold** _question_ about `code`")

        assertEquals("Bold question about code", title)
    }

    @Test
    fun `collapses whitespace`() {
        val title = ConversationTitles.local("Why   is\tthis so     spaced?")

        assertEquals("Why is this so spaced", title)
    }

    @Test
    fun `drops trailing punctuation`() {
        val title = ConversationTitles.local("What is Kotlin?")

        assertEquals("What is Kotlin", title)
    }

    @Test
    fun `caps a long question and marks it as shortened`() {
        val title = ConversationTitles.local("a".repeat(120))

        assertTrue(title.length <= ConversationTitles.MAX_LOCAL_TITLE)
        assertTrue(title.endsWith("\u2026"))
    }

    @Test
    fun `empty input produces an empty title`() {
        assertEquals("", ConversationTitles.local("   \n\t "))
    }

    @Test
    fun `a model answer is cleaned the same way`() {
        val title = ConversationTitles.fromModel("\"Sorting a map in Kotlin\"\nI hope this helps!")

        assertEquals("Sorting a map in Kotlin", title)
    }

    @Test
    fun `a model that over-answers is still capped`() {
        val title = ConversationTitles.fromModel("w".repeat(200))

        assertTrue(title.length <= ConversationTitles.MAX_LOCAL_TITLE)
    }

    @Test
    fun `the prompt contains the instruction and the question`() {
        val prompt = ConversationTitles.prompt("How do I sort a map?")

        assertTrue(prompt.contains(ConversationTitles.INSTRUCTION))
        assertTrue(prompt.contains("How do I sort a map?"))
    }
}
