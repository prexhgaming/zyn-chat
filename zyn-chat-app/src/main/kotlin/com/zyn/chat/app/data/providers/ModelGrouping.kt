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
 * A section of the model picker.
 *
 * The order of the constants is the order the sections are shown in, so a reader can tell the
 * priority of a model's grouping from this declaration alone.
 */
public enum class ModelGroup {
    /** The model in use, pulled out so it is always a tap away. */
    Selected,

    /** Models that can be shown a picture. */
    Vision,

    /** Models that can be offered tools. */
    Tools,

    /** Models usable without the person's own key. */
    Free,

    /** Everything else, including models we know nothing about. */
    Other,
}

/**
 * The single section [model] belongs to.
 *
 * A model is listed once, under the first thing that distinguishes it, so a free model that reads
 * pictures is listed as [ModelGroup.Vision] rather than twice. Being unknown is never treated as a
 * claim in either direction: a model we cannot identify is grouped as [ModelGroup.Other].
 */
public fun groupOf(model: AiModel): ModelGroup = when {
    model.capabilities.acceptsImages == true -> ModelGroup.Vision
    model.capabilities.hasTools -> ModelGroup.Tools
    model.isFree -> ModelGroup.Free
    else -> ModelGroup.Other
}

/** One short fact about a model, shown on its row. */
public enum class CapabilityToken {
    ReadsPictures,
    TextOnly,
    Tools,
}

/**
 * The capability facts worth showing for [capabilities], in a stable order.
 *
 * Nothing is claimed for an ability that is unknown: a model we know nothing about produces an empty
 * list rather than a "text only" label it may not deserve.
 */
public fun capabilityTokensOf(capabilities: ModelCapabilities): List<CapabilityToken> = buildList {
    when (capabilities.acceptsImages) {
        true -> add(CapabilityToken.ReadsPictures)
        false -> add(CapabilityToken.TextOnly)
        null -> Unit
    }
    if (capabilities.hasTools) add(CapabilityToken.Tools)
}

/**
 * [models] split into the sections the picker draws, in [ModelGroup] order with empty sections left
 * out.
 *
 * The selected model is moved into the leading [ModelGroup.Selected] section and removed from the
 * others, so it appears exactly once and is never scrolled out of reach.
 */
public fun groupedModels(
    models: List<AiModel>,
    selectedModelId: String?,
): List<Pair<ModelGroup, List<AiModel>>> {
    val selected = models.filter { model -> model.id == selectedModelId }
    val remaining = models.filterNot { model -> model.id == selectedModelId }

    return buildList {
        if (selected.isNotEmpty()) add(ModelGroup.Selected to selected)
        ModelGroup.entries
            .filterNot { group -> group == ModelGroup.Selected }
            .forEach { group ->
                val members = remaining.filter { model -> groupOf(model) == group }
                if (members.isNotEmpty()) add(group to members)
            }
    }
}
