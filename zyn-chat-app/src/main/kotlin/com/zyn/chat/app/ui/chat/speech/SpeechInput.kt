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
import android.os.Handler
import android.os.Looper
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

    /** Audio is being captured, including the short gap before the next attempt starts. */
    public data object Listening : SpeechInputState

    /** Listening stopped; the recognizer still has to deliver a result. */
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
 * Drives the platform speech recognizer behind a small state machine, **continuously**.
 *
 * The platform recognizer ends its session at the first end of speech, so a dictation session here
 * is a chain of recognition attempts: every completed utterance is appended to the accumulated text
 * and the recognizer is started again. That is what keeps dictation running until the person stops
 * it — [stop] ends the chain after the pending result lands, [cancel] drops it immediately.
 *
 * Recognised speech is handed back through the callback passed to [start]; this class never sends a
 * message itself, so stopping dictation can never trigger a send. Text that was already in the field
 * when dictation started is kept, and each completed utterance is appended to it once.
 *
 * An attempt that ends with silence is retried silently, and after
 * [MAX_SILENT_ATTEMPTS][MAX_SILENT_ATTEMPTS] consecutive silent attempts the session gives up and
 * reports the last reason rather than looping forever. Errors that cannot be retried (denied
 * permission, unreadable microphone, no recognizer) end the session right away.
 *
 * Call [release] when the host leaves the composition to destroy the recognizer and drop the
 * listener. [rememberSpeechInput] does that automatically.
 */
public class SpeechInput(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }

    /** The current dictation state. */
    public var state: SpeechInputState by mutableStateOf(
        if (recognizer == null) SpeechInputState.Unavailable else SpeechInputState.Idle,
    )
        private set

    /** Text that is final: what was in the field before dictation, plus every completed utterance. */
    private var finalizedText: String = ""

    /** The utterance being recognised; it replaces itself on every partial result. */
    private var partialText: String = ""

    private var onText: ((String) -> Unit)? = null
    private var onFailure: ((SpeechErrorReason) -> Unit)? = null

    /** Whether the chain of attempts runs, i.e. dictation continues until the person stops it. */
    private var sessionActive: Boolean = false

    private var released: Boolean = false

    /** Consecutive attempts that produced no speech, used to give up instead of looping forever. */
    private var silentAttempts: Int = 0

    private val listener = RecognitionListenerAdapter(
        onReady = { state = SpeechInputState.Listening },
        onSpeechEnd = { state = SpeechInputState.Processing },
        onPartial = { recognized -> onResult(recognized, isFinal = false) },
        onFinal = { recognized -> onResult(recognized, isFinal = true) },
        onFailure = ::onRecognitionError,
    )

    /** Starts the next attempt after one has finished. */
    private val restartRecognition = Runnable { startRecognition() }

    /** Settles the session in case a stopped recognizer never delivers its result. */
    private val settleWatchdog = Runnable { if (!sessionActive) settle() }

    /** Pushes the accumulated text to the host, unless there is nothing to show. */
    private val notifyText: () -> Unit = {
        val text = mergeDictation(finalizedText, partialText)
        if (text.isNotBlank()) onText?.invoke(text)
    }

    /** Whether audio is currently being captured. */
    public val isListening: Boolean get() = state == SpeechInputState.Listening

    /** Whether dictation is running, so the button offers stop instead of a new session. */
    public val isActive: Boolean get() = sessionActive

    /**
     * Starts dictation and keeps it running until [stop] or [cancel].
     *
     * @param baseText Text already in the field, kept and prefixed to whatever is recognised.
     * @param onText Receives the field's new text after every partial and final result.
     * @param onError Receives the reason when dictation fails.
     */
    public fun start(
        baseText: String,
        onText: (String) -> Unit,
        onError: (SpeechErrorReason) -> Unit,
    ) {
        if (recognizer == null) {
            state = SpeechInputState.Unavailable
            onError(SpeechErrorReason.Unavailable)
            return
        }
        if (sessionActive || released) return

        this.onText = onText
        onFailure = onError
        sessionActive = true
        finalizedText = baseText
        partialText = ""
        silentAttempts = 0
        startRecognition()
    }

    /**
     * Ends dictation, keeping everything recognised so far.
     *
     * A result that is still on its way is applied first. When dictation is already winding down,
     * the session is dropped instead.
     */
    public fun stop() {
        if (!sessionActive) {
            if (state == SpeechInputState.Processing) cancel()
            return
        }

        sessionActive = false
        mainHandler.removeCallbacks(restartRecognition)

        if (state == SpeechInputState.Listening) {
            state = SpeechInputState.Processing
            runCatching { recognizer?.stopListening() }
            // The pending result arrives through onResult, which then settles the session.
            mainHandler.postDelayed(settleWatchdog, SETTLE_TIMEOUT_MILLIS)
        } else {
            // Keep the utterance that is already on screen instead of dropping it.
            if (partialText.isNotBlank()) {
                finalizedText = mergeDictation(finalizedText, partialText)
                partialText = ""
                notifyText()
            }
            runCatching { recognizer?.cancel() }
            settle()
        }
    }

    /** Drops the session immediately: nothing in flight is applied, nothing is sent. */
    public fun cancel() {
        sessionActive = false
        mainHandler.removeCallbacks(restartRecognition)
        runCatching { recognizer?.cancel() }
        settle()
    }

    /** Destroys the recognizer and drops the listener. Call when the host is disposed. */
    public fun release() {
        released = true
        cancel()
        runCatching { recognizer?.destroy() }
    }

    /**
     * Starts the platform recognizer, optionally after a short delay.
     *
     * @param delayMillis How long to wait first; a small gap keeps the recognizer from rejecting the
     *   restart as busy.
     */
    private fun startRecognition(delayMillis: Long = 0L) {
        val recognizer = recognizer ?: return
        if (!sessionActive || released) return

        mainHandler.removeCallbacks(restartRecognition)
        state = SpeechInputState.Listening

        if (delayMillis > 0L) {
            mainHandler.postDelayed(restartRecognition, delayMillis)
            return
        }

        recognizer.setRecognitionListener(listener)
        runCatching { recognizer.startListening(recognitionIntent()) }
            .onFailure { fail(SpeechErrorReason.Unknown) }
    }

    /**
     * Handles a result of the current attempt.
     *
     * @param recognized Text the recognizer produced.
     * @param isFinal Whether the attempt has finished. Partials replace themselves inside the
     *   current utterance; a final result is appended to the accumulated text and triggers the next
     *   attempt, which is what makes dictation continuous.
     */
    private fun onResult(recognized: String, isFinal: Boolean) {
        if (!sessionActive) {
            // The person already stopped: keep this last utterance, then settle.
            if (recognized.isNotBlank()) {
                finalizedText = mergeDictation(finalizedText, recognized)
                partialText = ""
                notifyText()
            }
            if (isFinal) settle()
            return
        }

        if (!isFinal) {
            partialText = recognized
            silentAttempts = 0
            notifyText()
            return
        }

        partialText = ""
        if (recognized.isNotBlank()) {
            finalizedText = mergeDictation(finalizedText, recognized)
            silentAttempts = 0
        } else {
            silentAttempts++
        }
        notifyText()

        if (silentAttempts > MAX_SILENT_ATTEMPTS) {
            fail(SpeechErrorReason.NoMatch)
        } else {
            startRecognition(delayMillis = RESTART_DELAY_MILLIS)
        }
    }

    /**
     * Keeps the session alive through recoverable failures, so a pause or a hiccup does not end
     * dictation. Errors that cannot be retried end the session with a reportable reason.
     *
     * @param reason What the platform reported.
     */
    private fun onRecognitionError(reason: SpeechErrorReason) {
        if (!sessionActive) {
            settle()
            return
        }
        if (reason.isFatal()) {
            fail(reason)
            return
        }

        // Silence, a busy recognizer or a connection hiccup: retry and stay in dictation.
        partialText = ""
        notifyText()
        silentAttempts++

        if (silentAttempts > MAX_SILENT_ATTEMPTS) {
            fail(reason)
        } else {
            startRecognition(delayMillis = RESTART_DELAY_MILLIS)
        }
    }

    /** Ends the session quietly: callbacks dropped, nothing in flight applied. */
    private fun settle() {
        mainHandler.removeCallbacks(restartRecognition)
        mainHandler.removeCallbacks(settleWatchdog)
        onText = null
        onFailure = null
        finalizedText = ""
        partialText = ""
        silentAttempts = 0
        runCatching { recognizer?.setRecognitionListener(null) }
        state = SpeechInputState.Idle
    }

    /**
     * Ends the session with an error the host can report.
     *
     * @param reason What to report.
     */
    private fun fail(reason: SpeechErrorReason) {
        sessionActive = false
        val notify = onFailure
        settle()
        state = if (reason == SpeechErrorReason.Unavailable) {
            SpeechInputState.Unavailable
        } else {
            SpeechInputState.Error(reason)
        }
        notify?.invoke(reason)
    }
}

