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

package com.zyn.chat.app.data.providers

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Talks to OpenAI-compatible provider endpoints.
 *
 * The key is passed in per call rather than held here, so this client is stateless and creates no
 * per-provider or per-conversation state that could leak between chats. It is the only place that
 * attaches a credential to a request, and the credential always travels in a header on the
 * provider's own host — never in the URL, where log redaction could not reach it.
 *
 * @param httpClient Client to use; supply the provider client from `NetworkModule` for its longer
 *   timeouts.
 * @param moshi Moshi instance used to parse provider responses.
 */
public class AiProviderClient(
    private val httpClient: OkHttpClient,
    private val moshi: Moshi,
) {

    private val modelsAdapter = moshi.adapter(ModelsResponse::class.java)

    /**
     * Lists the models a provider offers. This is also the key check: a wrong key fails here with
     * [ProviderError.InvalidKey] before anything is saved.
     *
     * @param provider Provider to ask, including its endpoint.
     * @param key The person's key, or `null` for providers that need none.
     * @return The models, or a failure carrying a [ProviderException].
     */
    public suspend fun listModels(
        provider: AiProvider,
        key: String?,
    ): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        try {
            Result.success(fetchModels(provider, key))
        } catch (e: ProviderException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(ProviderException(ProviderError.NoConnection, detail = e.message))
        }
    }

    private fun fetchModels(provider: AiProvider, key: String?): List<AiModel> {
        val request = buildRequest(provider.endpoint, provider.endpoint.modelsUrl(), key)

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ProviderException(
                    reason = providerErrorOf(response.code),
                    detail = body.take(DETAIL_LIMIT),
                    httpStatus = response.code,
                )
            }

            val parsed = runCatching { modelsAdapter.fromJson(body) }.getOrNull()
                ?: throw ProviderException(ProviderError.BadResponse, detail = body.take(DETAIL_LIMIT))

            return parsed.data
                .map { remote -> remote.toModel(provider.id) }
                .sortedBy { model -> model.slug.lowercase() }
        }
    }

    private fun buildRequest(endpoint: ProviderEndpoint, url: String, key: String?): Request {
        val httpUrl = url.toHttpUrlOrNull()
            ?: throw ProviderException(ProviderError.NoConnection, detail = "Malformed URL: $url")

        return Request.Builder()
            .url(httpUrl)
            .get()
            .header("Accept", "application/json")
            .apply {
                endpoint.auth.headerValue(key.orEmpty())
                    ?.takeIf { key != null }
                    ?.let { value -> header(endpoint.auth.headerName, value) }
            }
            .build()
    }
}

/** `/models`, appended to the provider's endpoint root. */
private fun ProviderEndpoint.modelsUrl(): String = "${baseUrl.trimEnd('/')}$MODELS_PATH"

/** Turns a provider's model entry into an app model, with a selection id that is unique per provider. */
private fun RemoteModel.toModel(providerId: String): AiModel = AiModel(
    id = modelKey(providerId, id),
    displayName = id,
    slug = id,
    providerId = providerId,
)

/** Bare minimum of the OpenAI-compatible `/models` payload. */
private data class ModelsResponse(val data: List<RemoteModel> = emptyList())

/**
 * One entry of the `/models` payload.
 *
 * @param id Provider-side model identifier, e.g. `gpt-4o-mini`.
 */
private data class RemoteModel(val id: String = "")

private const val MODELS_PATH = "/models"

/** How much of an error body is kept for logs. */
private const val DETAIL_LIMIT = 400
