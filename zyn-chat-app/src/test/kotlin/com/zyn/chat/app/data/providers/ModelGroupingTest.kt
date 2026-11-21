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

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelGroupingTest {
    private fun model(
        id: String,
        capabilities: ModelCapabilities = ModelCapabilities.UNKNOWN,
        isFree: Boolean = false,
    ): AiModel = AiModel(
        id = id,
        displayName = id,
        slug = id,
        providerId = "test",
        isFree = isFree,
        capabilities = capabilities,
    )

    @Test
    fun `a model that reads pictures is grouped as vision, even when it is also free`() {
        val vision = model("vision", capabilities = ModelCapabilities.textAndImages(), isFree = true)

        assertEquals(ModelGroup.Vision, groupOf(vision))
    }

    @Test
    fun `a tool-capable model is grouped as tools`() {
        val tools = model("tools", capabilities = ModelCapabilities.UNKNOWN.withToolsForTest())

        assertEquals(ModelGroup.Tools, groupOf(tools))
    }

    @Test
    fun `a free text-only model is grouped as free`() {
        val free = model("free", capabilities = ModelCapabilities.textOnly(), isFree = true)

        assertEquals(ModelGroup.Free, groupOf(free))
    }

    @Test
    fun `an unidentified model is grouped as other rather than guessed at`() {
        assertEquals(ModelGroup.Other, groupOf(model("mystery")))
    }

    @Test
    fun `nothing is claimed for an unknown model`() {
        assertEquals(emptyList<CapabilityToken>(), capabilityTokensOf(ModelCapabilities.UNKNOWN))
    }

    @Test
    fun `a text-only model reports text only`() {
        assertEquals(
            listOf(CapabilityToken.TextOnly),
            capabilityTokensOf(ModelCapabilities.textOnly()),
        )
    }

    @Test
    fun `pictures come before tools in the summary`() {
        val capabilities = ModelCapabilities.textAndImages().withToolsForTest()

        assertEquals(
            listOf(CapabilityToken.ReadsPictures, CapabilityToken.Tools),
            capabilityTokensOf(capabilities),
        )
    }

    @Test
    fun `the selected model is moved to its own leading section and not repeated`() {
        val sections = groupedModels(
            models = listOf(
                model("chosen", capabilities = ModelCapabilities.textAndImages()),
                model("other-vision", capabilities = ModelCapabilities.textAndImages()),
                model("unknown"),
            ),
            selectedModelId = "chosen",
        )

        assertEquals(ModelGroup.Selected, sections.first().first)
        assertEquals(listOf("chosen"), sections.first().second.map(AiModel::id))
        assertEquals(listOf("other-vision"), sections[1].second.map(AiModel::id))
    }

    @Test
    fun `sections are ordered by the group declaration, with empty ones left out`() {
        val sections = groupedModels(
            models = listOf(
                model("unknown"),
                model("free", capabilities = ModelCapabilities.textOnly(), isFree = true),
                model("tools", capabilities = ModelCapabilities.UNKNOWN.withToolsForTest()),
                model("vision", capabilities = ModelCapabilities.textAndImages()),
            ),
            selectedModelId = null,
        )

        assertEquals(
            listOf(ModelGroup.Vision, ModelGroup.Tools, ModelGroup.Free, ModelGroup.Other),
            sections.map { section -> section.first },
        )
    }

    @Test
    fun `no models produce no sections`() {
        assertEquals(emptyList<Pair<ModelGroup, List<AiModel>>>(), groupedModels(emptyList(), "x"))
    }
}

/** The provider-unknown record, with tool support added, for the grouping rules that read it. */
private fun ModelCapabilities.withToolsForTest(): ModelCapabilities = copy(tools = true)
