package com.zyn.chat.app.ui.chat

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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.zyn.chat.app.R
import com.zyn.chat.app.ui.theme.ComposerShape
import com.zyn.chat.compose.ui.component.MessageData
import com.zyn.chat.compose.ui.component.SpeechToTextButton
import com.zyn.chat.compose.ui.component.rememberSpeechToTextButtonState
import kotlinx.coroutines.launch

/**
 * Message composer.
 *
 * A rounded capsule that holds the message field on top and a control row below: the attachment
 * picker, the model selector, dictation, and the send (or stop) button.
 *
 * Photos and documents are staged as attachments on [MessageData], exactly like the chat composer
 * in `zyn-chat-compose`, so they flow through the existing send path untouched.
 *
 * @param onSendClick Invoked with the composed message when the person sends it.
 * @param onStopClick Invoked while the assistant is answering, to stop it.
 * @param isGenerating Whether the assistant is currently generating a response.
 * @param selectedModel Model shown in the selector pill.
 * @param models Models offered by the selector.
 * @param onModelSelected Invoked with the model the person picked.
 * @param modifier Applied to the composer.
 */
@Composable
fun ZynComposer(
    onSendClick: (MessageData) -> Unit,
    onStopClick: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
    selectedModel: ChatModel = DefaultModel,
    models: List<ChatModel> = AvailableModels,
    onModelSelected: (ChatModel) -> Unit = {},
) {
    var messageData by rememberSaveable(stateSaver = MessageData.Saver) {
        mutableStateOf(MessageData())
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val microphoneDeniedMessage = stringResource(R.string.zyn_composer_mic_permission)

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        messageData = messageData.copy(attachments = messageData.attachments + uris.toSet())
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        messageData = messageData.copy(attachments = messageData.attachments + uris.toSet())
    }

    var dictationBase by remember { mutableStateOf("") }
    val speechToTextState = rememberSpeechToTextButtonState(
        onPartialResult = { recognized ->
            messageData = messageData.copy(text = mergeDictation(dictationBase, recognized))
        },
        onFinalResult = { recognized ->
            messageData = messageData.copy(text = mergeDictation(dictationBase, recognized))
        },
    )

    LaunchedEffect(speechToTextState.isRecording()) {
        if (speechToTextState.isRecording()) dictationBase = messageData.text
    }

    val canSend = messageData.text.isNotBlank() && !isGenerating

    val sendMessage = {
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

        if (messageData.attachments.isNotEmpty()) {
            StagedAttachments(
                attachments = messageData.attachments,
                onRemove = { uri ->
                    messageData = messageData.copy(attachments = messageData.attachments - uri)
                },
            )
        }

        Surface(
            shape = ComposerShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 1.dp,
        ) {
            Column {
                ComposerTextField(
                    text = messageData.text,
                    enabled = !isGenerating && !speechToTextState.isRecording(),
                    onTextChange = { messageData = messageData.copy(text = it) },
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
                        onPhotosClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(
                                    mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    maxItems = MAX_ATTACHMENTS,
                                ),
                            )
                        },
                        onFilesClick = { documentPicker.launch(DOCUMENT_MIME_TYPES) },
                    )

                    ModelSelector(
                        selectedModel = selectedModel,
                        models = models,
                        onModelSelected = onModelSelected,
                        enabled = !isGenerating,
                    )

                    Box(modifier = Modifier.weight(1f))

                    if (!isGenerating && messageData.text.isBlank()) {
                        SpeechToTextButton(
                            state = speechToTextState,
                            onPermissionDenied = {
                                scope.launch { snackbarHostState.showSnackbar(microphoneDeniedMessage) }
                            },
                        )
                    }

                    SendButton(
                        isGenerating = isGenerating,
                        enabled = canSend || isGenerating,
                        onSendClick = sendMessage,
                        onStopClick = onStopClick,
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

private const val MAX_ATTACHMENTS = 5
private val DOCUMENT_MIME_TYPES = arrayOf("*/*")

private fun mergeDictation(base: String, recognized: String): String =
    if (base.isBlank()) recognized else "${base.trim()} $recognized"
