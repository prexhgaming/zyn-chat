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

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Persists the configured providers and the current selection.
 *
 * Providers are stored as JSON in `SharedPreferences`: the list is small, it is read once at
 * startup and rewritten on every change, so a file or a database would add moving parts without
 * buying anything. API keys are deliberately **not** stored here — they live in [ApiKeyStore],
 * encrypted by the Android Keystore.
 *
 * A corrupt or unreadable payload is treated as "nothing configured" and cleared, so a bad write can
 * never leave the app unable to start.
 *
 * @param context Any context; the application context is used.
 */
internal class ProviderStorage(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /**
     * Built on first use, never at construction time.
     *
     * It is also allowed to fail: this runs on the startup path, and a serialiser that cannot be
     * built must degrade to "nothing configured" rather than take the whole app down.
     */
    private val providersAdapter: JsonAdapter<List<AiProvider>>? by lazy {
        runCatching {
            Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
                .adapter<List<AiProvider>>(
                    Types.newParameterizedType(List::class.java, AiProvider::class.java),
                )
        }.getOrNull()
    }

    /** Reads the configured providers, or an empty list when nothing is stored. */
    fun loadProviders(): List<AiProvider> {
        val json = preferences.getString(KEY_PROVIDERS, null) ?: return emptyList()
        val serializer = providersAdapter ?: return emptyList()
        return runCatching { serializer.fromJson(json).orEmpty() }
            .getOrElse {
                preferences.edit().remove(KEY_PROVIDERS).apply()
                emptyList()
            }
    }

    /** Stores the configured providers. */
    fun saveProviders(providers: List<AiProvider>) {
        if (providers.isEmpty()) {
            preferences.edit().remove(KEY_PROVIDERS).apply()
            return
        }
        val serializer = providersAdapter ?: return
        val json = runCatching { serializer.toJson(providers) }.getOrNull() ?: return
        preferences.edit().putString(KEY_PROVIDERS, json).apply()
    }

    /** Reads the id of the provider the picker was last showing. */
    fun loadProviderId(): String? = preferences.getString(KEY_PROVIDER_ID, null)

    /** Reads the id of the model the person last selected. */
    fun loadModelId(): String? = preferences.getString(KEY_MODEL_ID, null)

    /** Stores the current selection. */
    fun saveSelection(providerId: String?, modelId: String?) {
        preferences.edit()
            .putString(KEY_PROVIDER_ID, providerId)
            .putString(KEY_MODEL_ID, modelId)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "zyn_providers"
        const val KEY_PROVIDERS = "providers"
        const val KEY_PROVIDER_ID = "selected_provider_id"
        const val KEY_MODEL_ID = "selected_model_id"
    }
}
