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

/**
 * One attachment of a stored turn, in the form the transcript draws it again.
 *
 * Only pictures are kept: a copy is written into app storage when the message is sent, and [uri]
 * points at that copy. The original picker URI is deliberately not stored — it is a temporary
 * permission that can lapse once the process that received it is gone, which is why reopening a chat
 * used to show nothing.
 *
 * @param uri `file://` URI of the kept copy.
 * @param type Attachment type, `image` for a picture.
 * @param name File name, when one is known.
 * @param mimeType Media type of the kept copy.
 */
public data class StoredAttachment(
    val uri: String,
    val type: String = "image",
    val name: String? = null,
    val mimeType: String? = null,
)

/**
 * One message of a stored conversation.
 *
 * [attachments] defaults to empty so conversation files written before pictures were kept still
 * parse: a missing field means the turn simply has none.
 */
public data class StoredTurn(
    val role: String,
    val content: String,
    val attachments: List<StoredAttachment> = emptyList(),
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
    private val directory: File,
) {

    /**
     * Built on first use, never at construction time, and allowed to fail: this is reached from the
     * startup path, so a serialiser that cannot be built must leave conversations empty rather than
     * kill the process. See `proguard-rules.pro` for the R8 rules that keep it working.
     */
    private val adapter: JsonAdapter<StoredConversation>? by lazy {
        runCatching {
            Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
                .adapter(StoredConversation::class.java)
        }.getOrNull()
    }

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
        val serializer = adapter ?: return
        directory.mkdirs()
        runCatching { fileFor(conversation.id).writeText(serializer.toJson(conversation)) }
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

    private fun read(file: File): StoredConversation? {
        val serializer = adapter ?: return null
        return runCatching { serializer.fromJson(file.readText()) }.getOrNull()
    }

    private fun fileFor(id: String): File = File(directory, "${id.toStorageName()}.$JSON_EXTENSION")

    private companion object {
        const val JSON_EXTENSION = "json"
    }
}

/** The folder conversations are kept in, given an application `filesDir`. */
internal fun conversationsDirectory(filesDir: File): File = File(filesDir, "conversations")

/** The folder kept copies of sent pictures live in, given an application `filesDir`. */
internal fun picturesDirectory(filesDir: File): File = File(filesDir, "pictures")

/** Conversation ids are channel ids and may contain separators, so they are flattened for a file name. */
internal fun String.toStorageName(): String = map { character ->
    if (character.isLetterOrDigit() || character == '.' || character == '-' || character == '_') {
        character
    } else {
        '_'
    }
}.joinToString(separator = "")
