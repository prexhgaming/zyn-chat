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
 * An AI provider that owns a collection of [AiModel]s.
 *
 * Providers are registered at runtime; the application ships with none configured, so nothing shows
 * a provider or a model that the person has not actually set up.
 *
 * @param id Stable identifier.
 * @param name Name shown in the picker tabs, e.g. "PolyDev AI".
 * @param models Models this provider offers, in display order.
 * @param enabled Whether the provider takes part in selection. Disabled providers stay registered
 *   but their models are not offered.
 */
public data class AiProvider(
    public val id: String,
    public val name: String,
    public val models: List<AiModel> = emptyList(),
    public val enabled: Boolean = true,
)

/**
 * The complete provider configuration and the current selection.
 *
 * @param providers Every registered provider, in tab order.
 * @param selectedProviderId Provider whose models the picker is showing.
 * @param selectedModelId Model the person selected, if any.
 */
public data class ProvidersState(
    public val providers: List<AiProvider> = emptyList(),
    public val selectedProviderId: String? = null,
    public val selectedModelId: String? = null,
) {
    /** Providers that are enabled and can be offered. */
    public val activeProviders: List<AiProvider> get() = providers.filter(AiProvider::enabled)

    /** Every model across the enabled providers, in provider order. */
    public val allModels: List<AiModel> get() = activeProviders.flatMap(AiProvider::models)

    /** The provider the picker is showing, falling back to the first enabled one. */
    public val currentProvider: AiProvider?
        get() = providers.firstOrNull { it.id == selectedProviderId && it.enabled }
            ?: activeProviders.firstOrNull()

    /** Models belonging to [currentProvider]. */
    public val modelsOfCurrentProvider: List<AiModel> get() = currentProvider?.models.orEmpty()

    /** The selected model, or `null` when nothing is selected or the selection is gone. */
    public val selectedModel: AiModel?
        get() = allModels.firstOrNull { it.id == selectedModelId }
}
