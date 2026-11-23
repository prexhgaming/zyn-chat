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

package com.zyn.chat.app.presentation.chat

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyn.chat.compose.parts.AIMessagePart
import com.zyn.chat.app.R
import com.zyn.chat.app.data.chat.ImagePayload
import com.zyn.chat.app.data.providers.ApiKeyStore
import com.zyn.chat.app.data.providers.ProviderException
import com.zyn.chat.app.data.providers.ProviderRepository
import com.zyn.chat.app.data.providers.refusedPictures
import com.zyn.chat.app.data.repository.ChatAiRepository
import com.zyn.chat.app.domain.chat.ChatChunk
import com.zyn.chat.app.domain.chat.ChatEngine
import com.zyn.chat.app.domain.chat.ChatImage
import com.zyn.chat.app.domain.chat.ChatRole
import com.zyn.chat.app.domain.chat.ChatTurn
import com.zyn.chat.app.domain.chat.ConversationRepository
import com.zyn.chat.app.domain.chat.isChannelId
import com.zyn.chat.app.domain.isFromAi
import com.zyn.chat.compose.ui.component.MessageData
import com.zyn.chat.ondevice.AIConversationTurn
import com.zyn.chat.ondevice.AIOnDeviceModel
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.api.state.watchChannelAsState
import io.getstream.chat.android.client.channel.subscribeFor
import io.getstream.chat.android.client.events.AIIndicatorClearEvent
import io.getstream.chat.android.client.events.AIIndicatorStopEvent
import io.getstream.chat.android.client.events.AIIndicatorUpdatedEvent
import io.getstream.chat.android.client.events.ChatEvent
import io.getstream.chat.android.client.extensions.cidToTypeAndId
import io.getstream.chat.android.core.internal.InternalStreamChatApi
import io.getstream.chat.android.models.Attachment
import io.getstream.chat.android.models.ChannelCapabilities
import io.getstream.chat.android.models.EventType
import io.getstream.chat.android.models.User
import io.getstream.chat.android.ui.common.helper.internal.AttachmentStorageHelper
import io.getstream.log.taggedLogger
import io.getstream.result.Error
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import io.getstream.chat.android.models.Message as StreamMessage

/**
 * ViewModel for managing chat conversation state and interactions.
 * Handles message sending, AI agent management, and UI state updates.
 *
 * @param chatClient The Stream Chat client instance
 * @param chatAiRepository Repository for Chat AI operations
 * @param conversationId Optional conversation ID. If null, a new conversation will be created on first message.
 */