/** The recognition request used for every attempt in a dictation session. */
private fun recognitionIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

/** Joins the accumulated text with what was just recognised. */
private fun mergeDictation(base: String, recognized: String): String = when {
    base.isBlank() -> recognized.trim()
    recognized.isBlank() -> base
    else -> "${base.trimEnd()} ${recognized.trim()}"
}

/** Errors worth reporting: retrying them would only fail the same way. */
private fun SpeechErrorReason.isFatal(): Boolean = when (this) {
    SpeechErrorReason.PermissionDenied,
    SpeechErrorReason.Audio,
    SpeechErrorReason.Unavailable,
    -> true

    SpeechErrorReason.NoMatch,
    SpeechErrorReason.RecognizerBusy,
    SpeechErrorReason.Network,
    SpeechErrorReason.Unknown,
    -> false
}

/** Maps the recognizer's callbacks onto the small state machine in [SpeechInput]. */
private class RecognitionListenerAdapter(
    private val onReady: () -> Unit,
    private val onSpeechEnd: () -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onFailure: (SpeechErrorReason) -> Unit,
) : RecognitionListener {

    override fun onReadyForSpeech(params: Bundle?) = onReady()

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = onSpeechEnd()

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

/** Gap before the next attempt, long enough for the recognizer to accept a new session. */
private const val RESTART_DELAY_MILLIS = 300L

/** Consecutive attempts without speech that dictation tolerates before giving up. */
private const val MAX_SILENT_ATTEMPTS = 6

/** How long to wait for a result after the person stops dictation. */
private const val SETTLE_TIMEOUT_MILLIS = 1_500L
