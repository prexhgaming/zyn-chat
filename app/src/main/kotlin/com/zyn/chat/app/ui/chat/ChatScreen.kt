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

package com.zyn.chat.app.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import com.zyn.chat.app.ChatDependencies
import com.zyn.chat.app.R
import com.zyn.chat.app.di.ChatViewModelFactory
import com.zyn.chat.app.presentation.chat.ChatUiState
import com.zyn.chat.app.presentation.chat.ChatUiState.Action
import com.zyn.chat.app.presentation.chat.ChatViewModel
import com.zyn.chat.app.presentation.chat.getCurrentAssistantMessage
import com.zyn.chat.app.presentation.chat.getCurrentUserMessage
import com.zyn.chat.app.presentation.chat.isBusy
import com.zyn.chat.app.ui.components.ChatEmptyState
import com.zyn.chat.app.ui.components.ChatMessageItem
import com.zyn.chat.app.ui.components.ChatScaffold
import com.zyn.chat.app.ui.components.ChatTopBar
import com.zyn.chat.app.ui.components.ErrorActionSheet
import com.zyn.chat.app.ui.components.MessageActionMenu
import com.zyn.chat.compose.ui.component.AITypingIndicator
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String?,
    localConversationId: String,
    chatDependencies: ChatDependencies,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNewChatClick: () -> Unit = {},
    onChatDeleted: () -> Unit = {},
    modelName: String? = null,
    onModelClick: () -> Unit = {},
) {
    val appContext = LocalContext.current.applicationContext
    // Keyed by the storage id, which every chat has. Keying by the channel id collided: it is null
    // for every conversation answered by the person's own provider, so they all shared one ViewModel.
    val chatViewModel = viewModel<ChatViewModel>(
        key = localConversationId,
        factory = ChatViewModelFactory(
            chatAiRepository = chatDependencies.chatAiRepository,
            providerRepository = chatDependencies.providerRepository,
            apiKeyStore = chatDependencies.apiKeyStore,
            chatEngine = chatDependencies.chatEngine,
            conversations = chatDependencies.conversationRepository,
            localConversationId = localConversationId,
            appContext = appContext,
            conversationId = conversationId,
        ),
    )

    val listState = rememberLazyListState()
    val state by chatViewModel.uiState.collectAsState()

    val messages = state.messages
    val isAssistantBusy = state.assistantState.isBusy()
    val assistantState = state.assistantState

    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // The failed message whose recovery actions (retry / edit) are showing, if any.
    var errorActionMessage by remember { mutableStateOf<ChatUiState.Message?>(null) }

    // The message whose long-press action menu is showing, if any.
    var actionMenuMessage by remember { mutableStateOf<ChatUiState.Message?>(null) }

    // Text pushed into the composer by the edit action; the composer consumes it once.
    var editDraft by remember { mutableStateOf<String?>(null) }

    val actionMenuState = rememberModalBottomSheetState()

    val copyMessage: (ChatUiState.Message) -> Unit = { message ->
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("message", message.content))
        Toast.makeText(appContext, R.string.zyn_message_copied, Toast.LENGTH_SHORT).show()
    }

    // Sharing hands the answer to the system chooser, so it goes wherever the person already shares
    // text instead of tying the app to one target.
    val shareMessage: (ChatUiState.Message) -> Unit = { message ->
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message.content)
        }
        val chooser = Intent.createChooser(send, null).apply {
            // The chooser is started from the application context, which needs this to run outside it.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(chooser)
    }

    // Only the newest answer can be regenerated: it replaces the exchange it supersedes.
    val lastAssistantId = messages.firstOrNull { it.role == ChatUiState.Message.Role.Assistant }?.id

    // Editing a failed message drops it and puts its text back into the composer.
    val editFailedMessage: (ChatUiState.Message) -> Unit = { message ->
        editDraft = message.content
        chatViewModel.discardMessage(message.id)
    }

    // Scroll to bottom when a new message is added. Keyed on the newest message rather than the
    // whole state, so streaming text does not restart the scroll on every token.
    LaunchedEffect(state.messages.firstOrNull()?.id) {
        if (state.getCurrentUserMessage() != null) {
            delay(50) // Small delay to ensure layout is complete
            if (listState.layoutInfo.totalItemsCount > 0) {
                // In reverse layout, scroll to item 0 to show the bottom (newest messages)
                listState.animateScrollToItem(
                    index = 0,
                    scrollOffset = 0,
                )
            }
        }
    }

    val confirmDelete = {
        showDeleteConfirmation = false
        chatViewModel.deleteChannel(
            onSuccess = { onChatDeleted() },
        )
    }

    val currentAssistantMessage = state.getCurrentAssistantMessage()

    errorActionMessage?.let { failed ->
        ErrorActionSheet(
            errorReason = failed.errorReason ?: stringResource(R.string.zyn_error_failed_send),
            onRetry = { chatViewModel.retryMessage(failed.id) },
            onEdit = { editFailedMessage(failed) },
            onDismiss = { errorActionMessage = null },
        )
    }

    actionMenuMessage?.let { message ->
        MessageActionMenu(
            onCopy = { copyMessage(message) },
            onRetry = message.takeIf(ChatUiState.Message::isFailed)
                ?.let { failed -> { chatViewModel.retryMessage(failed.id) } },
            onEdit = message.takeIf { it.isFailed && it.role == ChatUiState.Message.Role.User }
                ?.let { failed -> { editFailedMessage(failed) } },
            onDelete = null,
            onDismiss = { actionMenuMessage = null },
            sheetState = actionMenuState,
        )
    }

    ChatScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { modifier ->
            ChatTopBar(
                modifier = modifier.background(brush = topBarFade()),
                title = state.title,
                onMenuClick = onMenuClick,
                onNewChatClick = if (state.actions.contains(Action.NewChat)) {
                    { onNewChatClick() }
                } else {
                    null
                },
                onDeleteClick = if (state.actions.contains(Action.DeleteChat)) {
                    { showDeleteConfirmation = true }
                } else {
                    null
                },
            )
        },
        bottomBar = { modifier ->
            Column(modifier = modifier.background(brush = composerFade())) {
                ZynComposer(
                    onSendClick = chatViewModel::sendMessage,
                    onStopClick = chatViewModel::stopStreaming,
                    isGenerating = isAssistantBusy,
                    modelName = modelName,
                    onModelClick = onModelClick,
                    draftText = editDraft,
                    onDraftConsumed = { editDraft = null },
                )
            }
        },
    ) { contentPadding ->
        AnimatedContent(
            targetState = state.isLoading,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (messages.isEmpty()) {
                ChatEmptyState(modifier = Modifier.padding(contentPadding))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding,
                    reverseLayout = true,
                ) {
                    // Assistant loading indicator appears at the bottom
                    item(key = "assistant_indicator") {
                        key(assistantState, currentAssistantMessage) {
                            when (assistantState) {
                                ChatUiState.AssistantState.Error -> AssistantErrorMessage(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )

                                else -> {
                                    AssistantLoadingIndicator(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        assistantState = assistantState,
                                        assistantMessage = currentAssistantMessage,
                                    )
                                }
                            }
                        }
                    }
                    // Messages appear above the Assistant loading indicator
                    items(
                        key = ChatUiState.Message::id,
                        items = messages,
                    ) { message ->
                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            enter = androidx.compose.animation.fadeIn(
                                animationSpec = androidx.compose.animation.core.tween(300),
                            ) + androidx.compose.animation.slideInVertically(
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                                ),
                                initialOffsetY = { it / 4 },
                            ),
                        ) {
                            ChatMessageItem(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 7.dp)
                                    .animateItem(),
                                message = message,
                                onErrorAction = { errorActionMessage = it },
                                onLongPress = { actionMenuMessage = it },
                                onCopy = { copyMessage(message) },
                                onShare = { shareMessage(message) },
                                onRegenerate = { chatViewModel.regenerate(message) },
                                isLastAssistant = message.id == lastAssistantId,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.zyn_dialog_delete_title)) },
            text = { Text(stringResource(R.string.zyn_dialog_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = confirmDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.zyn_action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    Text(stringResource(R.string.zyn_action_cancel))
                }
            },
        )
    }
}

/** Fades the header into the conversation behind it. */
@Composable
private fun topBarFade(): Brush =
    Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
            Color.Transparent,
        ),
    )

/** Fades the composer area into the conversation above it. */
@Composable
private fun composerFade(): Brush =
    Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.background,
        ),
    )

@Composable
private fun AssistantErrorMessage(
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.zyn_error_message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun AssistantLoadingIndicator(
    assistantState: ChatUiState.AssistantState,
    assistantMessage: ChatUiState.Message?,
    modifier: Modifier = Modifier,
) {
    // A reply with steps shows its own progress.
    if (assistantMessage?.parts.orEmpty().isNotEmpty()) return
    val text = when (assistantState) {
        ChatUiState.AssistantState.Thinking -> stringResource(R.string.zyn_reply_thinking)
        ChatUiState.AssistantState.CheckingSources -> stringResource(R.string.zyn_reply_checking_sources)
        ChatUiState.AssistantState.Generating -> stringResource(R.string.zyn_reply_generating).takeIf {
            assistantMessage == null || assistantMessage.content.isEmpty()
        }

        else -> null
    }
    if (text != null) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            AITypingIndicator(
                modifier = modifier,
                label = {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
            )
        }
    }
}
