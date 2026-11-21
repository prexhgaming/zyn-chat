package com.zyn.chat.app.ui.chat

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.ui.icons.ZynIcons

/**
 * Round button that opens the attachment menu, offering the photo picker and a general file picker.
 *
 * @param enabled Whether the button can be tapped.
 * @param onPhotosClick Invoked when "Photos" is chosen.
 * @param onFilesClick Invoked when "Files" is chosen.
 * @param modifier Applied to the button.
 */
@Composable
internal fun AttachmentButton(
    enabled: Boolean,
    onPhotosClick: () -> Unit,
    onFilesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                .clickable(enabled = enabled) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZynIcons.Plus,
                contentDescription = stringResource(R.string.zyn_composer_attach),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.zyn_composer_attach_photos)) },
                leadingIcon = { AttachmentMenuIcon(icon = ZynIcons.Image) },
                onClick = {
                    expanded = false
                    onPhotosClick()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.zyn_composer_attach_files)) },
                leadingIcon = { AttachmentMenuIcon(icon = ZynIcons.File) },
                onClick = {
                    expanded = false
                    onFilesClick()
                },
            )
        }
    }
}

@Composable
private fun AttachmentMenuIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Attachment chips staged in the composer, scrollable on one line so the composer never grows tall.
 *
 * @param attachments Staged attachment URIs.
 * @param onRemove Invoked with the URI of the chip that was dismissed.
 * @param modifier Applied to the row.
 */
@Composable
internal fun StagedAttachments(
    attachments: Set<Uri>,
    onRemove: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { uri ->
            StagedAttachmentChip(uri = uri, onRemove = { onRemove(uri) })
        }
    }
}

@Composable
private fun StagedAttachmentChip(
    uri: Uri,
    onRemove: () -> Unit,
) {
    val name = uri.attachmentName()
    val label = if (name.isBlank()) stringResource(R.string.zyn_composer_attachment) else name

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (uri.looksLikeImage()) ZynIcons.Image else ZynIcons.File,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZynIcons.Close,
                contentDescription = stringResource(R.string.zyn_composer_remove_attachment),
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "gif", "bmp")

private fun Uri.looksLikeImage(): Boolean =
    lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty() in IMAGE_EXTENSIONS

private fun Uri.attachmentName(): String =
    lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank).orEmpty()
