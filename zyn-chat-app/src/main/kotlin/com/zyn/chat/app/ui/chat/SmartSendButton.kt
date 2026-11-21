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

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.ui.chat.speech.SpeechInputState
import com.zyn.chat.app.ui.icons.ZynIcons
import com.zyn.chat.app.ui.theme.accent

/** Diameter of the composer's trailing button. */
private val ButtonSize = 38.dp

/**
 * The composer's single trailing button, which unifies sending and dictation.
 *
 * What a tap does follows the current state, and never overlaps:
 * - nothing typed and idle: dictate (microphone icon)
 * - text typed and idle: send (arrow icon), long press dictates instead of sending
 * - dictating: stop listening (microphone, accent coloured, softly pulsing)
 * - waiting for a result: cancel (progress indicator)
 * - assistant answering: stop generating
 *
 * A long press can never turn into a send: it is flagged when recognised and the flag swallows the
 * click that follows it. Recognising speech only ever fills the text field — this button is the only
 * thing that sends.
 *
 * @param hasText Whether the message field has content to send.
 * @param isGenerating Whether the assistant is currently generating a response.
 * @param speechState Current dictation state.
 * @param onSend Invoked to send the typed message.
 * @param onStopGenerating Invoked to stop the assistant.
 * @param onStartSpeech Invoked to start dictation.
 * @param onStopSpeech Invoked to stop dictation without sending anything.
 * @param modifier Applied to the button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SmartSendButton(
    hasText: Boolean,
    isGenerating: Boolean,
    speechState: SpeechInputState,
    onSend: () -> Unit,
    onStopGenerating: () -> Unit,
    onStartSpeech: () -> Unit,
    onStopSpeech: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.accent
    val isListening = speechState == SpeechInputState.Listening
    val isProcessing = speechState == SpeechInputState.Processing

    // Guards against a click being delivered after a long press started dictation.
    var longPressHandled by remember { mutableStateOf(false) }

    // The guard only needs to live as long as the gesture that set it.
    LaunchedEffect(speechState) {
        if (speechState == SpeechInputState.Idle) longPressHandled = false
    }

    val backgroundColor = when {
        isGenerating -> MaterialTheme.colorScheme.surfaceVariant
        isListening || isProcessing -> accent.primary
        hasText -> accent.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isGenerating -> MaterialTheme.colorScheme.onSurface
        isListening || isProcessing || hasText -> accent.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val description = when {
        isGenerating -> stringResource(R.string.zyn_composer_stop)
        isListening -> stringResource(R.string.zyn_composer_stop_dictation)
        isProcessing -> stringResource(R.string.zyn_composer_cancel_dictation)
        hasText -> stringResource(R.string.zyn_composer_send)
        else -> stringResource(R.string.zyn_composer_dictate)
    }

    val onClick = {
        if (longPressHandled) {
            longPressHandled = false
        } else {
            when {
                isGenerating -> onStopGenerating()
                isListening || isProcessing -> onStopSpeech()
                hasText -> onSend()
                else -> onStartSpeech()
            }
        }
    }

    val onLongClick = {
        when {
            isGenerating -> Unit
            isListening || isProcessing -> onStopSpeech()
            else -> {
                longPressHandled = true
                onStartSpeech()
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isListening) {
            ListeningPulse(color = accent.primary)
        }

        Box(
            modifier = Modifier
                .size(ButtonSize)
                .clip(CircleShape)
                .background(backgroundColor)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isProcessing -> CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )

                else -> Icon(
                    imageVector = when {
                        isGenerating -> ZynIcons.Stop
                        hasText -> ZynIcons.ArrowUp
                        else -> ZynIcons.Mic
                    },
                    contentDescription = description,
                    modifier = Modifier.size(if (isGenerating) 15.dp else 18.dp),
                    tint = contentColor,
                )
            }
        }
    }
}

/** Soft ring that breathes while the microphone is live. */
@Composable
private fun ListeningPulse(color: Color) {
    val transition = rememberInfiniteTransition(label = "listening")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_DURATION_MILLIS),
            repeatMode = RepeatMode.Restart,
        ),
        label = "listeningAlpha",
    )

    Box(
        modifier = Modifier
            .size(ButtonSize + PULSE_RING_SPREAD)
            .alpha(alpha)
            .border(width = 1.5.dp, color = color, shape = CircleShape),
    )
}

private const val PULSE_DURATION_MILLIS = 1100

private val PULSE_RING_SPREAD = 8.dp
