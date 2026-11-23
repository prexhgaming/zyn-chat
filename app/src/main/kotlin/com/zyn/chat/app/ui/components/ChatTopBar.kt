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

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.ui.theme.AppTheme

/**
 * Chat header: navigation trigger, the conversation title, and the actions for the current chat.
 *
 * Deliberately quiet — flat icon buttons on the background, with the title taking the free space —
 * so the conversation, not the chrome, carries the screen.
 *
 * @param title Conversation title shown in the middle.
 * @param onMenuClick Invoked when the navigation trigger is tapped.
 * @param onNewChatClick Invoked when "New chat" is tapped, if the action is available.
 * @param onDeleteClick Invoked when "Delete" is chosen, if the action is available.
 * @param modifier Applied to the bar.
 */
@Composable
fun ChatTopBar(
    title: String,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNewChatClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarIconButton(
            icon = R.drawable.ic_menu,
            label = stringResource(R.string.zyn_action_menu),
            onClick = onMenuClick,
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )

        if (onNewChatClick != null) {
            TopBarIconButton(
                icon = R.drawable.ic_new,
                label = stringResource(R.string.zyn_action_new_chat),
                onClick = onNewChatClick,
            )
        }

        if (onDeleteClick != null) {
            ChatOverflowMenu(
                title = title,
                onDeleteClick = onDeleteClick,
            )
        }
    }
}

@Composable
private fun TopBarIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatOverflowMenu(
    title: String,
    onDeleteClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    TopBarIconButton(
        icon = R.drawable.ic_more,
        label = stringResource(R.string.zyn_action_more),
        onClick = { expanded = true },
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .sizeIn(maxWidth = 200.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        DropdownMenuItem(
            text = { Text(stringResource(R.string.zyn_action_delete)) },
            onClick = {
                expanded = false
                onDeleteClick()
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            colors = MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.error,
                leadingIconColor = MaterialTheme.colorScheme.error,
            ),
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatTopBarPreview() {
    AppTheme {
        ChatTopBar(
            title = "Trip to Kyoto",
            onMenuClick = {},
            onNewChatClick = {},
            onDeleteClick = {},
        )
    }
}
