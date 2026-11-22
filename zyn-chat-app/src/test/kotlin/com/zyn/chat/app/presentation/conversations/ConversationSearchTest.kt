package com.zyn.chat.app.presentation.conversations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSearchTest {

    @Test
    fun `a blank query keeps every conversation, in order`() {
        val all = listOf(chat("1", "Coffee in Rome"), chat("2", "Kotlin coroutines"))

        assertEquals(all, all.matching(""))
        assertEquals(all, all.matching("   "))
        assertEquals(all, all.matching("\n\t "))
    }

    @Test
    fun `matching is case-insensitive on both sides`() {
        val all = listOf(chat("1", "Kotlin Coroutines"))

        assertEquals(1, all.matching("kotlin").size)
        assertEquals(1, all.matching("KOTLIN").size)
        assertEquals(1, all.matching("CoRoUtInEs").size)
    }

    @Test
    fun `a term matches anywhere in the title, not only at the start`() {
        val all = listOf(chat("1", "Best coffee shops in Rome"))

        assertEquals(1, all.matching("best").size)
        assertEquals(1, all.matching("shops").size)
        assertEquals(1, all.matching("rome").size)
    }

    @Test
    fun `every term has to appear, whatever order they were typed in`() {
        val all = listOf(chat("1", "Best coffee shops in Rome"))

        assertEquals(1, all.matching("rome coffee").size)
        assertEquals(1, all.matching("coffee rome").size)
        assertEquals(1, all.matching("rome best shops").size)
        assertTrue(all.matching("rome tea").isEmpty())
    }

    @Test
    fun `extra whitespace between terms is ignored`() {
        val all = listOf(chat("1", "Best coffee shops in Rome"))

        assertEquals(1, all.matching("  coffee   rome  ").size)
        assertEquals(1, all.matching("coffee\trome").size)
    }

    @Test
    fun `only the title is searched, because the title is all a row shows`() {
        val all = listOf(chat("chat-1", "Coffee in Rome", modelSlug = "openai/gpt-4o"))

        assertTrue(all.matching("chat-1").isEmpty())
        assertTrue(all.matching("gpt-4o").isEmpty())
    }

    @Test
    fun `a query that matches nothing returns nothing`() {
        val all = listOf(chat("1", "Coffee in Rome"), chat("2", "Kotlin coroutines"))

        assertTrue(all.matching("sourdough").isEmpty())
    }

    @Test
    fun `the newest-first order of the input is kept`() {
        val all = listOf(
            chat("3", "Coffee in Rome"),
            chat("2", "Coffee in Turin"),
            chat("1", "Coffee in Bari"),
        )

        assertEquals(listOf("3", "2", "1"), all.matching("coffee").map { it.id })
    }

    @Test
    fun `terms are not split, so a term only matches where the title really has it`() {
        val all = listOf(chat("1", "Kotlin coroutines"))

        assertEquals(1, all.matching("coro").size)
        assertTrue(all.matching("coroutines kotlinx").isEmpty())
    }

    @Test
    fun `only the conversations that match come back`() {
        val all = listOf(
            chat("1", "Coffee in Rome"),
            chat("2", "Kotlin coroutines"),
            chat("3", "Coffee grinders"),
        )

        assertEquals(listOf("1", "3"), all.matching("coffee").map { it.id })
    }
}

private fun chat(id: String, title: String, modelSlug: String? = null): Conversation = Conversation(
    id = id,
    title = title,
    modelSlug = modelSlug,
)
