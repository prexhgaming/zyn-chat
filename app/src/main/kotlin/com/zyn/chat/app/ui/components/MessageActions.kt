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

package com.zyn.chat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.presentation.chat.ChatUiState
import com.zyn.chat.app.ui.icons.ZynIcons
import kotlinx.coroutines.delay

/** How long the copied tick stays up before returning to the copy glyph. */
private const val COPIED_FEEDBACK_MS = 1_500L

/**
 * The action row that sits under a completed answer.
 *
 * Copy, share and regenerate sit on the left, in the order they are reached for. Nothing is drawn
 * while a response is still being written or is empty, so no action can ever target a half-written
 * message. Copy confirms itself in place: the glyph becomes a tick for a moment.
 *
 * @param message The answer these actions belong to.
 * @param onCopy Invoked to put the answer on the clipboard.
 * @param onShare Invoked to share the answer.
 * @param onRegenerate Invoked to ask for another version of the answer.
 * @param modifier Applied to the row.
 */
@Composable
fun MessageActions(
    message: ChatUiState.Message,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message.isGenerating || message.content.isBlank()) return

    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            icon = if (copied) ZynIcons.Check else ZynIcons.Copy,
            label = stringResource(
                if (copied) R.string.zyn_message_copied else R.string.zyn_action_copy,
            ),
            onClick = {
                onCopy()
                copied = true
            },
        )
        ActionButton(
            icon = ZynIcons.Share,
            label = stringResource(R.string.zyn_action_share),
            onClick = onShare,
        )
        ActionButton(
            icon = ZynIcons.Refresh,
            label = stringResource(R.string.zyn_action_regenerate),
            onClick = onRegenerate,
        )
    }
}

/** One small icon action, sized to stay unobtrusive next to the answer it belongs to. */
@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
