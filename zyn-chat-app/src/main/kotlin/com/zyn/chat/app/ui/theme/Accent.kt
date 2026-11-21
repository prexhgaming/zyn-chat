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

package com.zyn.chat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Accent tokens: the single source of truth for selection, emphasis and primary actions
 * (selected drawer row, active provider tab, selected model card, send button, ...).
 *
 * The accent is deliberately **not** hardcoded to any brand colour. [MaterialTheme.accent] falls
 * back to the colour scheme's `primary`, so the application keeps its own identity today, and a
 * future settings screen can supply a user-chosen accent by providing [LocalAccentColors] without
 * touching any component.
 */
@Immutable
public data class AccentColors(
    /** Solid accent. Used for the send button, filled indicators and emphasised text. */
    val primary: Color,
    /** Content drawn on top of [primary]. */
    val onPrimary: Color,
    /** Low-opacity accent wash for selected surfaces. */
    val tint: Color,
    /** Medium-opacity accent used for selected borders. */
    val border: Color,
)

/** Opacity of [AccentColors.tint] when it is painted over a surface. */
private const val TINT_ALPHA = 0.10f

/** Opacity of [AccentColors.border] for selected outlines. */
private const val BORDER_ALPHA = 0.45f

/**
 * Builds an accent token set from a single seed colour. Tints are derived with alpha so any seed
 * keeps working on both light and dark surfaces.
 *
 * @param primary The accent seed.
 * @param onPrimary Content colour used on top of the accent.
 */
public fun accentColors(
    primary: Color,
    onPrimary: Color = Color.White,
): AccentColors = AccentColors(
    primary = primary,
    onPrimary = onPrimary,
    tint = primary.copy(alpha = TINT_ALPHA),
    border = primary.copy(alpha = BORDER_ALPHA),
)

/** Overrides the accent for a subtree. `null` means "follow the colour scheme's primary". */
public val LocalAccentColors: ProvidableCompositionLocal<AccentColors?> =
    staticCompositionLocalOf { null }

/**
 * The accent tokens in scope. Defaults to the active colour scheme's primary and on-primary, so the
 * accent always matches the application identity unless a settings screen overrides it.
 */
public val MaterialTheme.accent: AccentColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAccentColors.current
        ?: accentColors(colorScheme.primary, colorScheme.onPrimary)
