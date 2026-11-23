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
 * Everything needed to reach a provider over HTTP.
 *
 * @param baseUrl OpenAI-compatible endpoint root, e.g. `https://api.openai.com/v1`.
 * @param auth How the key is sent.
 * @param kind Where the provider runs.
 * @param keyRequired Whether a key is needed to call it.
 * @param editableBaseUrl Whether the person may change [baseUrl]; true for local servers and custom
 *   endpoints, which are reachable on addresses only the person knows.
 * @param docsUrl Where the person gets a key.
 */
public data class ProviderEndpoint(
    public val baseUrl: String,
    public val auth: ProviderAuth,
    public val kind: ProviderKind = ProviderKind.Cloud,
    public val keyRequired: Boolean = true,
    public val editableBaseUrl: Boolean = false,
    public val docsUrl: String? = null,
) {
    /** Whether the provider runs on the person's own machine or network. */
    public val isLocal: Boolean get() = kind == ProviderKind.Local
}

/**
 * The part of a provider that changes as it is used, as opposed to how it is reached.
 *
 * @param enabled Whether the provider takes part in model selection.
 * @param keyHint Masked key fragment kept only as confirmation that a key is saved; the key itself
 *   lives in [ApiKeyStore] and never reaches the UI.
 * @param lastSyncedAt When the model list was last refreshed, or `null` if never.
 */
public data class ProviderConnection(
    public val enabled: Boolean = true,
    public val keyHint: String? = null,
    public val lastSyncedAt: Long? = null,
) {
    /** Whether a key still has to be added before the provider can be called. */
    public val needsKey: Boolean get() = keyHint == null
}

/**
 * A provider the person has configured, together with the models it offers.
 *
 * Instances only exist after an explicit user action (adding a provider from the catalog). The
 * application never creates one on its own, so an empty list means "nothing configured yet" rather
 * than "not loaded yet".
 *
 * @param id Stable identifier, also the key store account name.
 * @param name Name shown in tabs, cards and the picker.
 * @param endpoint How to reach it.
 * @param connection Whether it is enabled and whether its key is in place.
 * @param models Models it offers, in display order.
 */
public data class AiProvider(
    public val id: String,
    public val name: String,
    public val endpoint: ProviderEndpoint,
    public val connection: ProviderConnection = ProviderConnection(),
    public val models: List<AiModel> = emptyList(),
) {
    /** Whether the provider is enabled. */
    public val enabled: Boolean get() = connection.enabled

    /** Whether the provider is enabled and has whatever credentials it needs. */
    public val isCallable: Boolean
        get() = connection.enabled && (!endpoint.keyRequired || connection.keyHint != null)
}

/**
 * The complete provider configuration and the current selection.
 *
 * @param providers Every configured provider, in tab order.
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

    /** The provider that owns [selectedModel]. */
    public val selectedProvider: AiProvider?
        get() = selectedModel?.let { model -> providers.firstOrNull { it.id == model.providerId } }

    /** Whether anything is configured at all. */
    public val isEmpty: Boolean get() = providers.isEmpty()
}
