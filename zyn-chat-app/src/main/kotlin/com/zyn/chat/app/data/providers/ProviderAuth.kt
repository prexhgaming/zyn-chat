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
 * How a provider expects its API key to be sent.
 *
 * Every style here uses a **header**. A key must never travel in the URL: it would end up in
 * `HttpLoggingInterceptor` output, in `okhttp` tags and in any proxy log, where header redaction
 * cannot reach it.
 */
public enum class ProviderAuth(
    /** Header the key is sent in. */
    public val headerName: String,
) {
    /** `Authorization: Bearer <key>` — used by every OpenAI-compatible provider. */
    Bearer("Authorization"),

    /** `<x-api-key>: <key>` — used by providers that predate the OpenAI-compatible convention. */
    ApiKeyHeader("x-api-key"),

    /** No key at all: local servers that run on the person's own machine or network. */
    None(""),
    ;

    /** The header value for [key], or `null` when this style does not send a key. */
    public fun headerValue(key: String): String? = when (this) {
        Bearer -> "Bearer $key"
        ApiKeyHeader -> key
        None -> null
    }
}

/** Where a provider runs, used to group the catalog when adding a provider. */
public enum class ProviderKind {
    /** A hosted API reachable over the internet. */
    Cloud,

    /** A server the person runs themselves, usually on the local network. */
    Local,

    /** Anything else, described by hand. */
    Custom,
}
