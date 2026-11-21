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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.zyn.chat.app.R
import com.zyn.chat.app.data.providers.AiModel
import com.zyn.chat.app.data.providers.AiProvider
import com.zyn.chat.app.data.providers.ModelBrands
import com.zyn.chat.app.data.providers.ProvidersState
import com.zyn.chat.app.ui.brand.markOf
import com.zyn.chat.app.ui.components.BrandMark
import com.zyn.chat.app.ui.icons.ZynIcons
import com.zyn.chat.app.ui.theme.AccentColors
import com.zyn.chat.app.ui.theme.accent

/** Ceiling for the model list so the sheet stays a sheet instead of a full-screen page. */
private val ModelListMaxHeight = 420.dp

/**
 * Draggable bottom sheet for picking the active model.
 *
 * The sheet always opens, even with no providers configured: the header and the "Providers" action
 * stay visible and the list area explains what to do instead of showing invented providers or models.
 *
 * @param state Provider configuration and current selection.
 * @param onProviderSelected Invoked with the id of the provider tab that was tapped.
 * @param onModelSelected Invoked with the id of the model that was tapped.
 * @param onManageProviders Opens provider management. The drawer's "Providers" entry runs this very
 *   same action, so both entry points share one implementation and one state.
 * @param onDismiss Invoked when the sheet is dismissed.
 * @param sheetState State of the sheet, so the caller can hide it with an animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPickerSheet(
    state: ProvidersState,
    onProviderSelected: (providerId: String) -> Unit,
    onModelSelected: (modelId: String) -> Unit,
    onManageProviders: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val query = filter.trim()
    val visibleModels = if (query.isEmpty()) {
        state.modelsOfCurrentProvider
    } else {
        state.modelsOfCurrentProvider.filter { model ->
            model.slug.contains(query, ignoreCase = true) ||
                model.displayName.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        BottomSheetDefaults.DragHandle()

        SheetHeader(
            providerName = state.currentProvider?.name,
            onManageProviders = onManageProviders,
        )

        if (state.activeProviders.isEmpty()) {
            EmptyState(onManageProviders = onManageProviders)
        } else {
            ProviderTabs(
                providers = state.activeProviders,
                selectedProviderId = state.currentProvider?.id,
                onProviderSelected = onProviderSelected,
            )

            // Providers can report dozens of models, so the list is filterable. The query matches the
            // readable name and the provider's own slug.
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.zyn_models_filter)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )

            ModelList(
                models = visibleModels,
                selectedModelId = state.selectedModelId,
                onModelSelected = onModelSelected,
            )
        }
    }
}

@Composable
private fun SheetHeader(
    providerName: String?,
    onManageProviders: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Text(
                text = stringResource(R.string.zyn_models_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = providerName ?: stringResource(R.string.zyn_models_no_provider),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(modifier = Modifier.weight(1f))

        TextButton(onClick = onManageProviders) {
            Text(
                text = stringResource(R.string.zyn_models_providers_action),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.accent.primary,
            )
        }
    }
}

@Composable
private fun ProviderTabs(
    providers: List<AiProvider>,
    selectedProviderId: String?,
    onProviderSelected: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(key = AiProvider::id, items = providers) { provider ->
            ProviderTab(
                provider = provider,
                selected = provider.id == selectedProviderId,
                onClick = { onProviderSelected(provider.id) },
            )
        }
    }
}

@Composable
private fun ProviderTab(
    provider: AiProvider,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.accent
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent.tint else MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (selected) accent.border else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(
            label = provider.name,
            mark = markOf(ModelBrands.of(provider.id, "")),
            size = 22.dp,
        )
        Text(
            text = provider.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModelList(
    models: List<AiModel>,
    selectedModelId: String?,
    onModelSelected: (String) -> Unit,
) {
    if (models.isEmpty()) {
        Text(
            text = stringResource(R.string.zyn_models_provider_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ModelListMaxHeight),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(key = AiModel::id, items = models) { model ->
            ModelRow(
                model = model,
                selected = model.id == selectedModelId,
                onClick = { onModelSelected(model.id) },
            )
        }
    }
}

@Composable
private fun ModelRow(
    model: AiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.accent
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) accent.tint else MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (selected) accent.border else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(
            label = model.displayName,
            mark = markOf(ModelBrands.of(model.providerId, model.slug)),
            size = 38.dp,
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) accent.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (model.isFree) {
                    FreeBadge(accent = accent)
                }
            }

            Text(
                text = model.slug,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        model.contextWindowLabel?.let { contextWindow ->
            Text(
                text = contextWindow,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SelectionIndicator(selected = selected, accent = accent)
    }
}

@Composable
private fun FreeBadge(accent: AccentColors) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.tint)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.zyn_models_free),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent.primary,
        )
    }
}

@Composable
private fun SelectionIndicator(
    selected: Boolean,
    accent: AccentColors,
) {
    if (selected) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(accent.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZynIcons.Check,
                contentDescription = stringResource(R.string.zyn_models_selected),
                modifier = Modifier.size(13.dp),
                tint = accent.onPrimary,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

@Composable
private fun EmptyState(onManageProviders: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.zyn_models_empty_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.zyn_models_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onManageProviders,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.zyn_models_empty_action),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.accent.primary,
            )
        }
    }
}
