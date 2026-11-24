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

package com.zyn.chat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.presentation.conversations.Conversation
import com.zyn.chat.app.presentation.conversations.RecentSection
import com.zyn.chat.app.presentation.conversations.groupedByRecentSection
import com.zyn.chat.app.presentation.conversations.matching
import com.zyn.chat.app.ui.icons.ZynIcons
import com.zyn.chat.app.ui.theme.RowShape
import io.getstream.chat.android.models.User

/**
 * Slide-out navigation drawer.
 *
 * Layout follows one vertical rhythm: brand header, navigation entries, the recent conversations
 * (which take the remaining space), and a footer that stays anchored to the bottom.
 *
 * @param user The signed-in user, shown in the footer.
 * @param conversations Recent conversations, newest first.
 * @param selectedConversationId The conversation currently open, if any.
 * @param onNewChatClick Invoked when "New chat" is tapped.
 * @param onConversationClick Invoked with a tapped conversation, so both its storage id and its
 * channel id (when it has one) are available.
 * @param onRenameConversation Invoked with a conversation id and its new title.
 * @param onDeleteConversation Invoked with the id of a conversation to delete.
 * @param onUserInfoClick Invoked when the profile row is tapped.
 * @param onProvidersClick Invoked when "Providers" is tapped.
 * @param onRoutingClick Invoked when "Routing" is tapped.
 * @param modifier Applied to the drawer.
 *
 * Search is the drawer's own state rather than a callback: it filters nothing outside the drawer, and
 * while it is open the recents area becomes a flat result list.
 */
