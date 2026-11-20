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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyn.chat.app.ChatDependencies
import com.zyn.chat.app.di.ChatViewModelFactory
import com.zyn.chat.app.presentation.chat.ChatUiState
import com.zyn.chat.app.presentation.chat.ChatUiState.Action
import com.zyn.chat.app.presentation.chat.ChatViewModel
import com.zyn.chat.app.presentation.chat.getCurrentAssistantMessage
import com.zyn.chat.app.presentation.chat.getCurrentUserMessage
import com.zyn.chat.app.presentation.chat.isBusy
import com.zyn.chat.app.ui.components.ChatMessageItem
import com.zyn.chat.app.ui.components.ChatScaffold
import com.zyn.chat.app.ui.components.ChatTopBar
import com.zyn.chat.compose.ui.component.AISuggestions
import com.zyn.chat.compose.ui.component.AITypingIndicator
import com.zyn.chat.compose.ui.component.ChatComposer
import com.zyn.chat.compose.ui.component.MessageData
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(
    conversationId: String?,
    chatDependencies: ChatDependencies,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNewChatClick: () -> Unit = {},
    onChatDeleted: () -> Unit = {},
) {
    val appContext = LocalContext.current.applicationContext
    val chatViewModel = viewModel<ChatViewModel>(
        key = conversationId,
        factory = ChatViewModelFactory(
            chatAiRepository = chatDependencies.chatAiRepository,
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

    // Scroll to bottom when the user sends a new message
    LaunchedEffect(state) {
        if (state.getCurrentUserMessage() != null) {
            delay(100) // Small delay to ensure layout is complete
            if (listState.layoutInfo.totalItemsCount > 0) {
                // In reverse layout, scroll to item 0 to show the bottom (newest messages)
                listState.animateScrollToItem(0)
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

    ChatScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { modifier ->
            ChatTopBar(
                modifier = modifier
                    // Blur gradient to create a visual fade effect that blends with the message list behind it
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                Color.Transparent,
                            ),
                        ),
                    ),
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
            Column(
                modifier = modifier
                    // Blur gradient to create a visual fade effect that blends with the message list behind it
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            ) {
                if (!state.isLoading && messages.isEmpty()) {
                    AISuggestions(
                        suggestions = SUGGESTIONS,
                        onSuggestionClick = { text -> chatViewModel.sendMessage(MessageData(text = text)) },
                    )
                }
                ChatComposer(
                    onSendClick = chatViewModel::sendMessage,
                    onStopClick = chatViewModel::stopStreaming,
                    isGenerating = isAssistantBusy,
                )
            }
        },
    ) { contentPadding ->
        AnimatedContent(
            targetState = state.isLoading,
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
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
                        ChatMessageItem(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            message = message,
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Chat") },
            text = { Text("Are you sure you want to delete this chat? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = confirmDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

private val SUGGESTIONS = listOf(
    "Create a painting in Renaissance-style",
    "Create a workout plan for resistance training",
    "Help me study vocabulary for an exam",
    "Tell me the best stocks to invest",
    "Top 5 restaurants in New York",
)

@Composable
private fun AssistantErrorMessage(
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Sorry, I encountered an error while processing your request. Please try again.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
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
        ChatUiState.AssistantState.Thinking -> "Thinking"
        ChatUiState.AssistantState.CheckingSources -> "Checking sources"
        ChatUiState.AssistantState.Generating -> "Generating response".takeIf {
            assistantMessage == null || assistantMessage.content.isEmpty()
        }

        else -> null
    }
    if (text != null) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            AITypingIndicator(
                modifier = modifier,
                label = { Text(text = text) },
            )
        }
    }
}