@OptIn(ExperimentalCoroutinesApi::class, InternalStreamChatApi::class)
internal class ChatViewModel(
    private val chatClient: ChatClient,
    private val chatAiRepository: ChatAiRepository,
    providerRepository: ProviderRepository,
    apiKeyStore: ApiKeyStore,
    chatEngine: ChatEngine,
    private val conversations: ConversationRepository,
    private val localConversationId: String,
    private val appContext: Context,
    conversationId: String?,
) : ViewModel() {

    private val attachmentStorageHelper = AttachmentStorageHelper(appContext)

    // Answers when the AI agent couldn't be started, such as without a connection to the backend.
    private val onDeviceModel = AIOnDeviceModel(appContext)
    private var agentFailed = false
    private var onDeviceAnswer: Job? = null

    // Questions answered on the device and their answers, newest first. They are not sent to Stream.
    private val localMessages = MutableStateFlow<List<ChatUiState.Message>>(emptyList())

    // Messages that came back from a Stream channel, newest first. Stays empty when there is no
    // channel, which is the case for every chat answered by the person's own provider.
    private val channelMessages = MutableStateFlow<List<ChatUiState.Message>>(emptyList())

    private val logger by taggedLogger()

    /**
     * Bring-your-own-key answers. Used whenever a provider and a model are configured, in which case
     * the app talks to the provider directly and this conversation never touches Stream.
     */
    private val providerAnswers = ProviderAnswerSource(
        repository = providerRepository,
        apiKeyStore = apiKeyStore,
        chatEngine = chatEngine,
        context = appContext,
    )

    /** The in-flight provider answer, cancelled when the person stops it or leaves the chat. */
    private var providerAnswer: Job? = null

    /**
     * The Stream channel id for this chat, or `null` when it has no channel at all.
     *
     * Only a value that actually looks like a cid (`type:id`) is accepted. A stored conversation id
     * is a bare uuid, and handing that to the client throws — which is what crashed the app when a
     * past chat was opened from the drawer.
     */
    private val cid = MutableStateFlow(conversationId?.takeIf(::isChannelId))

    private val _uiState = MutableStateFlow(ChatUiState())

    /**
     * The current UI state of the chat conversation.
     * Observing this StateFlow allows the UI to reactively update when the state changes.
     */
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val currentUserId = chatClient.clientState.user
        .mapNotNull { user -> user?.id }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = "",
        )

    // Message to be sent once the AI agent is started
    private var pendingMessage: StreamMessage? = null

    init {
        // The message list is assembled from both sources unconditionally. It used to be built
        // inside the channel chain below, so with no Stream channel — every bring-your-own-key
        // chat — a sent message had nowhere to appear and the screen stayed empty.
        combine(channelMessages, localMessages) { fromChannel, local -> local + fromChannel }
            .onEach { messages ->
                _uiState.update { state -> state.copy(messages = messages, isLoading = false) }
            }
            .launchIn(viewModelScope)

        // Reopening a conversation restores what was said in it. This used to be missing entirely,
        // so a reopened chat came up empty even once it stopped crashing.
        viewModelScope.launch {
            val storedId = cid.value ?: localConversationId
            val stored = conversations.history(storedId)
            if (stored.isNotEmpty() && localMessages.value.isEmpty()) {
                localMessages.value = stored.reversed().map { turn ->
                    localMessage(
                        role = if (turn.role == ChatRole.Assistant) {
                            ChatUiState.Message.Role.Assistant
                        } else {
                            ChatUiState.Message.Role.User
                        },
                        content = turn.content,
                    )
                }
            }

            val meta = conversations.conversation(storedId)
            if (meta != null && meta.title.isNotBlank()) {
                _uiState.update { state -> state.copy(title = meta.title) }
            }
        }

        cid.filterNotNull()
            // Start the AI agent
            .onEach(::startAIAgentForChannel)
            // Subscribe to channel events
            .onEach { cid ->
                logger.d { "Subscribing to chat events for channel: $cid" }
                chatClient.channel(cid).subscribeFor<ChatEvent>(::handleChatEvent)
            }
            // Observe messages in the channel
            .flatMapLatest { cid ->
                logger.d { "Watching channel for messages: $cid" }
                chatClient.watchChannelAsState(cid = cid, messageLimit = 30)
            }
            .filterNotNull()
            .flatMapLatest { channelState ->
                combine(channelState.channelData, channelState.messages) {
                    channelState.toChannel()
                }
            }
            .onEach { channel ->
                channelMessages.value = channel.messages
                    .mapNotNull { message -> message.toChatMessage(currentUserId.value) }
                    .reversed()

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        title = channel.name.takeIf(String::isNotBlank) ?: "New Chat",
                        actions = buildList {
                            if (cid.value != null) {
                                add(ChatUiState.Action.NewChat)
                                if (channel.ownCapabilities.contains(ChannelCapabilities.DELETE_CHANNEL)) {
                                    add(ChatUiState.Action.DeleteChat)
                                }
                            }
                        },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Sends a message via Stream Chat.
     *
     * Validates input, builds lightweight attachments off the main thread, optimistically
     * updates the UI, then resolves attachment files before dispatching to the SDK.
     * When no channel exists yet, one is created first and the message is queued until the
     * AI agent is ready.
     */
    fun sendMessage(data: MessageData) {
        val text = data.text.trim()
        if ((text.isEmpty() && data.attachments.isEmpty()) || _uiState.value.assistantState.isBusy()) {
            return
        }
        // Busy from the tap itself, so a second tap during the IO hop below is ignored.
        _uiState.update { it.copy(assistantState = ChatUiState.AssistantState.Thinking) }

        // Bring-your-own-key first: with a provider and a model configured, the app talks to the
        // provider directly and this conversation never touches Stream.
        val selection = providerAnswers.currentSelection()
        if (selection != null) {
            // Staged pictures travel with the question, so the provider sees what was attached.
            val staged = data.attachments.toList()
            providerAnswer = viewModelScope.launch { answerWithProvider(text, staged, selection) }
            return
        }

        viewModelScope.launch {
            if (cid.value != null && agentFailed && onDeviceModel.isAvailable()) {
                onDeviceAnswer = launch { answerOnDevice(text) }
                return@launch
            }

            val uris = data.attachments.toList()
            val lightweightAttachments = withContext(Dispatchers.IO) {
                val metadata = attachmentStorageHelper.resolveMetadata(uris)
                attachmentStorageHelper.toAttachments(metadata)
            }

            val message = StreamMessage(
                text = text,
                user = User(id = currentUserId.value),
                attachments = lightweightAttachments,
            )

            _uiState.update { state ->
                state.copy(
                    messages = listOfNotNull(message.toChatMessage(currentUserId.value)) + state.messages,
                )
            }

            // Copies picker URIs into local cache files. Suspend; dispatches IO internally.
            val readyMessage = message.copy(
                attachments = attachmentStorageHelper.resolveAttachmentFiles(message.attachments),
            )

            val cid = cid.value

            if (cid == null) {
                // Queue the message to be sent once the new channel and AI agent are ready.
                pendingMessage = readyMessage

                val memberIds = listOf(currentUserId.value)
                chatClient.createChannel(
                    channelType = "messaging",
                    channelId = UUID.randomUUID().toString(),
                    memberIds = memberIds,
                    extraData = emptyMap(),
                ).enqueue { result ->
                    result.onSuccess { newChannel ->
                        val newCid = newChannel.cid
                        this@ChatViewModel.cid.value = newCid
                        logger.d { "Created new channel with cid: $newCid" }
                    }.onError { e ->
                        logger.e { "Failed to create channel: ${e.message}" }
                    }
                }
            } else {
                sendMessage(cid, readyMessage)
            }
        }
    }

    /**
     * Resends a message that failed. The failed attempt is dropped first, so the retry does not
     * duplicate the question, and the send goes through the normal path: the same routing, provider
     * and model a message typed just now would take.
     *
     * @param messageId Id of the failed message to resend.
     */
    fun retryMessage(messageId: String) {
        val failed = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        if (!failed.isFailed || failed.role != ChatUiState.Message.Role.User) return

        val text = failed.content.trim()
        if (text.isEmpty()) return

        localMessages.update { current -> current.filterNot { it.id == messageId } }
        sendMessage(MessageData(text = text))
    }

    /**
     * Removes a failed message, so its text can be edited in the composer and sent again fresh.
     *
     * @param messageId Id of the failed message to remove.
     */
    fun discardMessage(messageId: String) {
        localMessages.update { current -> current.filterNot { it.id == messageId } }
    }

    private suspend fun startAIAgentForChannel(cid: String): Result<Unit> {
        val (channelType, channelId) = cid.cidToTypeAndId()
        val platform = "openai"
        logger.d { "Starting AI agent on channel: $cid, platform: $platform" }

        return chatAiRepository.startAIAgent(
            channelType = channelType,
            channelId = channelId,
            platform = platform,
        ).onSuccess {
            agentFailed = false
            logger.d { "AI agent started successfully on channel: $cid" }
            // Send any pending message that was queued while starting the AI agent
            pendingMessage?.let { message ->
                sendMessage(cid, message) {
                    // Remove from pending once sent
                    pendingMessage = message
                    // Summarize after the first message is sent
                    viewModelScope.launch {
                        val platform = "openai"
                        logger.d { "Summarizing message: ${message.text}" }
                        chatAiRepository.summarize(
                            text = message.text,
                            platform = platform,
                        ).onSuccess {
                            logger.d { "Message summarized successfully" }
                            chatClient.channel(cid).updatePartial(
                                set = mapOf("name" to it),
                            ).enqueue { result ->
                                result.onSuccess {
                                    logger.d { "Channel updated with summary successfully" }
                                }.onError { e ->
                                    logger.e { "Failed to update channel with summary: ${e.message}" }
                                }
                            }
                        }.onFailure { e ->
                            logger.e { "Failed to summarize message: ${e.message}" }
                        }
                    }
                }
            }
        }.onFailure { e ->
            agentFailed = true
            logger.e { "Failed to start AI agent: ${e.message}" }
        }
    }

    /**
     * Pictures to send with a question.
     *
     * Every staged picture is sent: whether a deployment can read pictures is the provider's answer
     * to give, so the app does not pre-empt it. A provider that refuses them says so, and that
     * failure is reported in the conversation like any other.
     *
     * @param attachments URIs staged with the question, which may include documents.
     */
    private suspend fun imagesFor(attachments: List<Uri>): List<ChatImage> =
        ImagePayload.load(context = appContext, uris = attachments)

    /**
     * Staged URIs as attachments the transcript can draw, without copying any file.
     *
     * The picture itself is the attachment's `imageUrl`, so the chat can decode the same local URI
     * the composer previewed. The Stream helper is not used here: it describes files for upload,
     * which is not what a message rendered on this screen needs.
     */
    private suspend fun stagedAttachments(attachments: List<Uri>): List<Attachment> =
        withContext(Dispatchers.IO) {
            attachments.map { uri ->
                val mimeType = runCatching { appContext.contentResolver.getType(uri) }.getOrNull()
                val name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)

                if (mimeType?.startsWith("image/") == true) {
                    Attachment(type = "image", imageUrl = uri.toString(), name = name, mimeType = mimeType)
                } else {
                    Attachment(type = "file", assetUrl = uri.toString(), name = name, mimeType = mimeType)
                }
            }
        }

    /**
     * Puts the answer being generated into the transcript.
     *
     * @param answerId Id of the answer, which stays the same while it grows.
     * @param text The answer so far.
     * @param isGenerating Whether the provider is still sending.
     */
    private fun showAnswer(answerId: String, text: String, isGenerating: Boolean) {
        val message = localMessage(
            role = ChatUiState.Message.Role.Assistant,
            content = text,
            id = answerId,
            isGenerating = isGenerating,
        )
        localMessages.update { messages -> listOf(message) + messages.filterNot { it.id == answerId } }
    }

    /**
     * The conversation as the provider should see it, ending with the new question.
     *
     * The stored turns of this conversation win; the screen's messages are the fallback for
     * conversations whose history still comes from Stream.
     *
     * @param conversationId Conversation being answered.
     * @param question What the person just asked.
     * @param images Pictures to send with the question.
     */
    private suspend fun providerHistory(
        conversationId: String,
        question: String,
        images: List<ChatImage>,
    ): List<ChatTurn> {
        val storedTurns = conversations.history(conversationId)
        val turns = if (storedTurns.isNotEmpty()) {
            storedTurns
        } else {
            _uiState.value.messages.reversed().map { message ->
                ChatTurn(
                    role = when (message.role) {
                        ChatUiState.Message.Role.Assistant -> ChatRole.Assistant
                        ChatUiState.Message.Role.User, ChatUiState.Message.Role.Other -> ChatRole.User
                    },
                    content = message.content,
                )
            }
        }
        return turns + ChatTurn(role = ChatRole.User, content = question, images = images)
    }

    /**
     * Marks the question as failed and drops any partial answer.
     *
     * The failure belongs to the question, not to a reply: the question carries the reason, which is
     * what the retry and edit actions hang off, and a retry starts clean.
     *
     * @param questionId The question that failed.
     * @param answerId The partial answer to drop.
     * @param errorReason What to show on the question.
     */
    private fun markQuestionFailed(questionId: String, answerId: String, errorReason: String) {
        localMessages.update { messages ->
            messages
                .filterNot { it.id == answerId }
                .map { message ->
                    if (message.id == questionId) {
                        message.copy(isFailed = true, errorReason = errorReason)
                    } else {
                        message
                    }
                }
        }
    }

    /**
     * Streams an answer from the person's own provider.
     *
     * The history is built from this conversation alone, so no other chat can influence the answer,
     * and the request carries its own key: nothing is sent anywhere except the provider the person
     * configured. Cancelling the collection (stop, or leaving the chat) cancels the HTTP call.
     *
     * @param text What the person just asked.
     * @param attachments URIs staged with the question, sent with it as pictures.
     * @param selection Provider and model to ask.
     */
    private suspend fun answerWithProvider(
        text: String,
        attachments: List<Uri>,
        selection: ProviderSelection,
    ) {
        val answerId = UUID.randomUUID().toString()
        val questionId = UUID.randomUUID().toString()
        val conversationId = cid.value ?: localConversationId

        val images = imagesFor(attachments = attachments)
        val staged = if (attachments.isEmpty()) emptyList() else stagedAttachments(attachments)

        // Built before the question is added, so it appears in the request exactly once.
        val history = providerHistory(
            conversationId = conversationId,
            question = text,
            images = images,
        )

        localMessages.update {
            listOf(
                localMessage(
                    role = ChatUiState.Message.Role.User,
                    content = text,
                    id = questionId,
                    attachments = staged,
                ),
            ) + it
        }

        val answer = StringBuilder()
        val finalState = streamProviderAnswer(
            selection = selection,
            conversationId = conversationId,
            history = history,
            questionId = questionId,
            answerId = answerId,
            askedWithPictures = images.isNotEmpty(),
            answer = answer,
        )

        recordExchange(
            conversationId = conversationId,
            question = text,
            answer = answer.toString(),
            selection = selection,
            finalState = finalState,
        )
    }

    /**
     * Collects the provider's answer into [answer], publishing it as it grows.
     *
     * Text is published at most once per frame instead of once per token: a fast provider used to
     * rebuild the message list hundreds of times a second, and the UI could not draw that fast
     * anyway. Slow streams still publish every chunk.
     *
     * @param selection Provider and model to ask.
     * @param conversationId Conversation being answered.
     * @param history The conversation so far, ending with the new question.
     * @param questionId The question, marked failed if the provider fails.
     * @param answerId Id the growing answer is published under.
     * @param askedWithPictures Whether the request carried pictures.
     * @param answer Collects the answer text.
     * @return How the answer ended.
     */
    private suspend fun streamProviderAnswer(
        selection: ProviderSelection,
        conversationId: String,
        history: List<ChatTurn>,
        questionId: String,
        answerId: String,
        askedWithPictures: Boolean,
        answer: StringBuilder,
    ): ChatUiState.AssistantState {
        var finalState: ChatUiState.AssistantState = ChatUiState.AssistantState.Idle
        var generating = false
        var lastPublishedAt = 0L

        try {
            providerAnswers.stream(selection, conversationId, history).collect { chunk ->
                if (chunk is ChatChunk.Text) {
                    answer.append(chunk.delta)
                    if (!generating) {
                        generating = true
                        _uiState.update { it.copy(assistantState = ChatUiState.AssistantState.Generating) }
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastPublishedAt >= STREAM_FRAME_MS) {
                        lastPublishedAt = now
                        showAnswer(answerId = answerId, text = answer.toString(), isGenerating = true)
                    }
                }
            }
        } catch (e: ProviderException) {
            logger.e { "Provider answer failed: ${e.reason}" }
            finalState = ChatUiState.AssistantState.Error
            // A provider that rejected the pictures says so in its own words; that is more useful in
            // the conversation than the generic failure copy.
            val errorReason = if (askedWithPictures && e.refusedPictures()) {
                appContext.getString(R.string.zyn_provider_error_images_refused)
            } else {
                providerAnswers.failureText(e)
            }
            markQuestionFailed(
                questionId = questionId,
                answerId = answerId,
                errorReason = errorReason,
            )
        } finally {
            if (finalState != ChatUiState.AssistantState.Error && answer.isNotEmpty()) {
                showAnswer(answerId = answerId, text = answer.toString(), isGenerating = false)
            } else {
                localMessages.update { messages ->
                    messages.map { if (it.id == answerId) it.copy(isGenerating = false) else it }
                }
            }
            _uiState.update { it.copy(assistantState = finalState) }
        }

        return finalState
    }

    /**
     * Stores a finished exchange, unless it failed or produced nothing.
     *
     * Storing is also where the conversation gets its name: a short title derived on the device
     * rather than the first message repeated.
     *
     * @param conversationId Conversation the exchange belongs to.
     * @param question What was asked.
     * @param answer What came back.
     * @param selection Provider and model that answered.
     * @param finalState How the answer ended.
     */
    private suspend fun recordExchange(
        conversationId: String,
        question: String,
        answer: String,
        selection: ProviderSelection,
        finalState: ChatUiState.AssistantState,
    ) {
        if (answer.isBlank() || finalState == ChatUiState.AssistantState.Error) return

        conversations.record(
            conversationId = conversationId,
            question = question,
            answer = answer,
            providerId = selection.provider.id,
            modelId = selection.model.id,
        )
    }

    /** Answers [question] with the model on the device. The question and answer stay on this screen. */
    private suspend fun answerOnDevice(question: String) {
        val turns = _uiState.value.messages.reversed().mapNotNull(ChatUiState.Message::toTurn) +
            AIConversationTurn.user(question)
        val answerId = UUID.randomUUID().toString()
        localMessages.update { listOf(localMessage(ChatUiState.Message.Role.User, question)) + it }
        _uiState.update { it.copy(assistantState = ChatUiState.AssistantState.Thinking) }

        var finalState: ChatUiState.AssistantState = ChatUiState.AssistantState.Idle
        try {
            onDeviceModel.reply(ON_DEVICE_INSTRUCTIONS, turns).collect { answer ->
                val message = localMessage(ChatUiState.Message.Role.Assistant, answer, answerId, isGenerating = true)
                localMessages.update { messages -> listOf(message) + messages.filterNot { it.id == answerId } }
                _uiState.update { it.copy(assistantState = ChatUiState.AssistantState.Generating) }
            }
        } catch (e: AIOnDeviceModel.Unavailable) {
            logger.e { "The on-device model is unavailable: ${e.message}" }
            finalState = ChatUiState.AssistantState.Error
        } catch (e: AIOnDeviceModel.Failure) {
            logger.e { "The on-device model failed: ${e.cause?.message}" }
            finalState = ChatUiState.AssistantState.Error
        } finally {
            localMessages.update { messages ->
                messages.map { if (it.id == answerId) it.copy(isGenerating = false) else it }
            }
            _uiState.update { it.copy(assistantState = finalState) }
        }
    }

    private fun sendMessage(
        cid: String,
        message: StreamMessage,
        onSuccess: () -> Unit = {},
    ) {
        chatClient.channel(cid)
            .sendMessage(message = message)
            .enqueue { result ->
                result.onSuccess {
                    onSuccess()
                }.onError { e ->
                    logger.e { "Failed to send message: ${e.message}" }
                }
            }
    }

    /**
     * Stops the current streaming response by sending an AI typing indicator stop event to the server.
     * This tells the AI agent to stop generating content for the current message.
     */
    fun stopStreaming() {
        onDeviceAnswer?.cancel()
        providerAnswer?.cancel()
        val cid = cid.value ?: run {
            logger.d { "No channel available to stop streaming" }
            return
        }

        chatClient.channel(cid)
            .sendEvent(EventType.AI_TYPING_INDICATOR_STOP)
            .enqueue { result ->
                result.onSuccess {
                    logger.d { "Successfully sent AI typing indicator stop event" }
                }.onError { e ->
                    logger.e { "Failed to stop streaming: ${e.message}" }
                }
            }
    }

    private fun handleChatEvent(event: ChatEvent) {
        logger.d { "Received chat event: $event" }

        when (event) {
            is AIIndicatorUpdatedEvent -> {
                handleAIIndicatorUpdated(event)
            }

            is AIIndicatorClearEvent, is AIIndicatorStopEvent -> {
                _uiState.update { state ->
                    state.copy(
                        assistantState = ChatUiState.AssistantState.Idle,
                    )
                }
            }

            else -> Unit
        }
    }

    private fun handleAIIndicatorUpdated(event: AIIndicatorUpdatedEvent) {
        logger.d { "Processing $event" }

        _uiState.update { state ->
            state.copy(
                assistantState = event.aiState.toAssistantState(),
            )
        }
    }

    /**
     * Deletes the current chat channel.
     * Stops the AI agent first, then deletes the channel.
     * Calls the provided callbacks to indicate success or failure.
     *
     * @param onSuccess Callback invoked when the channel is successfully deleted
     * @param onError Callback invoked when deletion fails, with the error details
     */
    fun deleteChannel(
        onSuccess: () -> Unit = {},
        onError: (Error) -> Unit = {},
    ) {
        val cid = cid.value ?: run {
            logger.d { "No channel to delete" }
            return
        }

        viewModelScope.launch {
            // Stop the AI agent first
            stopAIAgent(cid) {
                // Then delete the channel
                chatClient.channel(cid)
                    .delete()
                    .enqueue { result ->
                        result.onSuccess {
                            logger.d { "Channel deleted successfully: $cid" }
                            onSuccess()
                        }.onError { e ->
                            logger.e { "Failed to delete channel: ${e.message}" }
                            onError(e)
                        }
                    }
            }
        }
    }

    override fun onCleared() {
        cid.value?.let(::stopAIAgent)
        onDeviceModel.close()
    }

    private fun stopAIAgent(cid: String, onSuccess: () -> Unit = {}) {
        logger.d { "Stopping AI agent on channel: $cid" }
        MainScope().launch {
            chatAiRepository.stopAIAgent(cid)
                .onSuccess {
                    logger.d { "AI agent stopped successfully on channel: $cid" }
                    onSuccess()
                }
                .onFailure { e ->
                    logger.e { "Failed to stop AI agent: ${e.message}" }
                }
        }
    }
}

