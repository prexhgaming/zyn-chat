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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.ui.icons.ZynIcons

/**
 * Actions for a message, shown in a bottom sheet after a long press.
 *
 * Recovery actions come first when they apply: a failed message offers retry and edit, any message
 * can be copied. Destructive actions sit behind a divider at the bottom and only appear when the
 * caller can actually perform them.
 *
 * @param onCopy Invoked when copy is chosen.
 * @param onRetry Invoked when retry is chosen; `null` hides it for messages that did not fail.
 * @param onEdit Invoked when edit is chosen; `null` hides it.
 * @param onDelete Invoked when delete is chosen; `null` hides it.
 * @param onDismiss Invoked when the sheet is dismissed.
 * @param sheetState Bottom sheet state.
 * @param modifier Applied to the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionMenu(
    onCopy: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            onRetry?.let { retry ->
                ActionItem(
                    icon = ZynIcons.Refresh,
                    label = stringResource(R.string.zyn_error_retry),
                    onClick = {
                        onDismiss()
                        retry()
                    },
                )
            }

            onEdit?.let { edit ->
                ActionItem(
                    icon = ZynIcons.Edit,
                    label = stringResource(R.string.zyn_error_edit),
                    onClick = {
                        onDismiss()
                        edit()
                    },
                )
            }

            ActionItem(
                icon = ZynIcons.Copy,
                label = stringResource(R.string.zyn_action_copy),
                onClick = {
                    onDismiss()
                    onCopy()
                },
            )

            onDelete?.let { delete ->
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                ActionItem(
                    icon = ZynIcons.Trash,
                    label = stringResource(R.string.zyn_action_delete),
                    onClick = {
                        onDismiss()
                        delete()
                    },
                    destructive = true,
                )
            }
        }
    }
}

/**
 * One tappable row of the menu: icon, label, and the error colour when the action is destructive.
 */
@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
        }
    }
}
