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

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.data.providers.AiModel
import com.zyn.chat.app.data.providers.AiProvider
import com.zyn.chat.app.data.providers.CapabilityToken
import com.zyn.chat.app.data.providers.ModelGroup
import com.zyn.chat.app.data.providers.ProvidersState
import com.zyn.chat.app.data.providers.capabilityTokensOf
import com.zyn.chat.app.data.providers.groupedModels
import com.zyn.chat.app.ui.brand.markFor
import com.zyn.chat.app.ui.components.BrandMark
import com.zyn.chat.app.ui.icons.ZynIcons
import com.zyn.chat.app.ui.theme.AccentColors
import com.zyn.chat.app.ui.theme.accent

/** Ceiling for the model list so the sheet stays a sheet instead of becoming a full-screen page. */
private val ModelListMaxHeight = 460.dp

private const val SHEET_ANIMATION_MILLIS = 220

/**
 * Draggable bottom sheet for picking the active model.
 *
 * The sheet always opens, even with no providers configured: the header and the "Providers" action
 * stay visible and the list area explains what to do instead of showing invented providers or models.
 *
 * The header, provider tabs and search field sit outside the scrolling list, so they stay put while
 * models scroll underneath them.
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
    val providerName = state.currentProvider?.name

    val visibleModels = remember(state.modelsOfCurrentProvider, query) {
        state.modelsOfCurrentProvider.matching(query)
    }

    // While searching, a flat result list is more useful than sections a person would have to scan.
    val sections = remember(visibleModels, state.selectedModelId, query) {
        if (query.isEmpty()) groupedModels(visibleModels, state.selectedModelId) else emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        BottomSheetDefaults.DragHandle()

        SheetHeader(
            providerName = providerName,
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

            SearchField(
                value = filter,
                onValueChange = { filter = it },
                onClear = { filter = "" },
            )

            AnimatedContent(
                targetState = query.isEmpty(),
                transitionSpec = {
                    (fadeIn(tween(SHEET_ANIMATION_MILLIS)) + slideInVertically { height -> height / 12 })
                        .togetherWith(
                            fadeOut(tween(SHEET_ANIMATION_MILLIS)) +
                                slideOutVertically { height -> -height / 12 },
                        )
                },
                label = "model-results",
            ) { grouped ->
                if (grouped) {
                    GroupedModelList(
                        sections = sections,
                        selectedModelId = state.selectedModelId,
                        onModelSelected = onModelSelected,
                        onOpenProviders = onManageProviders,
                    )
                } else {
                    SearchResults(
                        models = visibleModels,
                        selectedModelId = state.selectedModelId,
                        providerName = providerName,
                        query = query,
                        onModelSelected = onModelSelected,
                        onClearSearch = { filter = "" },
                    )
                }
            }
        }
    }
}

/** Models whose name or provider slug contains [query], case aside. */
private fun List<AiModel>.matching(query: String): List<AiModel> = if (query.isEmpty()) {
    this
} else {
    filter { model ->
        model.slug.contains(query, ignoreCase = true) ||
            model.displayName.contains(query, ignoreCase = true)
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
                style = MaterialTheme.typography.titleLarge,
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
            .padding(top = 12.dp, bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
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

    // Colour animates rather than size, so a tab that becomes selected never shifts the row.
    val background = animateColorAsState(
        targetValue = if (selected) accent.tint else MaterialTheme.colorScheme.surface,
        label = "provider-tab-background",
    )
    val border = animateColorAsState(
        targetValue = if (selected) accent.border else MaterialTheme.colorScheme.outline,
        label = "provider-tab-border",
    )

    Row(
        modifier = Modifier
            .clip(shape)
            .background(background.value)
            .border(width = 1.dp, color = border.value, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(
            label = provider.name,
            markRes = markFor(provider.id, ""),
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
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = {
            Text(
                text = stringResource(R.string.zyn_models_search_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = ZynIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = ZynIcons.Close,
                        contentDescription = stringResource(R.string.zyn_models_search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}

@Composable
private fun GroupedModelList(
    sections: List<Pair<ModelGroup, List<AiModel>>>,
    selectedModelId: String?,
    onModelSelected: (String) -> Unit,
    onOpenProviders: () -> Unit,
) {
    if (sections.isEmpty()) {
        ProviderEmptyState(onOpenProviders = onOpenProviders)
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ModelListMaxHeight),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { (group, models) ->
            item(key = "section-${group.name}") {
                SectionLabel(
                    group = group,
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 2.dp)
                        // Sections move when the grouping changes, so placement is animated.
                        .animateItem(),
                )
            }

            items(key = AiModel::id, items = models) { model ->
                ModelRow(
                    model = model,
                    selected = model.id == selectedModelId,
                    onClick = { onModelSelected(model.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    models: List<AiModel>,
    selectedModelId: String?,
    providerName: String?,
    query: String,
    onModelSelected: (String) -> Unit,
    onClearSearch: () -> Unit,
) {
    if (models.isEmpty()) {
        NoResultsState(
            providerName = providerName,
            query = query,
            onClearSearch = onClearSearch,
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ModelListMaxHeight),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(key = AiModel::id, items = models) { model ->
            ModelRow(
                model = model,
                selected = model.id == selectedModelId,
                onClick = { onModelSelected(model.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun SectionLabel(
    group: ModelGroup,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(group.labelRes()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp),
    )
}

@Composable
private fun ModelRow(
    model: AiModel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.accent
    val shape = RoundedCornerShape(16.dp)

    val background = animateColorAsState(
        targetValue = if (selected) accent.tint else MaterialTheme.colorScheme.surface,
        label = "model-row-background",
    )
    val border = animateColorAsState(
        targetValue = if (selected) accent.border else MaterialTheme.colorScheme.outline,
        label = "model-row-border",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background.value)
            .border(width = 1.dp, color = border.value, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(
            label = model.displayName,
            markRes = markFor(model.providerId, model.slug),
            size = 40.dp,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
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
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (model.isFree) {
                    FreeBadge(accent = accent)
                }
            }

            Text(
                text = model.summary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SelectionIndicator(selected = selected, accent = accent)
    }
}

/**
 * The one-line description under a model's name: its context window followed by what it can do.
 *
 * Anything unknown is stated as unknown rather than left out, so an empty description never reads as
 * "this model can do nothing".
 */
@Composable
private fun AiModel.summary(): String {
    val context = contextWindowLabel
        ?.let { label -> stringResource(R.string.zyn_models_cap_context, label) }
        ?: stringResource(R.string.zyn_models_cap_context_unknown)
    val capabilities = capabilityTokensOf(capabilities).map { token -> stringResource(token.labelRes()) }

    return (listOf(context) + capabilities).joinToString(separator = " · ")
}

@StringRes
private fun ModelGroup.labelRes(): Int = when (this) {
    ModelGroup.Selected -> R.string.zyn_models_section_selected
    ModelGroup.Vision -> R.string.zyn_models_section_vision
    ModelGroup.Tools -> R.string.zyn_models_section_tools
    ModelGroup.Free -> R.string.zyn_models_section_free
    ModelGroup.Other -> R.string.zyn_models_section_other
}

@StringRes
private fun CapabilityToken.labelRes(): Int = when (this) {
    CapabilityToken.ReadsPictures -> R.string.zyn_models_cap_reads_pictures
    CapabilityToken.TextOnly -> R.string.zyn_models_cap_text_only
    CapabilityToken.Tools -> R.string.zyn_models_cap_tools
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
                .size(22.dp)
                .clip(CircleShape)
                .background(accent.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ZynIcons.Check,
                contentDescription = stringResource(R.string.zyn_models_selected),
                modifier = Modifier.size(14.dp),
                tint = accent.onPrimary,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

@Composable
private fun ProviderEmptyState(onOpenProviders: () -> Unit) {
    MessageState(
        title = stringResource(R.string.zyn_models_provider_empty),
        message = stringResource(R.string.zyn_models_provider_empty_hint),
        actionLabel = stringResource(R.string.zyn_models_providers_action),
        onAction = onOpenProviders,
    )
}

@Composable
private fun NoResultsState(
    providerName: String?,
    query: String,
    onClearSearch: () -> Unit,
) {
    MessageState(
        title = stringResource(
            R.string.zyn_models_no_results,
            providerName ?: stringResource(R.string.zyn_models_no_provider),
            query,
        ),
        actionLabel = stringResource(R.string.zyn_models_search_clear),
        onAction = onClearSearch,
    )
}

@Composable
private fun EmptyState(onManageProviders: () -> Unit) {
    MessageState(
        title = stringResource(R.string.zyn_models_empty_title),
        message = stringResource(R.string.zyn_models_empty_message),
        actionLabel = stringResource(R.string.zyn_models_empty_action),
        onAction = onManageProviders,
    )
}

/** One shared layout for every "there is nothing to show, here is what to do" case. */
@Composable
private fun MessageState(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    message: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onAction,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.accent.primary,
            )
        }
    }
}
