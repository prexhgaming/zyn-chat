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

package com.zyn.chat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.ui.theme.accent

/** Default edge of a brand mark. */
public val BrandMarkSize: Dp = 34.dp

/**
 * Square badge that identifies a provider or a model.
 *
 * Draws the brand's glyph when one is known ([mark]) and falls back to [label]'s initials, so the
 * widget is never empty and new models work the moment they appear — before anyone has drawn a mark
 * for them. Colours come from the accent tokens, so a change of accent in settings propagates here
 * without touching this file.
 *
 * @param label Name the initials are taken from, and the mark's content description.
 * @param modifier Applied to the badge.
 * @param mark Brand glyph, or `null` to show initials.
 * @param size Edge of the badge.
 * @param tint Colour of the glyph; defaults to the accent.
 */
@Composable
public fun BrandMark(
    label: String,
    modifier: Modifier = Modifier,
    mark: ImageVector? = null,
    size: Dp = BrandMarkSize,
    tint: Color = MaterialTheme.colorScheme.accent.primary,
) {
    val shape = RoundedCornerShape(size / THREE)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.accent.tint),
        contentAlignment = Alignment.Center,
    ) {
        if (mark != null) {
            Icon(
                imageVector = mark,
                contentDescription = label,
                modifier = Modifier.size(size / TWO),
                tint = tint,
            )
        } else {
            Text(
                text = initialsOf(label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tint,
            )
        }
    }
}

/**
 * One or two initials for a name, e.g. "OpenRouter" becomes "OR" and "Groq" becomes "GQ".
 *
 * @param name Name to abbreviate.
 */
public fun initialsOf(name: String): String =
    name.split(*INITIAL_SEPARATORS)
        .filter(String::isNotBlank)
        .take(MAX_INITIALS)
        .map { part -> part.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { name.take(MAX_INITIALS).uppercase() }

private val INITIAL_SEPARATORS = charArrayOf(' ', '-', '_', '.', '/')
private const val MAX_INITIALS = 2
private const val TWO = 2
private const val THREE = 3
