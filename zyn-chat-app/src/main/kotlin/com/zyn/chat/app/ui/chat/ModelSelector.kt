package com.zyn.chat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.ui.icons.ZynIcons

/**
 * Compact model picker shown inside the composer.
 *
 * Renders as a pill with the model glyph, the selected model name and a chevron; tapping it opens
 * the list of available models. The pill is presentation only: [onModelSelected] reports the choice
 * to the caller, which owns the selection state.
 *
 * @param selectedModel The model currently selected.
 * @param models Models offered in the dropdown.
 * @param onModelSelected Invoked with the model the person picked.
 * @param modifier Applied to the pill.
 * @param enabled Whether the pill can be tapped.
 */
@Composable
fun ModelSelector(
    selectedModel: ChatModel,
    models: List<ChatModel>,
    onModelSelected: (ChatModel) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val pillLabel = stringResource(R.string.zyn_composer_model)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics { contentDescription = pillLabel },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ZynIcons.ModelBlocks,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )

            Text(
                text = selectedModel.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )

            Icon(
                imageVector = ZynIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            models.forEach { model ->
                ModelMenuItem(
                    model = model,
                    selected = model.id == selectedModel.id,
                    onClick = {
                        expanded = false
                        onModelSelected(model)
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelMenuItem(
    model: ChatModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val glyph: ImageVector = ZynIcons.ModelBlocks
    val tint =
        if (selected) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    DropdownMenuItem(
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = glyph,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tint,
            )
        },
        text = {
            Column {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = model.provider,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}
