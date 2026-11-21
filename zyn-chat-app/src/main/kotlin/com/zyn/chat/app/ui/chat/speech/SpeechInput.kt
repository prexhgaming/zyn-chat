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

package com.zyn.chat.app.ui.chat.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** The distinct states dictation moves through. */
public sealed interface SpeechInputState {
    /** Not dictating. */
    public data object Idle : SpeechInputState

    /** Actively listening; audio is being captured. */
    public data object Listening : SpeechInputState

    /** Listening stopped; waiting for the recognizer to deliver a result. */
    public data object Processing : SpeechInputState

    /** Dictation failed. [reason] explains why so the UI can react. */
    public data class Error(public val reason: SpeechErrorReason) : SpeechInputState

    /** No speech recognition service is available on this device. */
    public data object Unavailable : SpeechInputState
}

/** Why dictation failed. */
public enum class SpeechErrorReason {
    /** The app is not allowed to record audio. */
    PermissionDenied,

    /** The recognizer heard nothing usable. */
    NoMatch,

    /** Another recognition session is already running. */
    RecognizerBusy,

    /** Recognition needs a network connection that is missing or too slow. */
    Network,

    /** The microphone could not be read. */
    Audio,

    /** Speech recognition is not available on this device. */
    Unavailable,

    /** Anything else the platform reported. */
    Unknown,
}

/**
 * Drives the platform speech recognizer behind a small state machine.
 *
 * Recognised speech is handed back through the callback passed to [start]; this class never sends
 * a message itself, so stopping dictation can never trigger a send. The partially recognised text
 * replaces the dictated segment on every partial result, which keeps whatever the person had typed
 * before dictation started.
 *
 * [SpeechInputState.Listening] means audio is being captured, [SpeechInputState.Processing] means
 * the result is still on its way. [stop] moves between the two, and never sends anything.
 *
 * Call [release] when the host leaves the composition to destroy the recognizer and drop the
 * listener. [rememberSpeechInput] does that automatically.
 */
public class SpeechInput(private val context: Context) {

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }

    private val listener = RecognitionListenerAdapter(
        onReady = { updateState(SpeechInputState.Listening) },
        onEndOfSpeech = { updateState(SpeechInputState.Processing) },
        onPartial = { recognized -> publish(recognized) },
        onFinal = { recognized ->
            if (recognized.isBlank()) {
                fail(SpeechErrorReason.NoMatch)
            } else {
                publish(recognized)
                updateState(SpeechInputState.Idle)
            }
            clearSession()
        },
        onFailure = ::fail,
    )

    /** The current dictation state. */
    public var state: SpeechInputState by mutableStateOf(
        if (recognizer == null) SpeechInputState.Unavailable else SpeechInputState.Idle,
    )
        private set

    private var dictatedBase: String = ""
    private var onText: ((String) -> Unit)? = null
    private var onFailure: ((SpeechErrorReason) -> Unit)? = null

    /** Whether audio is currently being captured. */
    public val isListening: Boolean get() = state == SpeechInputState.Listening

    /** Whether the button should offer a stop action instead of starting a new session. */
    public val isActive: Boolean
        get() = state == SpeechInputState.Listening || state == SpeechInputState.Processing

    /**
     * Starts dictation.
     *
     * @param baseText Text already in the field, kept and prefixed to whatever is recognised.
     * @param onText Receives the field's new text on every partial and final result.
     * @param onError Receives the reason when dictation fails.
     */
    public fun start(
        baseText: String,
        onText: (String) -> Unit,
        onError: (SpeechErrorReason) -> Unit,
    ) {
        val recognizer = recognizer
        if (recognizer == null) {
            state = SpeechInputState.Unavailable
            onError(SpeechErrorReason.Unavailable)
            return
        }
        if (isActive) return

        dictatedBase = baseText
        this.onText = onText
        onFailure = onError
        state = SpeechInputState.Listening
        recognizer.setRecognitionListener(listener)
        runCatching { recognizer.startListening(recognitionIntent()) }
            .onFailure { fail(SpeechErrorReason.Unknown) }
    }

    /**
     * Stops capturing audio and keeps waiting for the result.
     *
     * When already waiting for a result, the session is dropped instead.
     */
    public fun stop() {
        when (state) {
            SpeechInputState.Listening -> {
                state = SpeechInputState.Processing
                runCatching { recognizer?.stopListening() }
            }

            SpeechInputState.Processing -> cancel()

            else -> Unit
        }
    }

    /** Abandons the current session. Nothing recognised so far is applied or sent. */
    public fun cancel() {
        runCatching { recognizer?.cancel() }
        clearSession()
        updateState(SpeechInputState.Idle)
    }

    /** Destroys the recognizer and drops the listener. Call when the host is disposed. */
    public fun release() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        clearSession()
        updateState(SpeechInputState.Idle)
    }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private fun publish(recognized: String) {
        val text = mergeDictation(dictatedBase, recognized)
        if (text.isNotBlank()) onText?.invoke(text)
    }

    private fun fail(reason: SpeechErrorReason) {
        updateState(
            if (reason == SpeechErrorReason.Unavailable) {
                SpeechInputState.Unavailable
            } else {
                SpeechInputState.Error(reason)
            },
        )
        onFailure?.invoke(reason)
        clearSession()
    }

    private fun updateState(newState: SpeechInputState) {
        state = newState
    }

    private fun clearSession() {
        onText = null
        onFailure = null
        dictatedBase = ""
        runCatching { recognizer?.setRecognitionListener(null) }
    }
}

/** Joins the text that was already in the field with what was just recognised. */
private fun mergeDictation(base: String, recognized: String): String = when {
    base.isBlank() -> recognized.trim()
    recognized.isBlank() -> base
    else -> "${base.trimEnd()} ${recognized.trim()}"
}

/** Maps the recognizer's callbacks onto the small state machine in [SpeechInput]. */
private class RecognitionListenerAdapter(
    private val onReady: () -> Unit,
    private val onEndOfSpeech: () -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onFailure: (SpeechErrorReason) -> Unit,
) : RecognitionListener {

    override fun onReadyForSpeech(params: Bundle?) = onReady()

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = onEndOfSpeech()

    override fun onError(error: Int) = onFailure(error.toReason())

    override fun onResults(results: Bundle?) = onFinal(results.firstResult())

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults.firstResult()
        if (text.isNotBlank()) onPartial(text)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun Bundle?.firstResult(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()

    private fun Int.toReason(): SpeechErrorReason = when (this) {
        SpeechRecognizer.ERROR_AUDIO -> SpeechErrorReason.Audio
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechErrorReason.PermissionDenied
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        -> SpeechErrorReason.Network

        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> SpeechErrorReason.NoMatch

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechErrorReason.RecognizerBusy
        SpeechRecognizer.ERROR_CLIENT -> SpeechErrorReason.Unknown
        else -> SpeechErrorReason.Unknown
    }
}

/** Remembers a [SpeechInput] and releases it when the host leaves the composition. */
@Composable
public fun rememberSpeechInput(): SpeechInput {
    val context = LocalContext.current
    val speechInput = remember(context) { SpeechInput(context) }

    DisposableEffect(speechInput) {
        onDispose { speechInput.release() }
    }

    return speechInput
}