@Composable
fun ChatDrawer(
    user: User?,
    conversations: List<Conversation>,
    selectedConversationId: String?,
    onNewChatClick: () -> Unit,
    onConversationClick: (conversation: Conversation) -> Unit,
    onRenameConversation: (conversationId: String, title: String) -> Unit,
    onDeleteConversation: (conversationId: String) -> Unit,
    onUserInfoClick: () -> Unit,
    onProvidersClick: () -> Unit,
    onRoutingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    // Search lives here rather than in the app's navigation state: nothing outside the drawer reads it,
    // and leaving search clears it, so a filter can never be left on unseen.
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val closeSearch: () -> Unit = {
        searchActive = false
        searchQuery = ""
        keyboardController?.hide()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding(),
    ) {
        DrawerHeader(
            searchActive = searchActive,
            onSearchOpen = { searchActive = true },
            onSearchClose = closeSearch,
        )

        if (searchActive) {
            DrawerSearchField(
                value = searchQuery,
                onValueChange = { query -> searchQuery = query },
            )
        }

        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            DrawerNavItem(
                icon = ZynIcons.Providers,
                label = stringResource(R.string.zyn_drawer_providers),
                onClick = onProvidersClick,
            )
            DrawerNavItem(
                icon = ZynIcons.Routing,
                label = stringResource(R.string.zyn_drawer_routing),
                onClick = onRoutingClick,
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        val results = remember(conversations, searchQuery) { conversations.matching(searchQuery) }

        DrawerSectionLabel(
            text = stringResource(
                if (searchActive) R.string.zyn_drawer_results else R.string.zyn_drawer_recents,
            ),
            modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 6.dp),
        )

        when {
            conversations.isEmpty() -> {
                DrawerHint(text = stringResource(R.string.zyn_drawer_empty))
                Spacer(modifier = Modifier.weight(1f))
            }

            // Searching found nothing: say what was searched for and offer to clear it, so the state
            // explains itself instead of looking like an empty drawer.
            searchActive && results.isEmpty() -> {
                DrawerHint(
                    text = stringResource(R.string.zyn_drawer_no_results, searchQuery.trim()),
                )
                DrawerHintAction(
                    text = stringResource(R.string.zyn_drawer_search_clear),
                    onClick = { searchQuery = "" },
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            // One flat list while searching: the Today/Yesterday buckets answer "when was this used",
            // which is not the question the search field asks.
            searchActive -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    items(
                        key = Conversation::id,
                        items = results,
                    ) { conversation ->
                        DrawerConversationRow(
                            conversation = conversation,
                            selected = conversation.id == selectedConversationId,
                            onClick = { onConversationClick(conversation) },
                            onRename = { renameTarget = conversation },
                            onDelete = { deleteTarget = conversation },
                        )
                    }
                }
            }

            else -> {
                val sections = remember(conversations) {
                    conversations.groupedByRecentSection(System.currentTimeMillis())
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    sections.forEach { (section, sectionConversations) ->
                        item(key = "section-$section") {
                            DrawerSectionLabel(
                                text = section.label(),
                                modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 2.dp),
                            )
                        }

                        items(
                            key = Conversation::id,
                            items = sectionConversations,
                        ) { conversation ->
                            DrawerConversationRow(
                                conversation = conversation,
                                selected = conversation.id == selectedConversationId,
                                onClick = { onConversationClick(conversation) },
                                onRename = { renameTarget = conversation },
                                onDelete = { deleteTarget = conversation },
                            )
                        }
                    }
                }
            }
        }

        DrawerFooter(
            user = user,
            onNewChatClick = onNewChatClick,
            onUserInfoClick = onUserInfoClick,
        )
    }

    renameTarget?.let { target ->
        RenameConversationDialog(
            initialTitle = target.title,
            onConfirm = { title ->
                onRenameConversation(target.id, title)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        DeleteConversationDialog(
            title = target.title,
            onConfirm = {
                onDeleteConversation(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun DrawerHeader(
    searchActive: Boolean,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 6.dp, top = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark()

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = if (searchActive) onSearchClose else onSearchOpen) {
            Icon(
                imageVector = if (searchActive) ZynIcons.Close else ZynIcons.Search,
                contentDescription = stringResource(
                    if (searchActive) R.string.zyn_drawer_search_close else R.string.zyn_drawer_search,
                ),
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The drawer's search field, shown only while searching.
 *
 * Opening search is a deliberate act, so the field takes focus and the keyboard comes up on its own
 * rather than making the person tap the field first. There is no clear button inside the field: the
 * header's close action is the single way out, so the drawer does not show two different "x" buttons.
 */
@Composable
private fun DrawerSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = {
            Text(
                text = stringResource(R.string.zyn_drawer_search_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = ZynIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .focusRequester(focusRequester),
    )
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Z",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun DrawerSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A muted line of guidance, used for the drawer's empty states. */
@Composable
private fun DrawerHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

/** The way out of an empty state, tinted with the accent so it reads as the action. */
@Composable
private fun DrawerHintAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = 14.dp)
            .clip(RowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

/**
 * One conversation in the recents list, wherever that list is: the sectioned recents and the flat
 * search results render the same row, so the two can never drift apart.
 */
@Composable
private fun DrawerConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    DrawerConversationItem(
        title = conversation.title,
        selected = selected,
        onClick = onClick,
        onRename = onRename,
        onDelete = onDelete,
    )
}

/** Drawer heading for a group of recent conversations. */
@Composable
private fun RecentSection.label(): String = stringResource(
    when (this) {
        RecentSection.Today -> R.string.zyn_drawer_today
        RecentSection.Yesterday -> R.string.zyn_drawer_yesterday
        RecentSection.PreviousSevenDays -> R.string.zyn_drawer_previous_7_days
        RecentSection.Older -> R.string.zyn_drawer_older
    },
)

@Composable
private fun DrawerFooter(
    user: User?,
    onNewChatClick: () -> Unit,
    onUserInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        NewChatButton(onClick = onNewChatClick)

        if (user != null) {
            UserSummary(
                user = user,
                onClick = onUserInfoClick,
            )
        }
    }
}

@Composable
private fun NewChatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_new),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )

        Text(
            text = stringResource(R.string.zyn_action_new_chat),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun UserSummary(
    user: User,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = user.initials,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private val User.initials: String
    get() = name.trim()
        .split("\\s+".toRegex())
        .mapNotNull(String::firstOrNull)
        .take(2)
        .joinToString("")
        .uppercase()
