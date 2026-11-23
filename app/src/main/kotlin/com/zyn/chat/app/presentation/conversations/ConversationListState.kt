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

/**
 * UI state for the conversation list.
 */
public data class ConversationListState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Data class representing a chat conversation.
 *
 * @param id Conversation id: a Stream channel id for channel-backed chats, or the stored
 * conversation id for chats answered by the person's own provider.
 * @param title Display title. A title the person set by hand always wins over a generated one.
 * @param updatedAt When the conversation was last used, used to group the drawer. `null` when unknown.
 * @param modelSlug Model the conversation was last answered with, used for its brand mark.
 * @param channelId The Stream channel id, or `null` when the chat has no channel at all. This is the
 * only value that may ever be handed to Stream: a stored conversation id is not a channel id, and
 * passing one to the client throws.
 */
public data class Conversation(
    val id: String,
    val title: String,
    val updatedAt: Long? = null,
    val modelSlug: String? = null,
    val channelId: String? = null,
)

/** Time buckets the drawer groups recent conversations into, newest first. */
public enum class RecentSection {
    /** Started today. */
    Today,

    /** Started yesterday. */
    Yesterday,

    /** Started in the last seven days. */
    PreviousSevenDays,

    /** Older than a week, or with no known time. */
    Older,
}

/**
 * Places a conversation in a drawer bucket based on when it was last used.
 *
 * Buckets are calendar-day based, so "yesterday" means the previous calendar day rather than
 * twenty-four hours ago.
 *
 * @param updatedAt When the conversation was last used, or `null` when unknown.
 * @param now The current time, injected so this stays testable.
 */
public fun recentSectionOf(updatedAt: Long?, now: Long): RecentSection {
    if (updatedAt == null || updatedAt <= 0L) return RecentSection.Older

    val startOfToday = startOfDay(now)
    return when {
        updatedAt >= startOfToday -> RecentSection.Today
        updatedAt >= startOfToday - DAY_MILLIS -> RecentSection.Yesterday
        updatedAt >= startOfToday - WEEK_MILLIS -> RecentSection.PreviousSevenDays
        else -> RecentSection.Older
    }
}

/** Groups conversations into the drawer's buckets, preserving their order within each bucket. */
public fun List<Conversation>.groupedByRecentSection(now: Long): List<Pair<RecentSection, List<Conversation>>> =
    RecentSection.entries
        .map { section -> section to filter { recentSectionOf(it.updatedAt, now) == section } }
        .filter { (_, conversations) -> conversations.isNotEmpty() }

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
private const val WEEK_MILLIS = 7L * DAY_MILLIS
