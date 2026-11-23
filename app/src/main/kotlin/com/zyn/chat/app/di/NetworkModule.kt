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

package com.zyn.chat.app.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Network module for providing network-related dependencies.
 * This module encapsulates the creation of Retrofit, OkHttp, and Moshi instances.
 */
public class NetworkModule {

    fun createMoshi(): Moshi =
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

    /**
     * Builds the HTTP client used for every outbound call, including the AI provider calls that
     * carry the person's own API key.
     *
     * Logging only ever runs when [enableLogging] is set (debug builds), and the credential headers
     * are always redacted: an unredacted `BODY` log would print `Authorization: Bearer sk-...` in
     * full, which would leak the key into logcat, bug reports and crash traces. Every provider in
     * this app authenticates with a header, never with a query parameter, so redacting these headers
     * is sufficient — do not add a provider that authenticates through the URL.
     *
     * @param enableLogging Whether to enable HTTP logging (debug builds only).
     */
    fun createOkHttpClient(enableLogging: Boolean = true): OkHttpClient =
        OkHttpClient.Builder()
            .apply {
                if (enableLogging) {
                    addInterceptor(createLoggingInterceptor())
                }
            }
            .build()

    private fun createLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            REDACTED_HEADERS.forEach(::redactHeader)
        }

    /**
     * Client for AI provider calls, which need far longer timeouts than the defaults: a reply can
     * take a while before the first token arrives, and a stream keeps the connection open.
     *
     * @param enableLogging Whether to enable HTTP logging (debug builds only).
     */
    fun createProviderHttpClient(enableLogging: Boolean = true): OkHttpClient =
        createOkHttpClient(enableLogging)
            .newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    fun createRetrofit(
        baseUrl: String,
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) {
            baseUrl
        } else {
            "$baseUrl/"
        }
        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}

/** Headers that carry credentials and must never appear in logs. */
private val REDACTED_HEADERS = listOf(
    "Authorization",
    "Proxy-Authorization",
    "x-api-key",
    "api-key",
    "x-goog-api-key",
)

/** Timeouts for provider calls: long enough for a slow model to answer. */
private const val CONNECT_TIMEOUT_SECONDS = 30L
private const val READ_TIMEOUT_SECONDS = 300L
private const val WRITE_TIMEOUT_SECONDS = 60L
