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

package com.zyn.chat.app

import android.content.Context
import com.squareup.moshi.Moshi
import com.zyn.chat.app.data.api.ChatAiApi
import com.zyn.chat.app.data.providers.AiProviderClient
import com.zyn.chat.app.data.providers.ProviderRepository
import com.zyn.chat.app.data.repository.ChatAiRepository
import com.zyn.chat.app.data.repository.ChatAiService
import com.zyn.chat.app.di.NetworkModule
import okhttp3.OkHttpClient

/**
 * Composition root for the chat feature: every dependency is built here so consumers stay simple and
 * there is exactly one HTTP client, one Moshi and one provider store in the app.
 *
 * `enableLogging` is passed in from `BuildConfig.DEBUG`; credential headers are redacted by
 * [NetworkModule] regardless, so no API key can reach a log.
 */
public class ChatDependencies(
    baseUrl: String,
    enableLogging: Boolean = true,
    networkModule: NetworkModule = NetworkModule(),
    context: Context,
) {

    /** Legacy path: starts and stops the AI agent on the Stream backend. */
    public val chatAiRepository: ChatAiRepository

    /** Where configured providers, their keys and the model selection live. */
    public val providerRepository: ProviderRepository

    /** Talks to provider endpoints. Stateless, so it is safe to share. */
    public val providerClient: AiProviderClient

    private val moshi: Moshi
    private val providerHttpClient: OkHttpClient

    init {
        moshi = networkModule.createMoshi()
        providerHttpClient = networkModule.createProviderHttpClient(enableLogging)

        val okHttpClient = networkModule.createOkHttpClient(enableLogging)
        val retrofit = networkModule.createRetrofit(
            baseUrl = baseUrl,
            okHttpClient = okHttpClient,
            moshi = moshi,
        )
        val chatAiApi = retrofit.create(ChatAiApi::class.java)
        chatAiRepository = ChatAiService(
            chatAiApi = chatAiApi,
            moshi = moshi,
        )

        providerRepository = ProviderRepository(context)
        providerClient = AiProviderClient(
            httpClient = providerHttpClient,
            moshi = moshi,
        )
    }
}
