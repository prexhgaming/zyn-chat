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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.presentation.conversations.Conversation
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
 * @param onConversationClick Invoked with the id of a tapped conversation.
 * @param onUserInfoClick Invoked when the profile row is tapped.
 * @param onProvidersClick Invoked when "Providers" is tapped.
 * @param onRoutingClick Invoked when "Routing" is tapped.
 * @param onSearchClick Invoked when the search action is tapped.
 * @param modifier Applied to the drawer.
 */
@Composable
fun ChatDrawer(
    user: User?,
    conversations: List<Conversation>,
    selectedConversationId: String?,
    onNewChatClick: () -> Unit,
    onConversationClick: (conversationId: String) -> Unit,
    onUserInfoClick: () -> Unit,
    onProvidersClick: () -> Unit,
    onRoutingClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding(),
    ) {
        DrawerHeader(onSearchClick = onSearchClick)

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

        DrawerSectionLabel(
            text = stringResource(R.string.zyn_drawer_recents),
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
        )

        if (conversations.isEmpty()) {
            Text(
                text = stringResource(R.string.zyn_drawer_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                items(
                    key = Conversation::id,
                    items = conversations,
                ) { conversation ->
                    DrawerConversationItem(
                        title = conversation.title,
                        selected = conversation.id == selectedConversationId,
                        onClick = { onConversationClick(conversation.id) },
                    )
                }
            }
        }

        DrawerFooter(
            user = user,
            onNewChatClick = onNewChatClick,
            onUserInfoClick = onUserInfoClick,
        )
    }
}

@Composable
private fun DrawerHeader(
    onSearchClick: () -> Unit,
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

        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = ZynIcons.Search,
                contentDescription = stringResource(R.string.zyn_drawer_search),
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
