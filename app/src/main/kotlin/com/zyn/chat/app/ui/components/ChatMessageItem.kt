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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.zyn.chat.app.R
import com.zyn.chat.app.presentation.chat.ChatUiState
import com.zyn.chat.app.ui.chat.ImagePreviewDialog
import com.zyn.chat.app.ui.chat.imageAttachmentsOf
import com.zyn.chat.app.ui.chat.rememberUriImage
import com.zyn.chat.app.ui.icons.ZynIcons
import com.zyn.chat.app.ui.theme.BubbleShape
import com.zyn.chat.compose.ui.component.AIMessageParts
import com.zyn.chat.compose.ui.component.StreamingText
import io.getstream.chat.android.compose.state.messages.attachments.AttachmentState
import io.getstream.chat.android.compose.ui.attachments.content.MediaAttachmentContent
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.models.Attachment
import io.getstream.chat.android.models.Message

/**
 * Displays a single chat message.
 *
 * The assistant answers as plain, full-width text; the person's own messages sit on the right in a
 * soft bubble. Sharing the same text styles keeps the two sides visually consistent. Attachments
 * appear on both sides — a picture the person attached and a picture an answer refers to are both
 * worth seeing — and tapping one opens it full screen.
 *
 * @param message The message to display.
 * @param modifier Applied to the message.
 * @param onErrorAction Invoked when the error indicator of a failed message is tapped.
 * @param onLongPress Invoked when the message is long pressed, to open its action menu.
 * @param onCopy Invoked to copy an assistant answer.
 * @param onShare Invoked to share an assistant answer.
 * @param onRegenerate Invoked to ask for another version; only offered on the latest answer.
 * @param onFeedback Invoked to rate an assistant answer.
 * @param isLastAssistant Whether this answer is the most recent one, which is the only one whose
 *   replacement a regenerate can mean.
 */
@Composable
public fun ChatMessageItem(
    message: ChatUiState.Message,
    modifier: Modifier = Modifier,
    onErrorAction: ((ChatUiState.Message) -> Unit)? = null,
    onLongPress: ((ChatUiState.Message) -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onFeedback: ((ChatUiState.Message.Feedback?) -> Unit)? = null,
    isLastAssistant: Boolean = false,
) {
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * MAX_BUBBLE_FRACTION).dp

    // The picture currently shown full screen, if any.
    var previewImage by remember { mutableStateOf<Attachment?>(null) }
    val openImage: (Attachment) -> Unit = { attachment -> previewImage = attachment }

    SelectionContainer(modifier = modifier) {
        when (message.role) {
            is ChatUiState.Message.Role.Assistant -> AssistantMessage(
                message = message,
                onImageClick = openImage,
                onCopy = onCopy,
                onShare = onShare,
                // Regenerate replaces the current answer, so older answers have no button for it.
                onRegenerate = onRegenerate.takeIf { isLastAssistant },
                onFeedback = onFeedback,
            )

            is ChatUiState.Message.Role.User -> BubbleMessage(
                message = message,
                maxBubbleWidth = maxBubbleWidth,
                alignment = Alignment.CenterEnd,
                onImageClick = openImage,
                onErrorClick = { onErrorAction?.invoke(message) },
                onLongClick = { onLongPress?.invoke(message) },
            )

            is ChatUiState.Message.Role.Other -> BubbleMessage(
                message = message,
                maxBubbleWidth = maxBubbleWidth,
                alignment = Alignment.CenterStart,
                onImageClick = openImage,
                onLongClick = { onLongPress?.invoke(message) },
            )
        }
    }

    previewImage?.let { attachment ->
        ImagePreviewDialog(
            attachment = attachment,
            onDismiss = { previewImage = null },
        )
    }
}

@Composable
private fun AssistantMessage(
    message: ChatUiState.Message,
    onImageClick: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
    onCopy: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onFeedback: ((ChatUiState.Message.Feedback?) -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (message.parts.isNotEmpty()) {
            AIMessageParts(parts = message.parts)
        }

        MessageAttachments(
            message = message,
            isMine = false,
            onImageClick = onImageClick,
        )

        if (message.content.isNotBlank()) {
            // The provider already paces the text, so it is drawn as it arrives. Animating it a
            // second time made the screen fall behind a fast model by many words.
            StreamingText(
                text = message.content,
                animate = false,
            )
        }

        if (onCopy != null && onShare != null && onFeedback != null) {
            MessageActions(
                message = message,
                onCopy = onCopy,
                onShare = onShare,
                onRegenerate = onRegenerate ?: {},
                feedback = message.feedback,
                onFeedback = onFeedback,
            )
        }
    }
}

