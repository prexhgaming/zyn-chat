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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.data.providers.AiProvider
import com.zyn.chat.app.data.providers.KnownProvider
import com.zyn.chat.app.data.providers.ProvidersState
import com.zyn.chat.app.presentation.providers.ProviderSyncState
import com.zyn.chat.app.ui.icons.ZynIcons
import com.zyn.chat.app.ui.theme.accent

/**
 * Manages the person's own AI providers and their API keys.
 *
 * The screen is the single provider-management surface: the drawer entry and the model picker's
 * "Providers" action both land here, so there is one implementation of adding, validating, syncing
 * and removing a provider.
 *
 * @param state The configured providers and the current selection.
 * @param syncState Progress and outcome of the network operation in flight.
 * @param onBack Invoked to leave the screen.
 * @param onAddProvider Invoked with the chosen catalog entry once its details are validated.
 * @param onRefreshModels Invoked to re-sync one provider's models.
 * @param onReplaceKey Invoked with a new key for a provider.
 * @param onRemoveProvider Invoked to remove a provider, its models and its key.
 * @param onSetEnabled Invoked to enable or disable a provider.
 * @param onDismissResult Invoked once an outcome has been shown.
 * @param modifier Applied to the screen.
 */
@Suppress("LongParameterList")
@Composable
public fun ProvidersScreen(
    state: ProvidersState,
    syncState: ProviderSyncState,
    onBack: () -> Unit,
    onAddProvider: (KnownProvider, String, String, String?) -> Unit,
    onRefreshModels: (String) -> Unit,
    onReplaceKey: (String, String) -> Unit,
    onRemoveProvider: (String) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onDismissResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var replaceKeyTarget by remember { mutableStateOf<AiProvider?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val successMessage = syncState.modelsFound
        ?.takeIf { syncState.error == null }
        ?.let { count -> stringResource(R.string.zyn_providers_synced, count) }
    val failureMessage = syncState.error?.let { error -> providerErrorText(error) }

    // Reacts to the outcome of the operation in flight: on success the open flow closes itself, on
    // failure it stays open so the key can be corrected. Either way the result is shown once.
    LaunchedEffect(successMessage, failureMessage) {
        val message = failureMessage ?: successMessage ?: return@LaunchedEffect
        if (failureMessage == null) {
            showCatalog = false
            replaceKeyTarget = null
        }
        onDismissResult()
        snackbarHostState.showSnackbar(message)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = { ProvidersTopBar(onBack = onBack, onAdd = { showCatalog = true }) },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyProviders(
                onAdd = { showCatalog = true },
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            ProviderList(
                providers = state.providers,
                syncState = syncState,
                onRefreshModels = onRefreshModels,
                onReplaceKey = { provider -> replaceKeyTarget = provider },
                onRemoveProvider = onRemoveProvider,
                onSetEnabled = onSetEnabled,
                contentPadding = contentPadding,
            )
        }
    }

    if (showCatalog) {
        AddProviderSheet(
            syncState = syncState,
            onSave = { known, name, baseUrl, key ->
                onAddProvider(known, name, baseUrl, key)
            },
            onDismiss = {
                showCatalog = false
                onDismissResult()
            },
        )
    }

    replaceKeyTarget?.let { provider ->
        ApiKeySheet(
            providerName = provider.name,
            inProgress = syncState.inProgress,
            error = syncState.error,
            onSave = { key ->
                onReplaceKey(provider.id, key)
                replaceKeyTarget = null
            },
            onDismiss = {
                replaceKeyTarget = null
                onDismissResult()
            },
        )
    }
}

@Composable
private fun ProvidersTopBar(
    onBack: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = ZynIcons.Back,
                contentDescription = stringResource(R.string.zyn_providers_back),
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = stringResource(R.string.zyn_providers_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onAdd) {
            Icon(
                imageVector = ZynIcons.Plus,
                contentDescription = stringResource(R.string.zyn_providers_add),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ProviderList(
    providers: List<AiProvider>,
    syncState: ProviderSyncState,
    onRefreshModels: (String) -> Unit,
    onReplaceKey: (AiProvider) -> Unit,
    onRemoveProvider: (String) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(key = AiProvider::id, items = providers) { provider ->
            ProviderCard(
                provider = provider,
                busy = syncState.inProgress && syncState.providerId == provider.id,
                onSetEnabled = { enabled -> onSetEnabled(provider.id, enabled) },
                onRefreshModels = { onRefreshModels(provider.id) },
                onReplaceKey = { onReplaceKey(provider) },
                onRemove = { onRemoveProvider(provider.id) },
            )
        }
    }
}

@Composable
private fun EmptyProviders(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.accent.tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZynIcons.Providers,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.accent.primary,
            )
        }

        Text(
            text = stringResource(R.string.zyn_providers_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )

        Text(
            text = stringResource(R.string.zyn_providers_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )

        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.accent.primary,
                contentColor = MaterialTheme.accent.onPrimary,
            ),
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.zyn_providers_empty_action),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
