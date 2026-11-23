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

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.zyn.chat.app.R
import com.zyn.chat.app.ui.icons.ZynIcons
import com.zyn.chat.app.ui.theme.AttachmentMenuShape
import com.zyn.chat.app.ui.theme.accent

/**
 * Round button that opens the attachment menu.
 *
 * The menu is a [Popup], so it lives in its own window: it opens **above** the button, over the
 * conversation, and never grows the composer or pushes the chat around. Tapping the button again,
 * tapping anywhere outside it, or pressing back closes it without picking anything.
 *
 * The glyph morphs from a plus into a cross while the menu is open, which is what tells the same
 * button closes it.
 *
 * @param enabled Whether the button reacts to taps.
 * @param onCameraClick Invoked when "Camera" is chosen.
 * @param onPhotosClick Invoked when "Photos" is chosen.
 * @param onFilesClick Invoked when "Files" is chosen.
 * @param modifier Applied to the button.
 */
@Composable
internal fun AttachmentButton(
    enabled: Boolean,
    onCameraClick: () -> Unit,
    onPhotosClick: () -> Unit,
    onFilesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AttachmentButtonFace(
            expanded = expanded,
            enabled = enabled,
            onClick = { expanded = !expanded },
        )

        if (expanded) {
            AttachmentMenu(
                onCameraClick = {
                    expanded = false
                    onCameraClick()
                },
                onPhotosClick = {
                    expanded = false
                    onPhotosClick()
                },
                onFilesClick = {
                    expanded = false
                    onFilesClick()
                },
                onDismiss = { expanded = false },
            )
        }
    }
}

/** The menu's anchor: a filled accent circle carrying a plus that turns into a cross when open. */
@Composable
private fun AttachmentButtonFace(
    expanded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) OPEN_ROTATION else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "attachmentButtonRotation",
    )

    Box(
        modifier = Modifier
            .size(BUTTON_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.accent.primary)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ZynIcons.Plus,
            contentDescription = stringResource(R.string.zyn_composer_attach),
            modifier = Modifier
                .size(BUTTON_GLYPH)
                .graphicsLayer { rotationZ = rotation },
            tint = MaterialTheme.accent.onPrimary,
        )
    }
}

/**
 * The popup window and its placement.
 *
 * @param onCameraClick Invoked when "Camera" is chosen.
 * @param onPhotosClick Invoked when "Photos" is chosen.
 * @param onFilesClick Invoked when "Files" is chosen.
 * @param onDismiss Invoked when the person taps outside the menu or presses back.
 */
@Composable
private fun AttachmentMenu(
    onCameraClick: () -> Unit,
    onPhotosClick: () -> Unit,
    onFilesClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val margin = with(density) { WINDOW_MARGIN.roundToPx() }
    val gap = with(density) { ANCHOR_GAP.roundToPx() }
    val shadowRoom = with(density) { SHADOW_ROOM.roundToPx() }
    val positionProvider = remember(margin, gap, shadowRoom) {
        AboveAnchorPositionProvider(margin = margin, gap = gap, shadowRoom = shadowRoom)
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AttachmentMenuCard(
            onCameraClick = onCameraClick,
            onPhotosClick = onPhotosClick,
            onFilesClick = onFilesClick,
        )
    }
}

/**
 * The card itself: one row per action, each an icon in a circle next to its label.
 *
 * It rises and fades in on the first frame it is composed, so it reads as coming out of the button
 * rather than appearing on top of the chat.
 */
