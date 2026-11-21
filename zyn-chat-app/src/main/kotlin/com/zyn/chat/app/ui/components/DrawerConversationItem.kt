package com.zyn.chat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.ui.theme.RowShape

/**
 * A recent conversation, rendered as a compact single-line row.
 *
 * The row is intentionally lightweight: no card, no avatar, no timestamps — just the title, so a
 * long history stays scannable without excessive scrolling.
 *
 * @param title Conversation title. Truncated with an ellipsis when it does not fit.
 * @param selected Whether this conversation is the one currently open.
 * @param onClick Invoked when the row is tapped.
 * @param modifier Applied to the row.
 */
@Composable
fun DrawerConversationItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background =
        if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            Color.Transparent
        }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        )
    }
}
