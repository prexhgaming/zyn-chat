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

/**
 * Remembers the models whose provider refused a picture, one of the two places a model's abilities can
 * come from.
 *
 * Most providers state what a model takes in their `/models` payload, and that statement is the truth
 * this app prefers. Many do not, though — a plain OpenAI-compatible endpoint returns nothing but ids —
 * and the app deliberately does not guess from the model's name, because the same model served by
 * different providers does not have to accept the same input. So when a provider answers a request that
 * carried a picture with its own refusal, that answer is recorded here, and the model is offered as
 * text-only afterwards.
 *
 * The record is per **provider and model**, never per model name alone, and it only fills in what the
 * provider never stated: a provider that reports it takes pictures keeps that report.
 *
 * @param context Any context; the application context is used.
 */
public class ImageAbilityStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /**
     * Whether a picture has already been refused for a model.
     *
     * @param providerId Provider that was asked.
     * @param modelId Model that was asked.
     */
    public fun refuses(providerId: String, modelId: String): Boolean =
        preferences.contains(key(providerId = providerId, modelId = modelId))

    /**
     * Records that a provider refused a picture for a model.
     *
     * @param providerId Provider that refused.
     * @param modelId Model that was refused.
     */
    public fun refuse(providerId: String, modelId: String) {
        preferences.edit().putBoolean(key(providerId = providerId, modelId = modelId), true).apply()
    }

    private fun key(providerId: String, modelId: String): String = "$providerId$SEPARATOR$modelId"

    private companion object {
        const val PREFERENCES_NAME = "zyn_image_abilities"
        const val SEPARATOR = ":"
    }
}

/**
 * The model with what its provider has already said about pictures folded in.
 *
 * A provider's own report always wins, and the remembered refusal only fills in what the provider left
 * unsaid, which is what [ModelCapabilities.orElse] does field by field.
 *
 * @param store Where refusals are remembered.
 * @param providerId Provider the model came from.
 */
internal fun AiModel.withLearnedAbilities(store: ImageAbilityStore, providerId: String): AiModel =
    withRefusedPictures(refused = store.refuses(providerId = providerId, modelId = id))

/**
 * The model with a refusal folded in.
 *
 * @param refused Whether the provider has already refused a picture for this model.
 */
internal fun AiModel.withRefusedPictures(refused: Boolean): AiModel =
    if (refused) {
        copy(capabilities = capabilities.orElse(ModelCapabilities.textOnly(contextWindowTokens)))
    } else {
        this
    }

/**
 * Whether a provider failure is its refusal of the pictures that were sent with the request.
 *
 * Providers say this in their own words, so this reads them: a client-side rejection whose body talks
 * about images, vision or modalities. A failure about a picture being too large is not a refusal — the
 * model can read pictures, that one was simply bigger than the endpoint accepts — so size complaints
 * are left out.
 */
internal fun ProviderException.refusedPictures(): Boolean {
    val body = detail.orEmpty().lowercase()
    val aboutPictures = PICTURE_WORDS.any(body::contains)
    val aboutSize = SIZE_WORDS.any(body::contains)
    return httpStatus in REFUSAL_STATUSES && aboutPictures && !aboutSize
}

/** Statuses providers use to reject the shape of a request they will not process. */
private val REFUSAL_STATUSES = setOf(400, 415, 422)

/** Words a provider uses when it is talking about picture input. */
private val PICTURE_WORDS = listOf("image", "vision", "modality", "multimodal")

/** Words that mark a complaint about the size of a picture rather than about pictures at all. */
private val SIZE_WORDS = listOf("too large", "too big", "size limit", "maximum size", "exceed")
