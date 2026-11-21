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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.data.providers.KnownProvider
import com.zyn.chat.app.data.providers.ProviderCatalog
import com.zyn.chat.app.data.providers.ProviderError
import com.zyn.chat.app.data.providers.ProviderKind
import com.zyn.chat.app.presentation.providers.ProviderSyncState
import com.zyn.chat.app.ui.components.BrandMark
import com.zyn.chat.app.ui.icons.ZynIcons
import com.zyn.chat.app.ui.theme.accent

/** Ceiling for the catalog list, so the sheet stays a sheet. */
private val CatalogMaxHeight = 420.dp

/**
 * Adds a provider in two steps: pick it from the known catalog, then connect it.
 *
 * Everything is validated against the provider's real endpoint before it is saved, so the sheet only
 * closes on a confirmed key. The secret is typed here and handed straight to the caller — it is never
 * held in this composable's state beyond the field.
 *
 * @param syncState Progress and outcome of the validation call.
 * @param onSave Invoked with the provider details once "Validate & save" is tapped.
 * @param onDismiss Invoked to close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddProviderSheet(
    syncState: ProviderSyncState,
    onSave: (KnownProvider, String, String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<KnownProvider?>(null) }

    // The sheet stays open on failure so the key can be corrected, and is closed by the caller on
    // success: only a confirmed provider may dismiss it.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val provider = selected
        if (provider == null) {
            CatalogStep(onSelect = { selected = it })
        } else {
            SetupStep(
                provider = provider,
                syncState = syncState,
                onBack = { selected = null },
                onSubmit = { name, baseUrl, key -> onSave(provider, name, baseUrl, key) },
            )
        }
    }
}

/** Step one: choose from the known providers. */
@Composable
private fun CatalogStep(onSelect: (KnownProvider) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) { ProviderCatalog.search(query) }

    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = stringResource(R.string.zyn_providers_catalog_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.zyn_providers_catalog_search)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )

        // A single list for every section: nested scrollables inside a draggable sheet fight each
        // other, and a section list per provider kind would not fit on screen.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = CatalogMaxHeight),
        ) {
            ProviderKind.entries.forEach { kind ->
                val ofKind = matches.filter { entry -> entry.kind == kind }
                if (ofKind.isNotEmpty()) {
                    item(key = "label-$kind") { CatalogSectionLabel(kind) }
                    items(key = KnownProvider::id, items = ofKind) { entry ->
                        CatalogRow(entry = entry, onSelect = onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSectionLabel(kind: ProviderKind) {
    Text(
        text = stringResource(
            when (kind) {
                ProviderKind.Cloud -> R.string.zyn_providers_group_cloud
                ProviderKind.Local -> R.string.zyn_providers_group_local
                ProviderKind.Custom -> R.string.zyn_providers_group_custom
            },
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun CatalogRow(
    entry: KnownProvider,
    onSelect: (KnownProvider) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.supported) { onSelect(entry) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(label = entry.displayName, size = 30.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (entry.supported) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.unsupportedReason ?: entry.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Step two: connect the chosen provider and validate it. */
@Composable
private fun SetupStep(
    provider: KnownProvider,
    syncState: ProviderSyncState,
    onBack: () -> Unit,
    onSubmit: (String, String, String?) -> Unit,
) {
    var name by rememberSaveable(provider.id) {
        mutableStateOf(if (provider.kind == ProviderKind.Custom) "" else provider.displayName)
    }
    var baseUrl by rememberSaveable(provider.id) { mutableStateOf(provider.baseUrl) }
    var key by rememberSaveable(provider.id) { mutableStateOf("") }
    var keyVisible by rememberSaveable { mutableStateOf(false) }

    val missingKey = provider.keyRequired && key.isBlank()
    val missingCustom = provider.kind == ProviderKind.Custom && (name.isBlank() || baseUrl.isBlank())

    Column(
        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(label = provider.displayName)
            Text(
                text = stringResource(R.string.zyn_providers_setup_title, provider.displayName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (provider.kind == ProviderKind.Custom) {
            SetupField(
                label = stringResource(R.string.zyn_providers_name_label),
                value = name,
                onValueChange = { name = it },
            )
        }

        if (provider.editableBaseUrl || provider.kind == ProviderKind.Custom) {
            SetupField(
                label = stringResource(R.string.zyn_providers_base_url_label),
                value = baseUrl,
                onValueChange = { baseUrl = it },
                keyboardType = KeyboardType.Uri,
            )
        }

        if (provider.keyRequired) {
            SetupField(
                label = stringResource(R.string.zyn_providers_key_label),
                value = key,
                onValueChange = { key = it },
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailing = {
                    TextButton(onClick = { keyVisible = !keyVisible }) {
                        Text(
                            text = stringResource(
                                if (keyVisible) {
                                    R.string.zyn_providers_key_hide
                                } else {
                                    R.string.zyn_providers_key_show
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )

            Text(
                text = stringResource(R.string.zyn_providers_key_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        provider.docsUrl?.let { url ->
            DocsLink(url = url)
        }

        syncState.error?.let { error ->
            Text(
                text = providerErrorText(error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.zyn_action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(modifier = Modifier.weight(1f))

            Button(
                onClick = { onSubmit(name, baseUrl, key.ifBlank { null }) },
                enabled = !syncState.inProgress && !missingKey && !missingCustom,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.accent.primary,
                    contentColor = MaterialTheme.colorScheme.accent.onPrimary,
                ),
            ) {
                if (syncState.inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.accent.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.zyn_providers_validate),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
        trailingIcon = trailing,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Opens the provider's key page in a browser, so nobody has to hunt for it. */
@Composable
private fun DocsLink(url: String) {
    val uriHandler = LocalUriHandler.current

    TextButton(
        onClick = { runCatching { uriHandler.openUri(url) } },
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ZynIcons.Key,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.accent.primary,
            )
            Text(
                text = stringResource(R.string.zyn_providers_get_key),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.accent.primary,
            )
        }
    }
}

/**
 * Replaces the stored key of an existing provider, validating it first.
 *
 * @param providerName Provider the key belongs to.
 * @param inProgress Whether validation is running.
 * @param error Failure of the last attempt, if any.
 * @param onSave Invoked with the new key.
 * @param onDismiss Invoked to close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApiKeySheet(
    providerName: String,
    inProgress: Boolean,
    error: ProviderError?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var key by rememberSaveable { mutableStateOf("") }
    var keyVisible by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.zyn_providers_replace_key_title, providerName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            SetupField(
                label = stringResource(R.string.zyn_providers_key_label),
                value = key,
                onValueChange = { key = it },
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailing = {
                    TextButton(onClick = { keyVisible = !keyVisible }) {
                        Text(
                            text = stringResource(
                                if (keyVisible) {
                                    R.string.zyn_providers_key_hide
                                } else {
                                    R.string.zyn_providers_key_show
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )

            Text(
                text = stringResource(R.string.zyn_providers_key_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            error?.let {
                Text(
                    text = providerErrorText(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.zyn_action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box(modifier = Modifier.weight(1f))

                Button(
                    onClick = { onSave(key) },
                    enabled = key.isNotBlank() && !inProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.accent.primary,
                        contentColor = MaterialTheme.colorScheme.accent.onPrimary,
                    ),
                ) {
                    if (inProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.accent.onPrimary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.zyn_providers_validate),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
