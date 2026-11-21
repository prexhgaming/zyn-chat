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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.zyn.chat.app.R
import com.zyn.chat.app.presentation.chat.ChatUiState
import com.zyn.chat.app.ui.icons.ZynIcons
import io.getstream.chat.android.compose.state.messages.attachments.AttachmentState
import io.getstream.chat.android.compose.ui.attachments.content.MediaAttachmentContent
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.models.Attachment
import io.getstream.chat.android.models.Message

/**
 * The pictures in a message, in the order they were attached.
 *
 * An attachment counts as a picture when it is typed as one or carries an image location, which
 * covers both pictures picked on this device and pictures a provider sends back.
 *
 * @param message Message to inspect.
 */
internal fun imageAttachmentsOf(message: ChatUiState.Message): List<Attachment> = message.attachments
    .filter { attachment -> attachment.type == IMAGE_TYPE || !attachment.imageUrl.isNullOrBlank() }
    .filter { attachment -> !attachment.imageUrl.isNullOrBlank() }

/**
 * Full-screen viewer for one picture from the conversation.
 *
 * A picture stored on this device is decoded straight from its URI at full preview size; anything
 * else — a remote URL, or a local file that could not be decoded — is drawn by the same attachment
 * renderer the message bubble uses, so there is one image pipeline rather than two. Tapping
 * anywhere, or the system back gesture, closes it.
 *
 * @param attachment Picture to show.
 * @param onDismiss Invoked when the viewer is closed.
 */
@Composable
internal fun ImagePreviewDialog(
    attachment: Attachment,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val localImage = attachment.imageUrl
            ?.toUri()
            ?.let { uri -> rememberUriImage(uri = uri, maxSizePx = PREVIEW_PX) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = BACKDROP_ALPHA))
                .clickable(onClick = onDismiss)
                .systemBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            if (localImage != null) {
                Image(
                    bitmap = localImage,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ChatTheme {
                    MediaAttachmentContent(
                        state = AttachmentState(
                            message = Message(attachments = listOf(attachment)),
                            isMine = true,
                        ),
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = ZynIcons.Close,
                    contentDescription = stringResource(R.string.zyn_image_preview_close),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Attachment type Stream uses for pictures. */
private const val IMAGE_TYPE = "image"

/** Longest side a preview is decoded at. */
private const val PREVIEW_PX = 2_048

private const val BACKDROP_ALPHA = 0.94f
