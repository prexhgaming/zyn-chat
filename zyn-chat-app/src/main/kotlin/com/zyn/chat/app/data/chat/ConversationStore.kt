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

package com.zyn.chat.app.data.chat

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

/** Where a conversation's title came from, which decides whether it may still change on its own. */
public enum class TitleSource {
    /** Derived on the device from the first question. */
    Local,

    /** Written by the model. Replaces [Local], but never [Manual]. */
    Model,

    /** Chosen by the person. Nothing overwrites it. */
    Manual,
}

/** One message of a stored conversation. */
public data class StoredTurn(
    val role: String,
    val content: String,
)

/**
 * A conversation as it is kept on the device: its own turns, its own title, and which provider and
 * model answered in it.
 *
 * @param summary Rolling summary of the turns that no longer fit the context window.
 */
public data class StoredConversation(
    val id: String,
    val title: String = "",
    val titleSource: TitleSource = TitleSource.Local,
    val providerId: String? = null,
    val modelId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val turns: List<StoredTurn> = emptyList(),
    val summary: String? = null,
)

/**
 * One JSON file per conversation, plus a small in-memory cache.
 *
 * There is deliberately no shared message list anywhere in the app: a conversation's turns live in
 * its own file and are read back by id, which is what keeps one chat's history out of another's
 * request. The file name is derived from the id and the id is stored inside the file too, so the
 * stored id is always authoritative.
 *
 * All calls touch the disk and must be made off the main thread.
 */
internal class ConversationStore(
    context: Context,
    private val directory: File = File(context.filesDir, CONVERSATIONS_DIRECTORY),
) {

    private val adapter: JsonAdapter<StoredConversation> = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(StoredConversation::class.java)

    private val cache = mutableMapOf<String, StoredConversation>()
    private var loaded = false

    /** Every stored conversation, most recently used first. */
    fun list(): List<StoredConversation> {
        ensureLoaded()
        return cache.values.sortedByDescending { conversation -> conversation.updatedAt }
    }

    /** One conversation, or `null` when nothing has been stored under that id. */
    fun get(id: String): StoredConversation? {
        ensureLoaded()
        return cache[id]
    }

    /** Writes a conversation, replacing any previous version of it. */
    fun save(conversation: StoredConversation) {
        ensureLoaded()
        cache[conversation.id] = conversation
        directory.mkdirs()
        runCatching { fileFor(conversation.id).writeText(adapter.toJson(conversation)) }
    }

    /** Forgets a conversation and its file. */
    fun delete(id: String) {
        ensureLoaded()
        cache.remove(id)
        fileFor(id).delete()
    }

    private fun ensureLoaded() {
        if (loaded) return
        directory.mkdirs()
        directory.listFiles()
            .orEmpty()
            .filter { file -> file.extension == JSON_EXTENSION }
            .forEach { file ->
                read(file)?.let { conversation -> cache[conversation.id] = conversation }
            }
        loaded = true
    }

    private fun read(file: File): StoredConversation? =
        runCatching { adapter.fromJson(file.readText()) }.getOrNull()

    private fun fileFor(id: String): File = File(directory, "${id.toFileName()}.$JSON_EXTENSION")

    private companion object {
        const val CONVERSATIONS_DIRECTORY = "conversations"
        const val JSON_EXTENSION = "json"
    }
}

/** Conversation ids are channel ids and may contain separators, so they are flattened for a file name. */
private fun String.toFileName(): String = map { character ->
    if (character.isLetterOrDigit() || character == '.' || character == '-' || character == '_') {
        character
    } else {
        '_'
    }
}.joinToString(separator = "")
