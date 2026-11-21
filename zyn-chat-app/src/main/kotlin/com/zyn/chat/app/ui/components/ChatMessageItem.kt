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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.presentation.chat.ChatUiState
import com.zyn.chat.app.ui.theme.BubbleShape
import com.zyn.chat.compose.ui.component.AIMessageParts
import com.zyn.chat.compose.ui.component.StreamingText
import io.getstream.chat.android.compose.state.messages.attachments.AttachmentState
import io.getstream.chat.android.compose.ui.attachments.content.MediaAttachmentContent
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.models.Message

/**
 * Displays a single chat message.
 *
 * The assistant answers as plain, full-width text; the person's own messages sit on the right in a
 * soft bubble. Sharing the same text styles keeps the two sides visually consistent.
 *
 * @param message The message to display.
 * @param modifier Applied to the message.
 */
@Composable
public fun ChatMessageItem(
    message: ChatUiState.Message,
    modifier: Modifier = Modifier,
) {
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * MAX_BUBBLE_FRACTION).dp

    SelectionContainer(modifier = modifier) {
        when (message.role) {
            is ChatUiState.Message.Role.Assistant -> AssistantMessage(message = message)

            is ChatUiState.Message.Role.User -> BubbleMessage(
                message = message,
                maxBubbleWidth = maxBubbleWidth,
                alignment = Alignment.CenterEnd,
            )

            is ChatUiState.Message.Role.Other -> BubbleMessage(
                message = message,
                maxBubbleWidth = maxBubbleWidth,
                alignment = Alignment.CenterStart,
            )
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatUiState.Message,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (message.parts.isNotEmpty()) {
            AIMessageParts(parts = message.parts)
        }
        if (message.content.isNotBlank()) {
            StreamingText(
                text = message.content,
                animate = message.isGenerating,
            )
        }
    }
}

@Composable
private fun BubbleMessage(
    message: ChatUiState.Message,
    maxBubbleWidth: Dp,
    alignment: Alignment,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        MessageBubble(modifier = Modifier.widthIn(max = maxBubbleWidth)) {
            if (message.attachments.isNotEmpty()) {
                ChatTheme {
                    MediaAttachmentContent(
                        state = AttachmentState(
                            message = Message(
                                text = message.content,
                                attachments = message.attachments,
                            ),
                            isMine = true,
                        ),
                    )
                }
            }

            if (message.content.isNotBlank()) {
                StreamingText(
                    text = message.content,
                    animate = false,
                )
            }
        }
    }
}

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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Share of the screen width a bubble may take up. */
private const val MAX_BUBBLE_FRACTION = 0.82f
