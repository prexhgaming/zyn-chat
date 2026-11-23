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

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zyn.chat.app.R
import com.zyn.chat.app.ui.chat.speech.SpeechErrorReason
import com.zyn.chat.app.ui.chat.speech.rememberSpeechInput
import com.zyn.chat.app.ui.theme.ComposerShape
import com.zyn.chat.compose.ui.component.MessageData
import kotlinx.coroutines.launch

/**
 * Message composer.
 *
 * A rounded capsule that holds the message field on top and a control row below: the attachment
 * picker, the model pill, and the single trailing button that sends, dictates or stops.
 *
 * Photos, pictures taken with the camera and documents are staged as attachments on [MessageData],
 * exactly like the Stream-derived `ChatComposer` in the `com.zyn.chat.compose` package tree, so they
 * flow through the existing send path untouched. The attachment menu is a popup that opens above the
 * composer and leaves it exactly where it was — see [AttachmentButton].
 *
 * Dictation is driven by [rememberSpeechInput] and runs continuously: every completed utterance is
 * appended to the field and listening restarts, until the person stops it with the same button or
 * starts typing. Typing wins — it ends dictation immediately — and a dictation never sends a
 * message, so the text always stays editable and reviewable before it goes out.
 *
 * @param onSendClick Invoked with the composed message when the person sends it.
 * @param onStopClick Invoked while the assistant is answering, to stop it.
 * @param isGenerating Whether the assistant is currently generating a response.
 * @param modelName Name of the active model, or `null` when none is selected.
 * @param onModelClick Invoked when the model pill is tapped, to open the model picker.
 * @param draftText Text pushed into the field from outside, such as by the edit action on a failed
 *   message. Applied once, then reported through [onDraftConsumed].
 * @param onDraftConsumed Invoked after [draftText] has been placed in the field.
 * @param modifier Applied to the composer.
 */
@Composable
fun ZynComposer(
    onSendClick: (MessageData) -> Unit,
    onStopClick: () -> Unit,
    isGenerating: Boolean,
    modelName: String?,
    onModelClick: () -> Unit,
    draftText: String? = null,
    onDraftConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var messageData by rememberSaveable(stateSaver = MessageData.Saver) {
        mutableStateOf(MessageData())
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val speechInput = rememberSpeechInput()

    val permissionMessage = stringResource(R.string.zyn_composer_mic_permission)
    val speechErrors = speechErrorMessages()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // Whatever the model does with pictures is the provider's business; a staged picture is
        // simply staged, and a refusal comes back as an answer in the chat.
        messageData = messageData.copy(attachments = messageData.attachments + uris.toSet())
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        messageData = messageData.copy(attachments = messageData.attachments + uris.toSet())
    }

    // Pictures taken with the camera follow the same rules as picked ones: they are staged, and the
    // provider decides whether it can read them.
    val startCamera = rememberCameraCapture(
        snackbarHostState = snackbarHostState,
        onCapture = { capture ->
            messageData = messageData.copy(attachments = messageData.attachments + capture)
        },
    )

    // Set when the person grants the microphone permission, so dictation starts without a second tap.
    var dictationRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            dictationRequested = true
        } else {
            scope.launch { snackbarHostState.showSnackbar(permissionMessage) }
        }
    }

    val beginDictation: () -> Unit = {
        speechInput.start(
            baseText = messageData.text,
            onText = { recognized -> messageData = messageData.copy(text = recognized) },
            onError = { reason ->
                val message = speechErrors[reason] ?: speechErrors[SpeechErrorReason.Unknown]
                if (message != null) {
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
            },
        )
    }

    LaunchedEffect(dictationRequested) {
        if (dictationRequested) {
            dictationRequested = false
            beginDictation()
        }
    }

    // Back cancels dictation instead of leaving the screen while the microphone is live.
    BackHandler(enabled = speechInput.isActive) {
        speechInput.cancel()
    }

    // A draft pushed from outside (the edit action on a failed message) fills the field once and is
    // then consumed, so a rotation or a recomposition does not paste it a second time.
    LaunchedEffect(draftText) {
        if (draftText != null) {
            messageData = messageData.copy(text = draftText)
            onDraftConsumed()
        }
    }

    val startDictation: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) beginDictation() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val canSend = (messageData.text.isNotBlank() || messageData.attachments.isNotEmpty()) && !isGenerating

    val sendMessage = {
        // Sending ends dictation first, so the two never fight over the field.
        if (speechInput.isActive) speechInput.cancel()

        if (canSend) {
            val message = messageData
            messageData = MessageData()
            keyboardController?.hide()
            onSendClick(message)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SnackbarHost(hostState = snackbarHostState)

        Surface(
            shape = ComposerShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 1.dp,
        ) {
            Column {
                // Staged attachments sit inside the field itself, so they read as part of what is
                // being written rather than floating above the composer.
                if (messageData.attachments.isNotEmpty()) {
                    StagedAttachments(
                        attachments = messageData.attachments,
                        onRemove = { uri ->
                            messageData = messageData.copy(attachments = messageData.attachments - uri)
                        },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                }

                ComposerTextField(
                    text = messageData.text,
                    enabled = !isGenerating,
                    onTextChange = { typed ->
                        // The person's typing takes over from dictation.
                        if (speechInput.isActive) speechInput.cancel()
                        messageData = messageData.copy(text = typed)
                    },
                    onSend = sendMessage,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AttachmentButton(
                        enabled = !isGenerating,
                        onCameraClick = startCamera,
                        onPhotosClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(
                                    mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        onFilesClick = { documentPicker.launch(DOCUMENT_MIME_TYPES) },
                    )

                    ModelSelector(
                        modelName = modelName,
                        onClick = onModelClick,
                        enabled = !isGenerating,
                    )

                    Box(modifier = Modifier.weight(1f))

                    SmartSendButton(
                        hasText = messageData.text.isNotBlank() || messageData.attachments.isNotEmpty(),
                        isGenerating = isGenerating,
                        speechState = speechInput.state,
                        onSend = sendMessage,
                        onStopGenerating = onStopClick,
                        onStartSpeech = startDictation,
                        onStopSpeech = { speechInput.stop() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerTextField(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp),
    ) {
        if (text.isEmpty()) {
            Text(
                text = stringResource(R.string.zyn_composer_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }

        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 24.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            maxLines = 8,
        )
    }
}

/** User-facing copy for every dictation failure. */
@Composable
private fun speechErrorMessages(): Map<SpeechErrorReason, String> = mapOf(
    SpeechErrorReason.PermissionDenied to stringResource(R.string.zyn_speech_error_permission),
    SpeechErrorReason.NoMatch to stringResource(R.string.zyn_speech_error_no_match),
    SpeechErrorReason.RecognizerBusy to stringResource(R.string.zyn_speech_error_busy),
    SpeechErrorReason.Network to stringResource(R.string.zyn_speech_error_network),
    SpeechErrorReason.Audio to stringResource(R.string.zyn_speech_error_audio),
    SpeechErrorReason.Unavailable to stringResource(R.string.zyn_speech_error_unavailable),
    SpeechErrorReason.Unknown to stringResource(R.string.zyn_speech_error_unknown),
)

private const val MAX_ATTACHMENTS = 5
private val DOCUMENT_MIME_TYPES = arrayOf("*/*")
