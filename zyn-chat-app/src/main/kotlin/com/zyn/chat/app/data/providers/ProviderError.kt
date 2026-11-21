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

/**
 * Why a provider call failed, in terms the UI can turn into one clear sentence and one clear action.
 *
 * The mapping from HTTP status codes to these reasons lives in one place ([providerErrorOf]) so the
 * key validation, the model sync and the chat engine all report failures identically instead of each
 * inventing their own copy.
 */
public enum class ProviderError {
    /** No key stored, or the provider rejected the one it was given. */
    InvalidKey,

    /** The key is valid but the account cannot use this endpoint or model. */
    Forbidden,

    /** Too many requests, or the account is out of quota. */
    RateLimited,

    /** The model id is not offered by this provider. */
    ModelNotFound,

    /** The request is too large for the model's context window. */
    ContextTooLarge,

    /** The provider is reachable but failing. */
    ProviderDown,

    /** The device could not reach the provider at all. */
    NoConnection,

    /** The response could not be understood. */
    BadResponse,

    /** Anything else. */
    Unknown,
}

/**
 * A provider failure carrying its [reason], so callers can react without parsing messages.
 *
 * @param reason What went wrong.
 * @param detail Short technical detail for logs; never shown to the person verbatim.
 * @param httpStatus Status code when there was one.
 */
public class ProviderException(
    public val reason: ProviderError,
    public val detail: String? = null,
    public val httpStatus: Int? = null,
) : Exception(detail ?: reason.name)

/**
 * Maps an HTTP status code to a [ProviderError].
 *
 * @param status The response code.
 */
public fun providerErrorOf(status: Int): ProviderError = when (status) {
    UNAUTHORIZED -> ProviderError.InvalidKey
    FORBIDDEN -> ProviderError.Forbidden
    NOT_FOUND -> ProviderError.ModelNotFound
    REQUEST_TIMEOUT, PAYLOAD_TOO_LARGE -> ProviderError.ContextTooLarge
    TOO_MANY_REQUESTS -> ProviderError.RateLimited
    else -> if (status >= SERVER_ERROR_FLOOR) ProviderError.ProviderDown else ProviderError.Unknown
}

private const val UNAUTHORIZED = 401
private const val FORBIDDEN = 403
private const val NOT_FOUND = 404
private const val REQUEST_TIMEOUT = 408
private const val PAYLOAD_TOO_LARGE = 413
private const val TOO_MANY_REQUESTS = 429
private const val SERVER_ERROR_FLOOR = 500
