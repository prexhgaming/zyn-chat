/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-chat-android-ai/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zyn.chat.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand
private val Blue = Color(0xFF0A6CF5)
private val BlueBright = Color(0xFF4C93FF)
private val BlueDeep = Color(0xFF0546A6)
private val BlueSoft = Color(0xFFBBD6FF)

// Neutrals
private val White = Color(0xFFFFFFFF)
private val Ink = Color(0xFF0E1013)
private val Mist = Color(0xFFF2F3F5)
private val Slate = Color(0xFF666D7A)
private val SlateSoft = Color(0xFF9BA2AF)
private val Hairline = Color(0xFFE4E6EA)
private val HairlineSoft = Color(0xFFEEF0F3)

// Dark neutrals
private val NightBackground = Color(0xFF0F1013)
private val NightSurface = Color(0xFF141519)
private val NightElevated = Color(0xFF23252B)
private val NightOutline = Color(0xFF2C2F36)
private val NightOutlineSoft = Color(0xFF23262C)
private val NightText = Color(0xFFECEEF1)

// Feedback
private val Red = Color(0xFFDC2B2B)
private val RedSoft = Color(0xFFFF6B6B)
private val RedWash = Color(0xFFFFF1F1)
private val RedDeep = Color(0xFF3A0B0B)

internal val LightColorScheme =
    lightColorScheme(
        primary = Blue,
        onPrimary = White,
        primaryContainer = Blue.copy(alpha = 0.10f),
        onPrimaryContainer = BlueDeep,
        secondary = Blue,
        onSecondary = White,
        secondaryContainer = Blue.copy(alpha = 0.10f),
        onSecondaryContainer = BlueDeep,
        tertiary = Blue,
        onTertiary = White,
        tertiaryContainer = Blue.copy(alpha = 0.12f),
        onTertiaryContainer = BlueDeep,
        background = White,
        onBackground = Ink,
        surface = White,
        onSurface = Ink,
        surfaceVariant = Mist,
        onSurfaceVariant = Slate,
        outline = Hairline,
        outlineVariant = HairlineSoft,
        error = Red,
        onError = White,
        errorContainer = RedWash,
        onErrorContainer = Red,
    )

internal val DarkColorScheme =
    darkColorScheme(
        primary = BlueBright,
        onPrimary = White,
        primaryContainer = BlueBright.copy(alpha = 0.16f),
        onPrimaryContainer = BlueBright,
        secondary = BlueBright,
        onSecondary = White,
        secondaryContainer = BlueBright.copy(alpha = 0.18f),
        onSecondaryContainer = BlueSoft,
        tertiary = BlueBright,
        onTertiary = Ink,
        tertiaryContainer = BlueBright.copy(alpha = 0.20f),
        onTertiaryContainer = BlueSoft,
        background = NightBackground,
        onBackground = NightText,
        surface = NightSurface,
        onSurface = NightText,
        surfaceVariant = NightElevated,
        onSurfaceVariant = SlateSoft,
        outline = NightOutline,
        outlineVariant = NightOutlineSoft,
        error = RedSoft,
        onError = Ink,
        errorContainer = RedDeep,
        onErrorContainer = RedSoft,
    )
