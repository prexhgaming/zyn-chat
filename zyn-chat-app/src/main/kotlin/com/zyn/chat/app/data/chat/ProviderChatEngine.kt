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

import com.squareup.moshi.Moshi
import com.zyn.chat.app.data.providers.ProviderError
import com.zyn.chat.app.data.providers.ProviderException
import com.zyn.chat.app.data.providers.providerErrorOf
import com.zyn.chat.app.domain.chat.ChatChunk
import com.zyn.chat.app.domain.chat.ChatEngine
import com.zyn.chat.app.domain.chat.ChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Answers from the person's own provider, over the OpenAI-compatible `/chat/completions` endpoint.
 *
 * The engine is stateless and creates nothing per conversation: the key is passed in with each
 * request, the conversation arrives inside [ChatRequest], and the call lasts only as long as the
 * flow is collected. Cancelling the collection (leaving the chat, tapping stop) cancels the HTTP call,
 * so an answer can never land in a conversation other than the one that asked for it.
 *
 * Failures are raised as [ProviderException] with a reason, so the UI can explain what happened
 * instead of showing a raw exception.
 *
 * @param httpClient Client to use; supply the long-timeout provider client from `NetworkModule`.
 * @param moshi Moshi instance for the wire format.
 */
public class ProviderChatEngine(
    private val httpClient: OkHttpClient,
    private val moshi: Moshi,
) : ChatEngine {

    private val protocol = OpenAiProtocol(moshi)

    override fun stream(request: ChatRequest): Flow<ChatChunk> = flow {
        val response = try {
            execute(request)
        } catch (e: IOException) {
            throw ProviderException(ProviderError.NoConnection, detail = e.message)
        }

        response.use { streamed ->
            if (!streamed.isSuccessful) throw failureOf(streamed)

            val body = streamed.body ?: throw ProviderException(ProviderError.BadResponse)
            val reader = SseReader(body.source())

            while (true) {
                val event = reader.next() ?: break
                if (event == DONE_PAYLOAD) {
                    emit(ChatChunk.Done)
                    return@flow
                }
                protocol.parseChunk(event)?.let { chunk -> emit(chunk) }
                if (protocol.isFinished(event)) {
                    emit(ChatChunk.Done)
                    return@flow
                }
            }
            emit(ChatChunk.Done)
        }
    }.flowOn(Dispatchers.IO)

    /** Opens the streaming request. */
    private fun execute(request: ChatRequest): Response {
        val endpoint = request.provider.endpoint
        val url = "${endpoint.baseUrl.trimEnd('/')}$COMPLETIONS_PATH".toHttpUrlOrNull()
            ?: throw ProviderException(ProviderError.NoConnection, detail = "Malformed provider URL")

        val builder = Request.Builder()
            .url(url)
            .post(protocol.requestBody(request, stream = true))
            .header("Accept", ACCEPT_EVENTS)

        val key = request.apiKey
        endpoint.auth.headerValue(key.orEmpty())
            ?.takeIf { key != null }
            ?.let { value -> builder.header(endpoint.auth.headerName, value) }

        return httpClient.newCall(builder.build()).execute()
    }

    /** Turns an HTTP failure into a reportable reason, keeping a little of the body for the log. */
    private fun failureOf(response: Response): ProviderException {
        val detail = runCatching {
            response.body?.string().orEmpty().take(DETAIL_LIMIT)
        }.getOrDefault("")

        return ProviderException(
            reason = providerErrorOf(response.code),
            detail = detail,
            httpStatus = response.code,
        )
    }
}

private const val COMPLETIONS_PATH = "/chat/completions"
private const val ACCEPT_EVENTS = "text/event-stream"
private const val DONE_PAYLOAD = "[DONE]"
private const val DETAIL_LIMIT = 400