@Composable
private fun BubbleMessage(
    message: ChatUiState.Message,
    maxBubbleWidth: Dp,
    alignment: Alignment,
    onImageClick: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
    onErrorClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        MessageBubble(
            modifier = Modifier
                .widthIn(max = maxBubbleWidth)
                .then(
                    if (onLongClick != null) {
                        Modifier.pointerInput(onLongClick) {
                            detectTapGestures(onLongPress = { onLongClick() })
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            MessageAttachments(
                message = message,
                isMine = true,
                onImageClick = onImageClick,
            )

            if (message.content.isNotBlank()) {
                StreamingText(
                    text = message.content,
                    animate = false,
                )
            }

            // Error indicator for failed user messages; tapping it opens the recovery actions.
            if (message.isFailed && message.role == ChatUiState.Message.Role.User) {
                ErrorIndicator(
                    errorReason = message.errorReason ?: stringResource(R.string.zyn_error_failed_send),
                    onClick = { onErrorClick?.invoke() },
                )
            }
        }
    }
}

/**
 * Everything attached to a message.
 *
 * Renders nothing when there is nothing attached. Pictures staged on this device are drawn from
 * their own URI and open full screen when tapped; everything else goes through the Stream renderer.
 */
@Composable
private fun MessageAttachments(
    message: ChatUiState.Message,
    isMine: Boolean,
    onImageClick: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message.attachments.isEmpty()) return

    val pictures = imageAttachmentsOf(message)
    // Pictures staged on this device carry a content:// URI, which the message renders itself. The
    // Stream renderer is kept for everything else, such as a picture the provider sends back.
    val localPictures = pictures.filter { attachment -> attachment.imageUrl.isLocalPictureUri() }
    val rest = message.attachments.filterNot { attachment -> attachment in localPictures }
    val remotePicture = pictures.firstOrNull { attachment -> attachment !in localPictures }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        localPictures.forEach { attachment ->
            LocalPicture(attachment = attachment, onImageClick = onImageClick)
        }

        if (rest.isNotEmpty()) {
            ChatTheme {
                Box(
                    modifier = Modifier.then(
                        if (remotePicture != null) {
                            Modifier.clickable { onImageClick(remotePicture) }
                        } else {
                            Modifier
                        },
                    ),
                ) {
                    MediaAttachmentContent(
                        state = AttachmentState(
                            message = Message(
                                text = message.content,
                                attachments = rest,
                            ),
                            isMine = isMine,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * A picture that lives on this device, drawn from its own URI.
 *
 * The Stream attachment renderer expects an upload to describe the file; a staged picture only has
 * its content URI, so it is decoded here and drawn at the message's width.
 *
 * @param attachment Picture to draw.
 * @param onImageClick Invoked when the picture is tapped.
 */
@Composable
private fun LocalPicture(
    attachment: Attachment,
    onImageClick: (Attachment) -> Unit,
) {
    val uri = attachment.imageUrl?.toUri() ?: return
    val image = rememberUriImage(uri = uri, maxSizePx = MESSAGE_PICTURE_PX)
    val shape = RoundedCornerShape(16.dp)

    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = attachment.name ?: stringResource(R.string.zyn_message_image),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MESSAGE_PICTURE_MAX_HEIGHT)
                .clip(shape)
                .clickable { onImageClick(attachment) },
        )
    } else {
        // The URI could not be decoded: keep a calm placeholder instead of an empty gap.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MESSAGE_PICTURE_MAX_HEIGHT)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZynIcons.Image,
                contentDescription = stringResource(R.string.zyn_message_image),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Whether an attachment's URL points at a file this app can read directly. */
private fun String?.isLocalPictureUri(): Boolean =
    this?.let { url -> url.startsWith("content://") || url.startsWith("file://") } == true

@Composable
private fun MessageBubble(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = BubbleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

/** Share of the screen width a bubble may take up. */
private const val MAX_BUBBLE_FRACTION = 0.82f

/** Longest side a picture in a message is decoded at. */
private const val MESSAGE_PICTURE_PX = 1_024

/** Tallest a picture in a message may be drawn. */
private val MESSAGE_PICTURE_MAX_HEIGHT = 280.dp

/**
 * Error indicator for failed messages.
 *
 * Shows a warning icon and error message that can be tapped.
 *
 * @param errorReason The error description
 * @param onClick Invoked when the indicator is tapped
 */
@Composable
private fun ErrorIndicator(
    errorReason: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZynIcons.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = errorReason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
