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

package com.zyn.chat.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyn.chat.app.ChatDependencies
import com.zyn.chat.app.data.providers.ProviderRepository
import com.zyn.chat.app.di.ConversationListViewModelFactory
import com.zyn.chat.app.presentation.conversations.ConversationListViewModel
import com.zyn.chat.app.presentation.providers.ProvidersViewModel
import com.zyn.chat.app.presentation.providers.ProvidersViewModelFactory
import com.zyn.chat.app.ui.chat.ChatScreen
import com.zyn.chat.app.ui.chat.ModelPickerSheet
import com.zyn.chat.app.ui.components.ChatDrawer
import com.zyn.chat.app.ui.components.ViewModelStore
import io.getstream.chat.android.client.ChatClient
import kotlinx.coroutines.launch

/**
 * Top-level chat app composable.
 *
 * Hosts a standard Android-style navigation drawer: the sheet covers a fraction of the screen, the
 * chat stays visible behind an animated scrim, and the drawer can be opened by swiping in from the
 * left edge, closed by swiping back, by tapping outside, or with the system back button.
 *
 * Provider configuration and the model selection are owned here, so the drawer, the model picker
 * sheet and the composer all read one state and call one set of actions.
 */
@Composable
public fun AiChatApp(
    chatDependencies: ChatDependencies,
    modifier: Modifier = Modifier,
) {
    val conversationListViewModel = viewModel<ConversationListViewModel>(
        factory = ConversationListViewModelFactory(),
    )
    val conversationListState by conversationListViewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val user by ChatClient.instance().clientState.user.collectAsState()

    val appContext = LocalContext.current.applicationContext
    val providerRepository = remember(appContext) { ProviderRepository(appContext) }
    val providersViewModel = viewModel<ProvidersViewModel>(
        factory = ProvidersViewModelFactory(providerRepository),
    )
    val providersState by providersViewModel.uiState.collectAsState()

    var selectedConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var showModelPicker by rememberSaveable { mutableStateOf(false) }

    /**
     * Revision counter for new chats. Incremented each time "New chat" is clicked.
     * This ensures AnimatedContent detects a state change even when already on a new chat (null -> null).
     */
    var newChatRevision by rememberSaveable { mutableIntStateOf(0) }

    /**
     * Provider management, shared by the drawer's "Providers" entry and the "Providers" action in
     * the model picker header. Provider and key management arrive with the integrations, so both
     * entry points currently route here and do nothing else.
     */
    val onManageProviders: () -> Unit = {
        showModelPicker = false
    }

    // Hide the keyboard when the drawer opens.
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.isOpen) {
            keyboardController?.hide()
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val drawerWidth = rememberDrawerWidth()

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = DRAWER_SCRIM_ALPHA),
        drawerContent = {
            Surface(
                modifier = Modifier
                    .width(drawerWidth)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                ChatDrawer(
                    user = user,
                    conversations = conversationListState.conversations,
                    selectedConversationId = selectedConversationId,
                    onNewChatClick = {
                        selectedConversationId = null
                        newChatRevision++
                        scope.launch { drawerState.close() }
                    },
                    onConversationClick = { conversationId ->
                        selectedConversationId = conversationId
                        scope.launch { drawerState.close() }
                    },
                    onUserInfoClick = {
                        // Profile is presentation only in this phase.
                        scope.launch { drawerState.close() }
                    },
                    onProvidersClick = {
                        scope.launch { drawerState.close() }
                        onManageProviders()
                    },
                    onRoutingClick = { scope.launch { drawerState.close() } },
                    onSearchClick = { },
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // A combined key for AnimatedContent: conversationId for real chats, revision for new chats.
            // This ensures that clicking "New chat" when already on a new chat triggers a state change.
            val navigationKey = selectedConversationId ?: "new-chat-$newChatRevision"

            AnimatedContent(
                targetState = navigationKey,
            ) { navigationKey ->
                ViewModelStore(navigationKey) {
                    ChatScreen(
                        modifier = Modifier.fillMaxSize(),
                        conversationId = selectedConversationId,
                        chatDependencies = chatDependencies,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNewChatClick = {
                            selectedConversationId = null
                            newChatRevision++
                        },
                        onChatDeleted = {
                            // Navigate back to new chat after deletion.
                            selectedConversationId = null
                            newChatRevision++
                        },
                        modelName = providersState.selectedModel?.displayName,
                        onModelClick = { showModelPicker = true },
                    )
                }
            }

            if (showModelPicker) {
                ModelPickerSheet(
                    state = providersState,
                    onProviderSelected = providersViewModel::selectProvider,
                    onModelSelected = { modelId ->
                        providersViewModel.selectModel(modelId)
                        showModelPicker = false
                    },
                    onManageProviders = onManageProviders,
                    onDismiss = { showModelPicker = false },
                )
            }

            // The drawer sheet sits off-screen when closed, so its own drag handle is not hittable.
            // This narrow strip restores the platform swipe-from-edge gesture for opening it.
            if (!drawerState.isOpen) {
                DrawerEdgeSwipe(
                    onSwipe = {
                        keyboardController?.hide()
                        scope.launch { drawerState.open() }
                    },
                )
            }
        }
    }
}

/** Width of the touch strip along the screen edge that opens the drawer. */
private val DrawerEdgeWidth = 22.dp

/** Scrim opacity while the drawer is open. */
private const val DRAWER_SCRIM_ALPHA = 0.32f

/** Fraction of the screen width the drawer covers at most. */
private const val DRAWER_WIDTH_FRACTION = 0.84f

/** Ceiling for the drawer width, so it stays a side sheet on tablets. */
private val DrawerMaxWidth = 320.dp

@Composable
private fun rememberDrawerWidth() = with(LocalConfiguration.current) {
    val fraction = (screenWidthDp.dp * DRAWER_WIDTH_FRACTION)
    if (fraction < DrawerMaxWidth) fraction else DrawerMaxWidth
}

/**
 * Invisible strip on the drawer's edge that opens the drawer on a rightward swipe.
 *
 * @param onSwipe Invoked once the swipe passes the drag threshold.
 */
@Composable
private fun BoxScope.DrawerEdgeSwipe(onSwipe: () -> Unit) {
    val layoutDirection = LocalLayoutDirection.current
    val startAlignment = if (layoutDirection == LayoutDirection.Rtl) {
        Alignment.CenterEnd
    } else {
        Alignment.CenterStart
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(DrawerEdgeWidth)
            .align(startAlignment)
            .pointerInput(layoutDirection) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (dragged > EDGE_SWIPE_THRESHOLD_PX) onSwipe()
                        dragged = 0f
                    },
                    onDragCancel = { dragged = 0f },
                    onHorizontalDrag = { _, delta -> dragged += delta },
                )
            },
    )
}

/** Horizontal travel, in pixels, required to open the drawer from the edge. */
private const val EDGE_SWIPE_THRESHOLD_PX = 48f
