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
import kotlin.math.pow
import kotlin.math.sqrt

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

/** WCAG's contrast offset, and the luminance of the light caption, used to find the switch point. */
private const val CONTRAST_OFFSET = 0.05f
private const val WHITE_LUMINANCE = 1f

/** Per-channel weights of WCAG 2.1 relative luminance. */
private const val LUMINANCE_RED = 0.2126f
private const val LUMINANCE_GREEN = 0.7152f
private const val LUMINANCE_BLUE = 0.0722f

/** WCAG's sRGB transfer curve: linear near black, a power curve above the knee. */
private const val LINEAR_CHANNEL_KNEE = 0.03928f
private const val LINEAR_CHANNEL_DIVISOR = 12.92f
private const val GAMMA_OFFSET = 0.055f
private const val GAMMA_SCALE = 1.055f
private const val GAMMA_EXPONENT = 2.4f

/**
 * The caption colour for an accent [seed]: the design system's [Ink] on a light accent, white on a
 * dark one.
 *
 * Derived rather than fixed, because a fixed white is wrong for a light accent — white on the brand
 * green is 2.9:1, below the 3:1 a non-text element such as the send button's icon needs, while Ink
 * reaches 6.4:1. Choosing by luminance always takes the more legible of the two.
 *
 * @param seed The accent the caption is drawn on.
 */
public fun onAccentColor(seed: Color): Color =
    if (relativeLuminanceOf(seed) > captionSwitchLuminance()) Ink else Color.White

/**
 * Luminance at which [Ink] and white contrast equally with the colour beneath them — the point where
 * the caption switches from one to the other. Derived from [Ink] rather than a fixed 0.179 (which
 * would hold for pure black): with this near-black the crossover sits at ~0.192, and the lower
 * figure would hand a mid-tone accent the less legible caption.
 */
private fun captionSwitchLuminance(): Float =
    sqrt((WHITE_LUMINANCE + CONTRAST_OFFSET) * (relativeLuminanceOf(Ink) + CONTRAST_OFFSET)) - CONTRAST_OFFSET

/** WCAG 2.1 relative luminance of an opaque colour. */
private fun relativeLuminanceOf(color: Color): Float =
    LUMINANCE_RED * linearChannel(color.red) +
        LUMINANCE_GREEN * linearChannel(color.green) +
        LUMINANCE_BLUE * linearChannel(color.blue)

/** Undoes the sRGB transfer curve for one channel. */
private fun linearChannel(channel: Float): Float =
    if (channel <= LINEAR_CHANNEL_KNEE) {
        channel / LINEAR_CHANNEL_DIVISOR
    } else {
        ((channel + GAMMA_OFFSET) / GAMMA_SCALE).pow(GAMMA_EXPONENT)
    }

/**
 * Builds an accent token set from a single seed colour. Tints are derived with alpha so any seed
 * keeps working on both light and dark surfaces.
 *
 * @param primary The accent seed.
 * @param onPrimary Content colour used on top of the accent. Defaults to [onAccentColor], so a
 * caller that has nothing but a seed still gets a legible caption.
 */
public fun accentColors(
    primary: Color,
    onPrimary: Color = onAccentColor(primary),
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
