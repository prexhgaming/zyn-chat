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

import com.squareup.moshi.Json
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

            // Only what the provider reported becomes a model; the catalog merely decorates it.
            return parsed.data
                .map { remote -> ModelCatalog.enrich(provider.id, remote.toReported()) }
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

/** Bare minimum of the OpenAI-compatible `/models` payload. */
private data class ModelsResponse(val data: List<RemoteModel> = emptyList())

/**
 * One entry of the `/models` payload.
 *
 * Only the fields the app understands are declared, and all of them are optional, because
 * OpenAI-compatible endpoints range from returning nothing but an id (Ollama, llama.cpp) to
 * returning full capability metadata (OpenRouter). Everything else in the payload is ignored.
 *
 * @param id Provider-side model identifier, e.g. `gpt-4o-mini`.
 * @param name Human-readable name, when the provider sends one.
 * @param contextLength Context window, when the provider reports one.
 * @param architecture What the model accepts, in OpenRouter's shape.
 * @param supportedParameters Parameters the model supports; `tools` here is how tool use is detected.
 * @param pricing Per-token pricing, used only to tell a free model from a paid one.
 */
private data class RemoteModel(
    val id: String = "",
    val name: String? = null,
    @Json(name = "context_length") val contextLength: Int? = null,
    val architecture: RemoteArchitecture? = null,
    @Json(name = "supported_parameters") val supportedParameters: List<String>? = null,
    val pricing: RemotePricing? = null,
)

/** OpenRouter-shaped description of what a model takes. */
private data class RemoteArchitecture(
    @Json(name = "input_modalities") val inputModalities: List<String>? = null,
    val modality: String? = null,
)

/** OpenRouter-shaped pricing, as strings in dollars per token. */
private data class RemotePricing(val prompt: String? = null)

/** Turns a provider entry into the raw facts the catalog understands. */
private fun RemoteModel.toReported(): ReportedModel = ReportedModel(
    slug = id,
    displayName = name,
    capabilities = capabilities(),
    isFree = pricing?.prompt?.let { prompt -> prompt.toDoubleOrNull() == FREE_PRICE },
)

/**
 * Reads the capabilities a provider stated, without inferring any it did not.
 *
 * `input_modalities` is preferred, with `modality` (`text+image->text`) as the fallback. Tool support
 * is taken from `supported_parameters` only when the provider sends that list at all — an endpoint
 * that stays silent leaves it unknown rather than being recorded as incapable.
 */
private fun RemoteModel.capabilities(): ModelCapabilities = ModelCapabilities(
    inputModalities = modalities(),
    supportsTools = supportedParameters?.let { parameters -> parameters.any { it == TOOLS_PARAMETER } },
    contextWindowTokens = contextLength,
)

/** What the model takes, or `null` when the provider did not say. */
private fun RemoteModel.modalities(): Set<Modality>? {
    architecture?.inputModalities?.let { reported ->
        return reported.mapNotNull { value -> Modality.of(value) }.toSet()
    }

    // "text+image->text" describes inputs before the arrow and outputs after it.
    return architecture?.modality
        ?.substringBefore(MODALITY_ARROW)
        ?.split(MODALITY_SEPARATOR)
        ?.mapNotNull { value -> Modality.of(value) }
        ?.toSet()
}

private const val MODELS_PATH = "/models"

/** Parameter a provider lists when a model can call tools. */
private const val TOOLS_PARAMETER = "tools"

private const val MODALITY_ARROW = "->"
private const val MODALITY_SEPARATOR = "+"

/** Providers that price a model report its prompt price as `"0"`. */
private const val FREE_PRICE = 0.0

/** How much of an error body is kept for logs. */
private const val DETAIL_LIMIT = 400