private fun StreamMessage.toChatMessage(currentUserId: String): ChatUiState.Message? {
    // Stream lifts `name` out of extraData, and a tool call's step needs it back.
    val parts = AIMessagePart.parts(attachments.map { it.type.orEmpty() to it.extraData + ("name" to it.name) })
    if (text.isBlank() && parts.isEmpty() && attachments.isEmpty()) {
        return null
    }

    val isFromCurrentUser = user.id == currentUserId
    val role = when {
        isFromAi() -> ChatUiState.Message.Role.Assistant
        isFromCurrentUser -> ChatUiState.Message.Role.User
        else -> ChatUiState.Message.Role.Other
    }

    return ChatUiState.Message(
        id = id,
        role = role,
        content = text,
        attachments = attachments,
        parts = parts,
        isGenerating = extraData["generating"] == true,
    )
}

private fun ChatUiState.Message.toTurn(): AIConversationTurn? = when (role) {
    ChatUiState.Message.Role.User -> AIConversationTurn.user(content)
    ChatUiState.Message.Role.Assistant -> AIConversationTurn.assistant(content)
    ChatUiState.Message.Role.Other -> null
}

private fun localMessage(
    role: ChatUiState.Message.Role,
    content: String,
    id: String = UUID.randomUUID().toString(),
    attachments: List<Attachment> = emptyList(),
    isGenerating: Boolean = false,
) = ChatUiState.Message(
    id = id,
    role = role,
    content = content,
    attachments = attachments,
    parts = emptyList(),
    isGenerating = isGenerating,
)

/** Shortest gap between two transcript updates while an answer streams, in milliseconds. */
private const val STREAM_FRAME_MS = 32L

private const val ON_DEVICE_INSTRUCTIONS =
    "You are a helpful assistant answering on the person's device, without a connection to the server. " +
        "Answer briefly. You have no tools."

private fun String.toAssistantState() = when (this) {
    "AI_STATE_THINKING" -> ChatUiState.AssistantState.Thinking
    // The "consulting tools / sources" state has two spellings in the wild: the reference
    // agents send AI_STATE_EXTERNAL_SOURCES, while the backend constant and some SDKs use
    // AI_STATE_CHECKING_SOURCES. ai_state is an unvalidated String, so accept both.
    "AI_STATE_EXTERNAL_SOURCES", "AI_STATE_CHECKING_SOURCES" -> ChatUiState.AssistantState.CheckingSources
    "AI_STATE_GENERATING" -> ChatUiState.AssistantState.Generating
    "AI_STATE_ERROR" -> ChatUiState.AssistantState.Error
    else -> ChatUiState.AssistantState.Idle
}
