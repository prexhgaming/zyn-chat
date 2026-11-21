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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Applies the Zyn Chat design system.
 *
 * @param darkTheme Whether to use the dark colour scheme. Defaults to the system setting.
 * @param accent Accent tokens for selection and primary actions. `null` (the default) derives the
 * accent from the active colour scheme's primary, which is the application's own identity. A future
 * settings screen swaps the accent by passing its own [AccentColors] here — no component needs to
 * change.
 * @param content The content to theme.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentColors? = null,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val resolvedAccent = accent ?: accentColors(colorScheme.primary, colorScheme.onPrimary)

    CompositionLocalProvider(LocalAccentColors provides resolvedAccent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ZynShapes,
            content = content,
        )
    }
}
