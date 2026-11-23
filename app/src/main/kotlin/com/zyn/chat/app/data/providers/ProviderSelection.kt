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
 * Pure helpers for keeping the provider list and the selection consistent.
 *
 * They are kept out of [ProviderRepository] so the repository only holds state and mutation, and so
 * each rule below can be read and tested on its own.
 */

/** Replaces the provider with the same id, or appends it when it is new. */
internal fun List<AiProvider>.replacing(provider: AiProvider): List<AiProvider> {
    val index = indexOfFirst { it.id == provider.id }
    return if (index == -1) this + provider else toMutableList().apply { set(index, provider) }
}

/** Drops [providerId] from the list. */
internal fun List<AiProvider>.without(providerId: String): List<AiProvider> =
    filterNot { it.id == providerId }

/**
 * Id to select when [candidate] no longer points at an enabled provider: keeps the id when it is
 * still valid, otherwise falls back to the first enabled provider.
 */
internal fun List<AiProvider>.resolvedProviderId(candidate: String?): String? =
    candidate?.takeIf { id -> any { it.id == id && it.enabled } }
        ?: firstOrNull(AiProvider::enabled)?.id

/**
 * Drops a model selection that cannot be resolved any more, so the picker never shows a model that
 * belongs to a provider which was removed, disabled or unconfigured.
 */
internal fun ProvidersState.withValidSelection(): ProvidersState {
    val providerId = providers.resolvedProviderId(selectedProviderId)
    val modelId = selectedModelId?.takeIf { id -> allModels.any { it.id == id } }
    return if (providerId == selectedProviderId && modelId == selectedModelId) {
        this
    } else {
        copy(selectedProviderId = providerId, selectedModelId = modelId)
    }
}
