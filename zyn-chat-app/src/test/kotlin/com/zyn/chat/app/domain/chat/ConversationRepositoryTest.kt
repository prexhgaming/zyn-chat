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

import com.zyn.chat.app.data.chat.ConversationStore
import com.zyn.chat.app.data.chat.TitleSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * Covers the guarantee the chat depends on: a conversation is answered from its own history and
 * nothing else. This is what would break if the storage key were ever shared between chats.
 */
class ConversationRepositoryTest {

    private lateinit var directory: java.io.File
    private lateinit var repository: ConversationRepository

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("zyn-conversations").toFile()
        repository = ConversationRepository(ConversationStore(directory))
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun record(id: String, question: String) = runBlocking {
        repository.record(
            conversationId = id,
            question = question,
            answer = "answer to $question",
            providerId = "openai",
            modelId = "gpt-4o-mini",
        )
    }

    @Test
    fun `history holds the question and the answer`() = runBlocking {
        record("chat-a", "What is Kotlin?")

        val history = repository.history("chat-a")

        assertEquals(2, history.size)
        assertEquals(ChatRole.User, history.first().role)
        assertEquals("What is Kotlin?", history.first().content)
        assertEquals(ChatRole.Assistant, history.last().role)
    }

    @Test
    fun `two conversations never see each other's history`() = runBlocking {
        record("chat-a", "Question about maps")
        record("chat-b", "Question about lists")

        val first = repository.history("chat-a")
        val second = repository.history("chat-b")

        assertNotEquals(first, second)
        assertTrue(first.none { turn -> turn.content.contains("lists") })
        assertTrue(second.none { turn -> turn.content.contains("maps") })
    }

    @Test
    fun `an unknown conversation has no history`() = runBlocking {
        record("chat-a", "Hello")

        assertTrue(repository.history("chat-b").isEmpty())
        assertTrue(repository.history(newConversationId()).isEmpty())
    }

    @Test
    fun `a second exchange extends the same conversation`() = runBlocking {
        record("chat-a", "First question")
        record("chat-a", "Second question")

        val history = repository.history("chat-a")

        assertEquals(4, history.size)
        assertEquals("Second question", history[2].content)
    }

    @Test
    fun `stored conversations survive being read back from disk`() = runBlocking {
        record("chat-a", "What is Kotlin?")

        // A fresh store reads the same folder, which is what happens after a restart.
        val reopened = ConversationRepository(ConversationStore(directory))

        assertEquals(2, reopened.history("chat-a").size)
    }

    @Test
    fun `the first question names the conversation`() = runBlocking {
        record("chat-a", "How do I sort a map in Kotlin?")

        val meta = repository.metadata().single { it.id == "chat-a" }

        assertEquals("How do I sort a map in Kotlin", meta.title)
    }

    @Test
    fun `a renamed conversation is never renamed back automatically`() = runBlocking {
        record("chat-a", "First question")
        assertTrue(repository.setTitle("chat-a", "My own name", TitleSource.Manual))

        val fromModel = repository.setTitle("chat-a", "A model suggestion", TitleSource.Model)

        assertFalse(fromModel)
        assertEquals("My own name", repository.metadata().single().title)
    }

    @Test
    fun `a model title replaces the derived one`() = runBlocking {
        record("chat-a", "First question")

        assertTrue(repository.setTitle("chat-a", "Short name", TitleSource.Model))

        assertEquals("Short name", repository.metadata().single().title)
    }

    @Test
    fun `renaming an unknown conversation changes nothing`() = runBlocking {
        assertFalse(repository.setTitle("chat-a", "Name", TitleSource.Manual))
    }

    @Test
    fun `deleting a conversation forgets it and leaves the others alone`() = runBlocking {
        record("chat-a", "Keep me")
        record("chat-b", "Delete me")

        repository.delete("chat-b")

        assertTrue(repository.history("chat-b").isEmpty())
        assertEquals(2, repository.history("chat-a").size)
        assertEquals(listOf("chat-a"), repository.metadata().map { it.id })
    }

    @Test
    fun `conversations are listed most recently used first`() = runBlocking {
        record("chat-a", "Older")
        // Ordering comes from a millisecond timestamp, so make the two writes distinguishable.
        Thread.sleep(5)
        record("chat-b", "Newer")

        assertEquals(listOf("chat-b", "chat-a"), repository.metadata().map { it.id })
    }

    @Test
    fun `every new conversation gets its own id`() {
        val ids = (1..50).map { newConversationId() }

        assertEquals(50, ids.toSet().size)
    }

    @Test
    fun `a stored conversation reports its title and model back`() = runBlocking {
        record("chat-a", "How do I sort a map in Kotlin?")

        val stored = requireNotNull(repository.conversation("chat-a"))

        assertEquals("How do I sort a map in Kotlin", stored.title)
        assertTrue(stored.modelId?.endsWith("gpt-4o-mini") == true)
    }

    @Test
    fun `an unknown conversation reports nothing back`() = runBlocking {
        assertEquals(null, repository.conversation(newConversationId()))
    }

    @Test
    fun `a channel id is recognised and a storage id is not`() {
        // The distinction the resume crash came down to: Stream only accepts the first kind.
        assertTrue(isChannelId("messaging:abc-123"))
        assertTrue(isChannelId("livestream:room"))

        assertFalse(isChannelId(newConversationId()))
        assertFalse(isChannelId("abc-123"))
        assertFalse(isChannelId(null))
    }
}
