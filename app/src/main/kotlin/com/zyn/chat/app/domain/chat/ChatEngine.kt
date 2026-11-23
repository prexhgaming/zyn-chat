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

package com.zyn.chat.app.domain.chat

import com.zyn.chat.app.data.providers.AiModel
import com.zyn.chat.app.data.providers.AiProvider
import kotlinx.coroutines.flow.Flow

/** Who said a turn in a request. */
public enum class ChatRole {
    /** Instructions for the model; never shown in the conversation. */
    System,

    /** Something the person wrote. */
    User,

    /** Something the model answered. */
    Assistant,
}

/**
 * One message of a request.
 *
 * @param role Who said it.
 * @param content Its text.
 * @param images Pictures to send with it, in the order they were attached.
 */
public data class ChatTurn(
    public val role: ChatRole,
    public val content: String,
    public val images: List<ChatImage> = emptyList(),
)

/**
 * A picture carried with a request.
 *
 * Pictures travel inline: the bytes are held base64-encoded and written into a `data:` URL when the
 * request is built, which is the one form every OpenAI-compatible endpoint takes without a separate
 * upload step of its own. Encoding happens where the picture is read (see `data/chat/ImagePayload.kt`),
 * so the engine and the wire format stay free of image handling.
 *
 * @param mimeType Media type of the encoded picture, e.g. `image/jpeg`.
 * @param base64 The picture's bytes, base64-encoded.
 */
public data class ChatImage(
    public val mimeType: String,
    public val base64: String,
)

/**
 * Everything one answer needs.
 *
 * The request carries its own [messages], built from a single conversation, so an engine is
 * stateless: nothing is remembered between calls and no conversation can see another's history.
 *
 * @param conversationId Conversation this belongs to; used for cancellation and logging, never sent.
 * @param provider Provider to call.
 * @param model Model to call it with.
 * @param apiKey The person's key, or `null` when the provider needs none.
 * @param systemPrompt Instructions put in front of the conversation.
 * @param messages The conversation so far, oldest first.
 * @param maxOutputTokens Ceiling for the answer, or `null` to let the provider decide.
 */
public data class ChatRequest(
    public val conversationId: String,
    public val provider: AiProvider,
    public val model: AiModel,
    public val apiKey: String?,
    public val systemPrompt: String?,
    public val messages: List<ChatTurn>,
    public val maxOutputTokens: Int? = null,
)

/** What arrives while an answer is being produced. */
public sealed interface ChatChunk {
    /** More text of the answer. */
    public data class Text(public val delta: String) : ChatChunk

    /** More of the model's reasoning, when it reports one. */
    public data class Reasoning(public val delta: String) : ChatChunk

    /** Token counts the provider reported for this exchange. */
    public data class Usage(public val promptTokens: Int, public val completionTokens: Int) : ChatChunk

    /** The answer is complete. */
    public data object Done : ChatChunk
}

/**
 * Produces answers from an AI provider.
 *
 * This is the seam between the chat UI and the transport: the UI knows only this interface, so the
 * app can talk to a provider directly (bring-your-own-key) without the screens changing. Engines must
 * be stateless — a request carries its whole conversation — and must fail with
 * `ProviderException` so the UI can explain the failure.
 */
public interface ChatEngine {

    /**
     * Streams an answer.
     *
     * @param request What to ask, including the whole conversation.
     * @return Chunks in arrival order, ending with [ChatChunk.Done] on success.
     */
    public fun stream(request: ChatRequest): Flow<ChatChunk>
}