@Composable
private fun AttachmentMenuCard(
    onCameraClick: () -> Unit,
    onPhotosClick: () -> Unit,
    onFilesClick: () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val fade by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = FADE_MILLIS),
        label = "attachmentMenuFade",
    )
    val rise by animateFloatAsState(
        targetValue = if (appeared) 0f else 1f,
        animationSpec = tween(durationMillis = RISE_MILLIS, easing = LinearOutSlowInEasing),
        label = "attachmentMenuRise",
    )
    val riseDistance = with(LocalDensity.current) { RISE.roundToPx().toFloat() }

    // The padding is the room the card's shadow is drawn in: it belongs to the popup's own window,
    // which is cut at its edges, so without it the shadow would be clipped away.
    Box(modifier = Modifier.padding(SHADOW_ROOM)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(WIDTH_FRACTION)
                .widthIn(max = MAX_WIDTH)
                .graphicsLayer {
                    alpha = fade
                    translationY = rise * riseDistance
                },
            shape = AttachmentMenuShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = SHADOW_ROOM,
        ) {
            Column(
                modifier = Modifier.padding(vertical = CARD_VERTICAL_PADDING),
                verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
            ) {
                AttachmentMenuRow(
                    icon = ZynIcons.Camera,
                    label = stringResource(R.string.zyn_composer_attach_camera),
                    onClick = onCameraClick,
                )

                AttachmentMenuRow(
                    icon = ZynIcons.Image,
                    label = stringResource(R.string.zyn_composer_attach_photos),
                    onClick = onPhotosClick,
                )

                AttachmentMenuRow(
                    icon = ZynIcons.Clip,
                    label = stringResource(R.string.zyn_composer_attach_files),
                    onClick = onFilesClick,
                )
            }
        }
    }
}

/**
 * One action in the menu: a glyph in a filled circle, with its label beside it.
 *
 * @param icon Glyph to draw. Described by [label], which is the row's own text, so the icon carries
 *   no description of its own.
 * @param label Text of the action, and what the row announces when focused.
 * @param onClick Invoked when the row is tapped.
 */
@Composable
private fun AttachmentMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_INSET)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(
                start = ROW_CONTENT_PADDING,
                end = ROW_CONTENT_PADDING,
                top = ROW_VERTICAL_PADDING,
                bottom = ROW_VERTICAL_PADDING,
            ),
        horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(ICON_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(GLYPH_SIZE),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Places the menu just above its anchor, aligned with the anchor's leading edge.
 *
 * Opening downwards is not an option here: the composer sits at the bottom of the screen, so a menu
 * below the button would be clipped away. It opens upwards instead, and stays inside the window
 * margins when the anchor is at either edge.
 *
 * @param margin Smallest distance kept between the menu and the window's edges, in pixels.
 * @param gap Distance between the menu and the top of the anchor, in pixels.
 * @param shadowRoom Padding the card leaves around itself for its shadow, in pixels.
 */
private class AboveAnchorPositionProvider(
    private val margin: Int,
    private val gap: Int,
    private val shadowRoom: Int,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // The card, not the popup content, is what has to clear the button: the content carries the
        // shadow padding, which is taken off and added back here so only the card moves.
        val cardWidth = (popupContentSize.width - 2 * shadowRoom).coerceAtLeast(0)
        val furthestRight = (windowSize.width - cardWidth - margin).coerceAtLeast(margin)
        val cardLeft = anchorBounds.left.coerceIn(margin, furthestRight)
        val above = anchorBounds.top - popupContentSize.height + shadowRoom - gap

        return IntOffset(cardLeft - shadowRoom, above.coerceAtLeast(margin))
    }
}

private val BUTTON_SIZE = 48.dp
private val BUTTON_GLYPH = 24.dp

/** Rotation that swings the plus into a cross. */
private const val OPEN_ROTATION = 45f

private val MAX_WIDTH = 300.dp

/** Share of the popup window's width the card aims for, before [MAX_WIDTH] caps it. */
private const val WIDTH_FRACTION = 0.9f

private val CARD_VERTICAL_PADDING = 10.dp
private val SHADOW_ROOM = 12.dp
private val WINDOW_MARGIN = 12.dp
private val ANCHOR_GAP = 10.dp
private val RISE = 16.dp
private const val FADE_MILLIS = 140
private const val RISE_MILLIS = 200

private val ROW_SPACING = 2.dp
private val ROW_INSET = 6.dp
private val ROW_CONTENT_PADDING = 8.dp
private val ROW_VERTICAL_PADDING = 6.dp
private val ICON_SIZE = 44.dp
private val GLYPH_SIZE = 22.dp
private val ICON_GAP = 16.dp
