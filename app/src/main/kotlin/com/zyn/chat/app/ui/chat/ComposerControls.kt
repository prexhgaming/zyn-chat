/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://github.com/GetStream/stream-chat-android-ai/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zyn.chat.app.ui.chat

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
        FloatingActionMenu(
            expanded = expanded,
            onPhotoClick = onPhotosClick,
            onDocumentClick = onFilesClick,
            onToggle = { if (enabled) expanded = !expanded },
        )
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
 * Attachments staged in the composer, shown on one line inside the composer's own field.
 *
 * A picture is drawn as a small copy of itself, so what will be sent is visible before it is sent;
 * anything that is not a picture stays a chip with its file name. The row scrolls rather than wraps,
 * so the composer never grows taller than the person expects.
 *
 * @param attachments Staged attachment URIs.
 * @param onRemove Invoked with the URI of the attachment that was dismissed.
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
            key(uri) {
                if (uri.looksLikeImage()) {
                    StagedImageThumbnail(uri = uri, onRemove = { onRemove(uri) })
                } else {
                    StagedAttachmentChip(uri = uri, onRemove = { onRemove(uri) })
                }
            }
        }
    }
}

/** A staged picture, drawn as the picture itself. */
@Composable
private fun StagedImageThumbnail(
    uri: Uri,
    onRemove: () -> Unit,
) {
    val image = rememberUriImage(uri = uri, maxSizePx = THUMBNAIL_PX)
    val shape = RoundedCornerShape(12.dp)

    Box(modifier = Modifier.size(THUMBNAIL_SIZE)) {
        Box(
            modifier = Modifier
                .size(THUMBNAIL_SIZE)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = uri.attachmentName().takeIf(String::isNotBlank),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(THUMBNAIL_SIZE)
                        .clip(shape),
                )
            } else {
                // Not decodable: keep the slot the same size so the row does not jump.
                Icon(
                    imageVector = ZynIcons.Image,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(REMOVE_BADGE_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZynIcons.Close,
                contentDescription = stringResource(R.string.zyn_composer_remove_attachment),
                modifier = Modifier.size(12.dp),
                tint = Color.White,
            )
        }
    }
}

/** A staged file that is not a picture, kept as a chip with its name. */
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
            imageVector = ZynIcons.File,
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

private val THUMBNAIL_SIZE = 56.dp

/** Longest side a staged thumbnail is decoded at; comfortably above its drawn size. */
private const val THUMBNAIL_PX = 192

private val REMOVE_BADGE_SIZE = 20.dp
private const val SCRIM_ALPHA = 0.55f
