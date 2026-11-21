package com.zyn.chat.app.ui.chat

/**
 * A model the person can pick in the composer.
 *
 * Presentation only: this phase wires the selector to local state, so choosing a model keeps the
 * choice in the UI. Provider credentials and request routing are not implemented yet.
 *
 * @param id Stable identifier of the model.
 * @param name Name shown in the composer pill and in the selector.
 * @param provider Provider or runtime the model belongs to.
 * @param description Short hint shown under the name in the selector.
 */
public data class ChatModel(
    public val id: String,
    public val name: String,
    public val provider: String,
    public val description: String,
)

/**
 * Models offered by the model selector.
 *
 * Replace with the models resolved from the person's own API keys once provider management lands.
 */
public val AvailableModels: List<ChatModel> = listOf(
    ChatModel(
        id = "deepseek-v4.1-flash",
        name = "Deepseek V4.1 Flash",
        provider = "DeepSeek",
        description = "Fast and inexpensive",
    ),
    ChatModel(
        id = "deepseek-v4.1",
        name = "Deepseek V4.1",
        provider = "DeepSeek",
        description = "Balanced reasoning",
    ),
    ChatModel(
        id = "gpt-4.1-mini",
        name = "GPT-4.1 mini",
        provider = "OpenAI",
        description = "Quick everyday answers",
    ),
    ChatModel(
        id = "claude-sonnet-4",
        name = "Claude Sonnet 4",
        provider = "Anthropic",
        description = "Long-form writing and code",
    ),
    ChatModel(
        id = "on-device",
        name = "On-device model",
        provider = "This device",
        description = "Runs locally, stays private",
    ),
)

/** Model selected when the person has not picked one yet. */
public val DefaultModel: ChatModel = AvailableModels.first()
