package com.zyn.chat.app.di

import androidx.lifecycle.ViewModelStore

/**
 * ViewModel stores for open conversations, kept for the life of the process.
 *
 * A conversation's ViewModel owns its answer stream, so it has to outlive the screen that shows it:
 * navigating to another chat, or turning the device, must not cancel a response that is still being
 * written. Stores therefore live here instead of being created and cleared around the chat
 * composable, and are released only when the conversation itself is deleted.
 *
 * Not thread-confined: Compose may build a store from the composition while deletion arrives from a
 * coroutine, so every access is synchronised.
 */
internal object ChatViewModelStores {

    private val stores = mutableMapOf<String, ViewModelStore>()

    /** The store for [conversationId], created on first use and reused after that. */
    @Synchronized
    fun storeFor(conversationId: String): ViewModelStore =
        stores.getOrPut(conversationId) { ViewModelStore() }

    /** Clears and forgets one conversation's ViewModels, once that conversation is deleted. */
    @Synchronized
    fun clear(conversationId: String) {
        stores.remove(conversationId)?.clear()
    }

    /** Clears every conversation's ViewModels, for when all chats are being dropped at once. */
    @Synchronized
    fun clearAll() {
        stores.values.forEach(ViewModelStore::clear)
        stores.clear()
    }
}
