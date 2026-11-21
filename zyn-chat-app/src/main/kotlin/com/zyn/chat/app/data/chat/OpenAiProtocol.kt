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

package com.zyn.chat.app.data.chat

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.zyn.chat.app.domain.chat.ChatChunk
import com.zyn.chat.app.domain.chat.ChatRequest
import com.zyn.chat.app.domain.chat.ChatRole
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The OpenAI-compatible wire format: how a request is written and how a streamed chunk is read.
 *
 * Both directions live here so the engine above stays about transport (HTTP, streaming, errors)
 * rather than JSON. Only fields the app uses are modelled; unknown fields are ignored, which is what
 * lets one implementation serve providers that extend the format.
 *
 * @param moshi Moshi instance used for both directions.
 */
internal class OpenAiProtocol(moshi: Moshi) {

    private val requestAdapter = moshi.adapter(ChatCompletionRequest::class.java)
    private val chunkAdapter = moshi.adapter(ChunkPayload::class.java)

    /** Builds the request body; [stream] selects a streamed or single-shot answer. */
    fun requestBody(request: ChatRequest, stream: Boolean): RequestBody {
        val body = ChatCompletionRequest(
            model = request.model.slug,
            messages = request.messages.map { turn ->
                RequestMessage(role = turn.role.wireName(), content = turn.content)
            },
            stream = stream,
            maxTokens = request.maxOutputTokens,
        )
        return requestAdapter.toJson(body).toRequestBody(JSON_MEDIA_TYPE)
    }

    /** Reads one event's payload, or `null` when it carries no text, reasoning or usage. */
    fun parseChunk(json: String): ChatChunk? {
        val payload = runCatching { chunkAdapter.fromJson(json) }.getOrNull() ?: return null

        payload.usage?.let { usage ->
            return ChatChunk.Usage(
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
            )
        }

        val delta = payload.choices.firstOrNull()?.delta ?: return null
        val reasoning = delta.reasoningContent
        if (!reasoning.isNullOrEmpty()) return ChatChunk.Reasoning(reasoning)

        val content = delta.content
        return if (content.isNullOrEmpty()) null else ChatChunk.Text(content)
    }

    /** Whether an event's payload says the answer is over. */
    fun isFinished(json: String): Boolean {
        val payload = runCatching { chunkAdapter.fromJson(json) }.getOrNull() ?: return false
        return payload.choices.any { choice -> choice.finishReason != null }
    }
}

/** Role name the wire format uses. */
private fun ChatRole.wireName(): String = when (this) {
    ChatRole.System -> "system"
    ChatRole.User -> "user"
    ChatRole.Assistant -> "assistant"
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private data class ChatCompletionRequest(
    val model: String,
    val messages: List<RequestMessage>,
    val stream: Boolean,
    @Json(name = "max_tokens") val maxTokens: Int? = null,
)

private data class RequestMessage(val role: String, val content: String)

private data class ChunkPayload(
    val choices: List<ChunkChoice> = emptyList(),
    val usage: ChunkUsage? = null,
)

private data class ChunkChoice(
    val delta: ChunkDelta? = null,
    @Json(name = "finish_reason") val finishReason: String? = null,
)

private data class ChunkDelta(
    val content: String? = null,
    @Json(name = "reasoning_content") val reasoningContent: String? = null,
)

private data class ChunkUsage(
    @Json(name = "prompt_tokens") val promptTokens: Int = 0,
    @Json(name = "completion_tokens") val completionTokens: Int = 0,
)
