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
 * A provider the person can pick when adding their own API key.
 *
 * The catalog is the list of **known** endpoints so nobody has to type a base URL. Nothing here is
 * connected or reachable until the person adds their own key, and no entry implies that a provider
 * is already set up: [AiProvider] instances are only created from an explicit user action.
 *
 * `baseUrl` values are endpoint roots that serve the OpenAI-compatible `/models` and
 * `/chat/completions` paths. The validation step (a real `GET /models` with the person's key) is the
 * safety net when a provider changes its URL — prefer fixing the entry here over special-casing a
 * provider in the client.
 *
 * @param id Stable identifier, also used as the key store's account name.
 * @param displayName Name shown in the catalog and on the provider card.
 * @param baseUrl OpenAI-compatible endpoint root.
 * @param auth How the key is sent.
 * @param kind Where the provider runs, used to group the catalog.
 * @param keyRequired Whether a key is needed. Local servers usually need none.
 * @param supported Whether this app can talk to the provider. Known endpoints that speak a different
 *   dialect stay listed (so the catalog is honest) but cannot be added.
 * @param editableBaseUrl Whether the person may change [baseUrl], needed for local servers and for
 *   anything not on the known list.
 * @param unsupportedReason Copy explaining why an unsupported provider cannot be added.
 * @param docsUrl Where the person gets a key.
 */
public data class KnownProvider(
    public val id: String,
    public val displayName: String,
    public val baseUrl: String,
    public val auth: ProviderAuth,
    public val kind: ProviderKind,
    public val keyRequired: Boolean = true,
    public val supported: Boolean = true,
    public val editableBaseUrl: Boolean = false,
    public val unsupportedReason: String? = null,
    public val docsUrl: String? = null,
)

/**
 * The known OpenAI-compatible providers.
 *
 * Anthropic is listed but not supported: its API uses the `/messages` dialect rather than the
 * OpenAI-compatible `/chat/completions`, and pretending otherwise would fail at request time.
 */
public object ProviderCatalog {

    /** Identifier of the hand-configured provider. */
    public const val CUSTOM_ID: String = "custom"

    /** Every entry offered to the person, in display order. */
    public val all: List<KnownProvider> = listOf(
        cloud("openai", "OpenAI", "https://api.openai.com/v1", "https://platform.openai.com/api-keys"),
        cloud("groq", "Groq", "https://api.groq.com/openai/v1", "https://console.groq.com/keys"),
        cloud("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "https://platform.deepseek.com"),
        cloud("mistral", "Mistral AI", "https://api.mistral.ai/v1", "https://console.mistral.ai"),
        cloud("xai", "xAI (Grok)", "https://api.x.ai/v1", "https://console.x.ai"),
        cloud("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "https://openrouter.ai/keys"),
        cloud("together", "Together AI", "https://api.together.xyz/v1", "https://api.together.ai/settings/api-keys"),
        cloud("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference/v1", "https://fireworks.ai/account/api-keys"),
        cloud("perplexity", "Perplexity", "https://api.perplexity.ai", "https://www.perplexity.ai/settings/api"),
        cloud("cerebras", "Cerebras", "https://api.cerebras.ai/v1", "https://cloud.cerebras.ai"),
        cloud("deepinfra", "DeepInfra", "https://api.deepinfra.com/v1/openai", "https://deepinfra.com/dash/api_keys"),
        cloud("moonshot", "Moonshot (Kimi)", "https://api.moonshot.ai/v1", "https://platform.moonshot.ai"),
        cloud("zai", "Z.ai (GLM)", "https://api.z.ai/api/paas/v4", "https://z.ai/manage-apikey/apikey-list"),
        cloud("minimax", "MiniMax", "https://api.minimax.io/v1", "https://platform.minimax.io"),
        cloud("github", "GitHub Models", "https://models.github.ai/inference", "https://github.com/settings/tokens"),
        KnownProvider(
            id = "gemini",
            displayName = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
            auth = ProviderAuth.Bearer,
            kind = ProviderKind.Cloud,
            docsUrl = "https://aistudio.google.com/apikey",
        ),
        KnownProvider(
            id = "anthropic",
            displayName = "Anthropic (Claude)",
            baseUrl = "https://api.anthropic.com/v1",
            auth = ProviderAuth.ApiKeyHeader,
            kind = ProviderKind.Cloud,
            supported = false,
            unsupportedReason = "Anthropic speaks its own /messages API, not the " +
                "OpenAI-compatible one. Not supported yet.",
            docsUrl = "https://console.anthropic.com/settings/keys",
        ),
        local("ollama", "Ollama", "http://localhost:11434/v1"),
        local("lmstudio", "LM Studio", "http://localhost:1234/v1"),
        local("llamacpp", "llama.cpp server", "http://localhost:8080/v1"),
        KnownProvider(
            id = CUSTOM_ID,
            displayName = "Custom endpoint",
            baseUrl = "",
            auth = ProviderAuth.Bearer,
            kind = ProviderKind.Custom,
            editableBaseUrl = true,
        ),
    )

    /** Looks an entry up by id. */
    public fun byId(id: String): KnownProvider? = all.firstOrNull { it.id == id }

    /**
     * Entries matching [query] by name or id, in catalog order. An empty query returns everything.
     *
     * @param query Text the person typed in the catalog search field.
     */
    public fun search(query: String): List<KnownProvider> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return all
        return all.filter { provider ->
            provider.displayName.contains(trimmed, ignoreCase = true) ||
                provider.id.contains(trimmed, ignoreCase = true)
        }
    }

    private fun cloud(
        id: String,
        displayName: String,
        baseUrl: String,
        docsUrl: String,
    ): KnownProvider = KnownProvider(
        id = id,
        displayName = displayName,
        baseUrl = baseUrl,
        auth = ProviderAuth.Bearer,
        kind = ProviderKind.Cloud,
        docsUrl = docsUrl,
    )

    private fun local(
        id: String,
        displayName: String,
        baseUrl: String,
    ): KnownProvider = KnownProvider(
        id = id,
        displayName = displayName,
        baseUrl = baseUrl,
        auth = ProviderAuth.None,
        kind = ProviderKind.Local,
        keyRequired = false,
        editableBaseUrl = true,
    )
}
