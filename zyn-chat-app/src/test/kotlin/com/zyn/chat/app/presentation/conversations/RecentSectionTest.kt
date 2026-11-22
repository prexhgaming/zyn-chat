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

package com.zyn.chat.app.presentation.conversations

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the drawer's time buckets, which are calendar-day based rather than "24 hours ago".
 *
 * Timestamps are built with calendar arithmetic on purpose: a fixed millisecond offset would shift
 * across a daylight-saving change and make these assertions fail twice a year.
 */
class RecentSectionTest {

    @Test
    fun `a conversation used today is bucketed as today`() {
        assertEquals(RecentSection.Today, recentSectionOf(middayDaysAgo(days = 0), now()))
    }

    @Test
    fun `just after midnight this morning is still today`() {
        assertEquals(RecentSection.Today, recentSectionOf(midnightToday(), now()))
    }

    @Test
    fun `the last moment before midnight is yesterday, not today`() {
        assertEquals(RecentSection.Yesterday, recentSectionOf(midnightToday() - 1, now()))
    }

    @Test
    fun `midday yesterday is yesterday`() {
        assertEquals(RecentSection.Yesterday, recentSectionOf(middayDaysAgo(days = 1), now()))
    }

    @Test
    fun `three days ago is inside the last seven days`() {
        assertEquals(RecentSection.PreviousSevenDays, recentSectionOf(middayDaysAgo(days = 3), now()))
    }

    @Test
    fun `six days ago is still inside the last seven days`() {
        assertEquals(RecentSection.PreviousSevenDays, recentSectionOf(middayDaysAgo(days = 6), now()))
    }

    @Test
    fun `eight days ago has fallen out of the last seven days`() {
        assertEquals(RecentSection.Older, recentSectionOf(middayDaysAgo(days = 8), now()))
    }

    @Test
    fun `an unknown time is older rather than dropped`() {
        assertEquals(RecentSection.Older, recentSectionOf(updatedAt = null, now = now()))
    }

    @Test
    fun `a missing or nonsensical timestamp is older`() {
        assertEquals(RecentSection.Older, recentSectionOf(updatedAt = 0L, now = now()))
        assertEquals(RecentSection.Older, recentSectionOf(updatedAt = -1L, now = now()))
    }

    @Test
    fun `buckets come in declaration order and empty ones are left out`() {
        val conversations = listOf(
            conversation(id = "old", updatedAt = middayDaysAgo(days = 9)),
            conversation(id = "today", updatedAt = middayDaysAgo(days = 0)),
            conversation(id = "week", updatedAt = middayDaysAgo(days = 3)),
        )

        val sections = conversations.groupedByRecentSection(now())

        assertEquals(
            listOf(RecentSection.Today, RecentSection.PreviousSevenDays, RecentSection.Older),
            sections.map { (section, _) -> section },
        )
    }

    @Test
    fun `conversations keep their order inside a bucket`() {
        val conversations = listOf(
            conversation(id = "newest", updatedAt = middayDaysAgo(days = 0)),
            conversation(id = "older", updatedAt = middayDaysAgo(days = 0)),
        )

        val sections = conversations.groupedByRecentSection(now())

        assertEquals(listOf("newest", "older"), sections.single().second.map(Conversation::id))
    }

    @Test
    fun `a conversation with no known time still reaches the older bucket`() {
        val sections = listOf(conversation(id = "unknown", updatedAt = null)).groupedByRecentSection(now())

        assertEquals(RecentSection.Older, sections.single().first)
        assertEquals(listOf("unknown"), sections.single().second.map(Conversation::id))
    }

    @Test
    fun `no conversations produce no buckets`() {
        assertEquals(
            emptyList<Pair<RecentSection, List<Conversation>>>(),
            emptyList<Conversation>().groupedByRecentSection(now()),
        )
    }
}

/** The current time, fixed at midday so a test never straddles a day boundary while it runs. */
private fun now(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 12)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Midday, [days] calendar days before today. */
private fun middayDaysAgo(days: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_MONTH, -days)
    set(Calendar.HOUR_OF_DAY, 12)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** The first moment of today. */
private fun midnightToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** A conversation that only carries what the bucketing rules read. */
private fun conversation(id: String, updatedAt: Long?): Conversation = Conversation(
    id = id,
    title = id,
    updatedAt = updatedAt,
)
