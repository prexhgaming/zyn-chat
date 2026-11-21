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

package com.zyn.chat.app.ui.providers

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zyn.chat.app.R
import com.zyn.chat.app.data.providers.ProviderError

/**
 * Turns a [ProviderError] into one sentence the person can act on.
 *
 * Living in one place means the provider screen, the model picker and the chat screen all explain a
 * failure the same way, instead of each inventing its own wording for "your key is wrong".
 *
 * @param error The failure to describe.
 */
@Composable
public fun providerErrorText(error: ProviderError): String = when (error) {
    ProviderError.InvalidKey -> stringResource(R.string.zyn_provider_error_invalid_key)
    ProviderError.Forbidden -> stringResource(R.string.zyn_provider_error_forbidden)
    ProviderError.RateLimited -> stringResource(R.string.zyn_provider_error_rate_limited)
    ProviderError.ModelNotFound -> stringResource(R.string.zyn_provider_error_model_not_found)
    ProviderError.ContextTooLarge -> stringResource(R.string.zyn_provider_error_context_too_large)
    ProviderError.ProviderDown -> stringResource(R.string.zyn_provider_error_provider_down)
    ProviderError.NoConnection -> stringResource(R.string.zyn_provider_error_no_connection)
    ProviderError.BadResponse -> stringResource(R.string.zyn_provider_error_bad_response)
    ProviderError.Unknown -> stringResource(R.string.zyn_provider_error_unknown)
}
