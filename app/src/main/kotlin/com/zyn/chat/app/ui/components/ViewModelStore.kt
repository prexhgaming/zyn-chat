/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-chat-android-ai/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zyn.chat.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Scopes the composable content to [store].
 *
 * The store belongs to the caller and is deliberately **not** cleared when this content leaves the
 * composition. The previous version created a store here and cleared it on dispose, which destroyed
 * the chat's ViewModel — and with it the running answer — the moment someone switched to another
 * conversation. Whoever owns the store decides when it is released.
 *
 * @param store Store whose ViewModels the content may obtain.
 * @param content The content to scope.
 */
@Composable
internal fun ProvideViewModelStore(
    store: ViewModelStore,
    content: @Composable () -> Unit,
) {
    val owner = remember(store) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}
