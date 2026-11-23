package com.zyn.chat.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.ui.brand.markFor
import com.zyn.chat.app.ui.theme.RowShape

/**
 * A recent conversation, rendered as a compact single-line row: brand mark, then title.
 *
 * Long press opens the row menu, which is where a conversation is renamed or deleted. The menu is
 * the only affordance on the row besides tapping it, so the list stays scannable.
 *
 * @param title Conversation title. Truncated with an ellipsis when it does not fit.
 * @param selected Whether this conversation is the one currently open.
 * @param modelSlug Model the conversation was last answered with, used for its brand mark.
 * @param onClick Invoked when the row is tapped.
 * @param onRename Invoked when "Rename" is chosen from the row menu.
 * @param onDelete Invoked when "Delete" is chosen from the row menu.
 * @param modifier Applied to the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerConversationItem(
    title: String,
    selected: Boolean,
    modelSlug: String?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    val background =
        if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            Color.Transparent
        }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RowShape)
                .background(background)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(
                label = title,
                markRes = markFor("", modelSlug.orEmpty()),
                size = 20.dp,
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.zyn_conversation_rename)) },
                onClick = {
                    showMenu = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.zyn_conversation_delete)) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
            )
        }
    }
}
