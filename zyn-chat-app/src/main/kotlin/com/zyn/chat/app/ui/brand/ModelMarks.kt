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

package com.zyn.chat.app.ui.brand

import androidx.compose.ui.graphics.vector.ImageVector
import com.zyn.chat.app.data.providers.ModelBrand
import com.zyn.chat.app.ui.icons.ZynIcons

/**
 * The glyph drawn inside a brand badge.
 *
 * The marks are the app's own geometric drawings (see `ZynIcons`), not redistributed brand artwork.
 *
 * @param brand Family to draw a mark for.
 * @return The mark, or `null` for [ModelBrand.Generic] so [com.zyn.chat.app.ui.components.BrandMark]
 *   falls back to initials instead of showing a meaningless logo.
 */
public fun markOf(brand: ModelBrand): ImageVector? = when (brand) {
    ModelBrand.OpenAI -> ZynIcons.BrandOpenAI
    ModelBrand.Anthropic -> ZynIcons.BrandClaude
    ModelBrand.Google -> ZynIcons.BrandGoogle
    ModelBrand.Meta -> ZynIcons.BrandLlama
    ModelBrand.Mistral -> ZynIcons.BrandMistral
    ModelBrand.DeepSeek -> ZynIcons.BrandDeepSeek
    ModelBrand.Qwen -> ZynIcons.BrandQwen
    ModelBrand.XAI -> ZynIcons.BrandXAI
    ModelBrand.Microsoft -> ZynIcons.BrandMicrosoft
    ModelBrand.Generic -> null
}
