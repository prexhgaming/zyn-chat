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

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the accent: the brand seed, the caption picked for it, and the contrast guarantee.
 *
 * The WCAG maths below is written out again instead of calling the production helper, so a mistake in
 * the helper cannot quietly make its own test agree with it.
 */
class AccentTest {

    @Test
    fun `the accent seed is the brand green in both schemes`() {
        assertEquals(Color(0xFF60A832), LightColorScheme.primary)
        assertEquals(Color(0xFF60A832), DarkColorScheme.primary)
    }

    @Test
    fun `the caption on the shipped accent reaches aa`() {
        shippedAccents().forEach { (name, accent) ->
            val ratio = contrast(accent.primary, accent.onPrimary)
            assertTrue("the $name accent caption is $ratio:1, below $AA_TEXT", ratio >= AA_TEXT)
        }
    }

    @Test
    fun `a seed on its own still gets a legible caption`() {
        shippedAccents().forEach { (name, accent) ->
            val derived = accentColors(accent.primary)
            val ratio = contrast(derived.primary, derived.onPrimary)
            assertTrue("the derived $name caption is $ratio:1, below $AA_TEXT", ratio >= AA_TEXT)
        }
    }

    @Test
    fun `the green accent takes the dark caption rather than white`() {
        assertEquals(Ink, onAccentColor(Color(0xFF60A832)))
        assertEquals(Color.White, onAccentColor(Color(0xFF0B1B3A)))
    }

    @Test
    fun `a caption is never the less legible of the two`() {
        SEEDS.forEach { seed ->
            val white = contrast(seed, Color.White)
            val ink = contrast(seed, Ink)
            assertEquals(
                "caption for ${seed.value.toString(16)}",
                if (white >= ink) Color.White else Ink,
                onAccentColor(seed),
            )
        }
    }

    @Test
    fun `a seed derives tint and border at the token opacities`() {
        val accent = accentColors(Color(0xFF60A832))
        assertEquals(Color(0xFF60A832), accent.primary)
        assertEquals(TINT_ALPHA, accent.tint.alpha, TOLERANCE)
        assertEquals(BORDER_ALPHA, accent.border.alpha, TOLERANCE)
        assertEquals(accent.primary.copy(alpha = TINT_ALPHA), accent.tint)
        assertEquals(accent.primary.copy(alpha = BORDER_ALPHA), accent.border)
    }

    /** The pair [AppTheme] hands to [accentColors], i.e. what the send button actually draws. */
    private fun shippedAccents(): List<Pair<String, AccentColors>> =
        listOf("light" to LightColorScheme, "dark" to DarkColorScheme)
            .map { (name, scheme) -> name to accentColors(scheme.primary, scheme.onPrimary) }

    private fun contrast(first: Color, second: Color): Float {
        val light = maxOf(luminance(first), luminance(second))
        val dark = minOf(luminance(first), luminance(second))
        return (light + OFFSET) / (dark + OFFSET)
    }

    private fun luminance(color: Color): Float =
        RED_WEIGHT * linear(color.red) + GREEN_WEIGHT * linear(color.green) + BLUE_WEIGHT * linear(color.blue)

    private fun linear(channel: Float): Float =
        if (channel <= KNEE) channel / LINEAR_DIVISOR else ((channel + GAMMA_OFFSET) / GAMMA_SCALE).pow(GAMMA_EXPONENT)

    private companion object {
        /** WCAG AA for normal text, the stricter of the two minimums that apply to an accent. */
        const val AA_TEXT = 4.5f
        const val TOLERANCE = 0.001f
        const val OFFSET = 0.05f
        const val TINT_ALPHA = 0.10f
        const val BORDER_ALPHA = 0.45f
        const val RED_WEIGHT = 0.2126f
        const val GREEN_WEIGHT = 0.7152f
        const val BLUE_WEIGHT = 0.0722f
        const val KNEE = 0.03928f
        const val LINEAR_DIVISOR = 12.92f
        const val GAMMA_OFFSET = 0.055f
        const val GAMMA_SCALE = 1.055f
        const val GAMMA_EXPONENT = 2.4f

        /**
         * The brand green; the blue this replaced, which was chosen against white; both extremes; and
         * the mid grey where neither caption can reach 4.5:1, so only "the better of the two" holds.
         */
        val SEEDS = listOf(
            Color(0xFF60A832),
            Color(0xFF0A6CF5),
            Color(0xFF0F1013),
            Color(0xFFFFFFFF),
            Color(0xFFFFEB3B),
            Color(0xFF777777),
        )
    }
}
