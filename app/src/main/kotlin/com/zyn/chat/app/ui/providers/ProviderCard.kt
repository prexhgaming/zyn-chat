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

package com.zyn.chat.app.ui.providers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.data.providers.AiProvider
import com.zyn.chat.app.ui.brand.markFor
import com.zyn.chat.app.ui.components.BrandMark
import com.zyn.chat.app.ui.icons.ZynIcons
import com.zyn.chat.app.ui.theme.accent

/**
 * One configured provider, with its key status and the actions that act on it.
 *
 * The card always states whether a key is present, because a provider without one cannot answer: the
 * person must never have to guess why a model is silently unusable.
 *
 * @param provider The provider to show.
 * @param busy Whether an operation on this provider is in flight and the controls should wait.
 * @param onSetEnabled Invoked to enable or disable the provider.
 * @param onRefreshModels Invoked to re-sync its model list.
 * @param onReplaceKey Invoked to open the key sheet.
 * @param onRemove Invoked after the person confirmed removal.
 * @param modifier Applied to the card.
 */
@Composable
internal fun ProviderCard(
    provider: AiProvider,
    busy: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onRefreshModels: () -> Unit,
    onReplaceKey: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRemoveConfirmation by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            ProviderHeader(
                provider = provider,
                showMenu = showMenu,
                busy = busy,
                onSetEnabled = onSetEnabled,
                onShowMenuChange = { showMenu = it },
                onRefreshModels = onRefreshModels,
                onReplaceKey = onReplaceKey,
                onRemoveRequested = { showRemoveConfirmation = true },
            )

            KeyStatus(
                provider = provider,
                onReplaceKey = onReplaceKey,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }

    if (showRemoveConfirmation) {
        RemoveProviderDialog(
            providerName = provider.name,
            onConfirm = {
                showRemoveConfirmation = false
                onRemove()
            },
            onDismiss = { showRemoveConfirmation = false },
        )
    }
}

@Composable
private fun ProviderHeader(
    provider: AiProvider,
    showMenu: Boolean,
    busy: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onShowMenuChange: (Boolean) -> Unit,
    onRefreshModels: () -> Unit,
    onReplaceKey: () -> Unit,
    onRemoveRequested: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(label = provider.name, markRes = markFor(provider.id, ""))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = providerSubtitle(provider),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Switch(
            checked = provider.enabled,
            onCheckedChange = onSetEnabled,
            enabled = !busy,
        )

        ProviderMenu(
            expanded = showMenu,
            onExpandedChange = onShowMenuChange,
            onRefreshModels = onRefreshModels,
            onReplaceKey = onReplaceKey,
            onRemoveRequested = onRemoveRequested,
        )
    }
}

@Composable
private fun ProviderMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRefreshModels: () -> Unit,
    onReplaceKey: () -> Unit,
    onRemoveRequested: () -> Unit,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                imageVector = ZynIcons.More,
                contentDescription = stringResource(R.string.zyn_action_more),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            ProviderMenuItem(
                icon = ZynIcons.Refresh,
                label = stringResource(R.string.zyn_providers_refresh),
                onClick = {
                    onExpandedChange(false)
                    onRefreshModels()
                },
            )
            ProviderMenuItem(
                icon = ZynIcons.Key,
                label = stringResource(R.string.zyn_providers_replace_key),
                onClick = {
                    onExpandedChange(false)
                    onReplaceKey()
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProviderMenuItem(
                icon = ZynIcons.Trash,
                label = stringResource(R.string.zyn_providers_remove),
                tint = MaterialTheme.colorScheme.error,
                onClick = {
                    onExpandedChange(false)
                    onRemoveRequested()
                },
            )
        }
    }
}

@Composable
private fun ProviderMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    DropdownMenuItem(
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = tint,
            )
        },
        text = {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tint)
        },
    )
}

@Composable
private fun KeyStatus(
    provider: AiProvider,
    onReplaceKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val missing = provider.endpoint.keyRequired && provider.connection.needsKey
    val tint = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ZynIcons.Key,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = tint,
        )

        Text(
            text = keyStatusText(provider),
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (missing) {
            TextButton(onClick = onReplaceKey) {
                Text(
                    text = stringResource(R.string.zyn_providers_add_key),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.accent.primary,
                )
            }
        }
    }
}

/** Copy describing the provider's key state. */
@Composable
private fun keyStatusText(provider: AiProvider): String = when {
    !provider.endpoint.keyRequired -> stringResource(R.string.zyn_providers_key_not_required)
    provider.connection.keyHint != null -> provider.connection.keyHint
    else -> stringResource(R.string.zyn_providers_key_missing)
}

/** Copy describing what the provider offers. */
@Composable
private fun providerSubtitle(provider: AiProvider): String = when {
    provider.models.isEmpty() -> provider.endpoint.baseUrl
    else -> stringResource(R.string.zyn_providers_model_count, provider.models.size)
}

/** Confirms removal, making clear that the key goes with it. */
@Composable
private fun RemoveProviderDialog(
    providerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zyn_providers_remove_title, providerName)) },
        text = { Text(stringResource(R.string.zyn_providers_remove_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.zyn_providers_remove))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(stringResource(R.string.zyn_action_cancel))
            }
        },
    )
}
