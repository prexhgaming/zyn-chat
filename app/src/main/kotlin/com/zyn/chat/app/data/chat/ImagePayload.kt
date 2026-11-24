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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.zyn.chat.app.domain.chat.ChatImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * A picture ready to send, together with the copy kept on the device.
 *
 * @param image The picture exactly as the request will carry it.
 * @param storedUri `file://` URI of the kept copy, or `null` when nothing could be written.
 */
internal data class PreparedImage(
    val image: ChatImage,
    val storedUri: String?,
)

/**
 * Turns staged pictures into something a request can carry, and keeps a copy of each.
 *
 * Pictures are sent inline, as base64 inside a `data:` URL, because that is the one form every
 * OpenAI-compatible endpoint takes without an upload step of its own. An untouched phone photo would
 * cost several megabytes per message, so anything larger than [MAX_EDGE] on its longest side is
 * decoded at a power-of-two reduction and re-encoded as JPEG; a picture that is already small enough
 * is sent exactly as it was, without a needless re-encode.
 *
 * Those same bytes are written into app storage, because the URI a picker hands back is a temporary
 * permission: it is readable while the process that received it lives, and may be useless after a
 * restart. Keeping the copy is what lets a reopened conversation show its pictures again.
 *
 * Every call runs on the IO dispatcher: reading, decoding, encoding and writing pictures is real work
 * and must never happen on the main thread. A URI that is not a readable picture is skipped rather
 * than failing the send, because documents belong to the provider's own file handling, not to this one.
 */
internal object ImagePayload {

    /**
     * Reads the pictures among [uris], in the order they were staged.
     *
     * @param context Context used to open the URIs.
     * @param uris Staged attachment URIs, which may include documents.
     * @param directory Folder the kept copies are written to, or `null` to send without keeping one.
     * @return The readable pictures, at most [MAX_IMAGES] of them.
     */
    suspend fun load(
        context: Context,
        uris: List<Uri>,
        directory: File?,
    ): List<PreparedImage> = withContext(Dispatchers.IO) {
        uris.take(MAX_IMAGES).mapNotNull { uri -> read(context = context, uri = uri, directory = directory) }
    }

    /** Reads one URI as a picture, or `null` when it is not one or cannot be read. */
    private fun read(context: Context, uri: Uri, directory: File?): PreparedImage? = runCatching {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val bytes = if (mimeType.startsWith(IMAGE_MIME_PREFIX)) open(context, uri) else null
        bytes?.let { picture -> prepared(bytes = picture, mimeType = mimeType, directory = directory) }
    }.getOrNull()

    /** The bytes behind a URI, or `null` when it cannot be opened. */
    private fun open(context: Context, uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }

    /** The picture as it is sent, plus a copy kept so the chat can draw it again later. */
    private fun prepared(bytes: ByteArray, mimeType: String, directory: File?): PreparedImage {
        val encoded = toSend(bytes = bytes, mimeType = mimeType)
        return PreparedImage(
            image = ChatImage(
                mimeType = encoded.mimeType,
                base64 = Base64.getEncoder().encodeToString(encoded.bytes),
            ),
            storedUri = encoded.bytes.keptIn(directory = directory, mimeType = encoded.mimeType),
        )
    }

    /** The exact bytes a request will carry, before they are base64 encoded. */
    private data class Encoded(val mimeType: String, val bytes: ByteArray)

    /** The picture as it will be sent: untouched when small, a re-encoded JPEG otherwise. */
    private fun toSend(bytes: ByteArray, mimeType: String): Encoded =
        shrunk(bytes)?.let { smaller -> Encoded(JPEG_MIME_TYPE, smaller) }
            ?: Encoded(mimeType, bytes)

    /** A re-encoded copy of the picture, or `null` when it is small enough to send as it is. */
    private fun shrunk(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val fitsAsIs = maxOf(bounds.outWidth, bounds.outHeight) <= MAX_EDGE && bytes.size <= MAX_BYTES
        if (fitsAsIs) return null
        return recompress(bytes = bytes, bounds = bounds)
    }

    /**
     * Decodes [bytes] with a power-of-two reduction and writes it back as JPEG.
     *
     * @param bytes The picture as read.
     * @param bounds Dimensions read from the header, used to pick the reduction.
     * @return The JPEG, or `null` when the picture cannot be decoded.
     */
    private fun recompress(bytes: ByteArray, bounds: BitmapFactory.Options): ByteArray? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(width = bounds.outWidth, height = bounds.outHeight)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return try {
            ByteArrayOutputStream().use { recompressed ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, recompressed)
                recompressed.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Writes the sent bytes into [directory] and returns their URI, or `null` when it cannot. */
    private fun ByteArray.keptIn(directory: File?, mimeType: String): String? {
        val target = directory ?: return null
        return runCatching {
            target.mkdirs()
            val file = File(target, imageFileName(bytes = this, mimeType = mimeType))
            // The name is a content hash, so an existing file is already this exact picture.
            if (!file.exists()) file.writeBytes(this)
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    /** Largest power of two that keeps the longer side at or above [MAX_EDGE]. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * 2) >= MAX_EDGE) {
            sample *= 2
        }
        return sample
    }
}

/**
 * Name of the kept copy of a picture: a hash of its bytes plus an extension for its media type.
 *
 * Hashing rather than numbering keeps one copy of a picture sent twice and gives the name a stable
 * value that can be asserted in a test.
 *
 * @param bytes The picture's bytes, exactly as sent.
 * @param mimeType Media type of those bytes.
 */
internal fun imageFileName(bytes: ByteArray, mimeType: String): String =
    sha256Hex(bytes).take(HASH_HEX_LENGTH) + "." + extensionFor(mimeType)

/** File extension for a picture's media type, falling back to a neutral one. */
internal fun extensionFor(mimeType: String): String = when (mimeType.lowercase()) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/heic" -> "heic"
    "image/heif" -> "heif"
    "image/bmp" -> "bmp"
    "image/avif" -> "avif"
    else -> "img"
}

/** Lower-case hex SHA-256 of [bytes]. */
private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance(SHA_256)
        .digest(bytes)
        .joinToString(separator = "") { byte ->
            ((byte.toInt() and BYTE_MASK) + HEX_RADIX).toString(HEX_BITS).substring(1)
        }

/** Media types that mark a staged attachment as a picture. */
private const val IMAGE_MIME_PREFIX = "image/"

/** Media type of a picture re-encoded for sending. */
private const val JPEG_MIME_TYPE = "image/jpeg"

/**
 * Longest side a picture is sent at, in pixels.
 *
 * Above this a model gains no detail it can use, while the request keeps growing with every extra
 * pixel.
 */
private const val MAX_EDGE = 1_568

/** Quality of the JPEG a picture is re-encoded to. */
private const val JPEG_QUALITY = 85

/** Largest picture sent without being re-encoded. */
private const val MAX_BYTES = 1_500_000

/** Pictures sent with one message; the composer stages no more than this either. */
private const val MAX_IMAGES = 5

/** Characters of the hash kept in a file name: plenty to keep two pictures apart. */
private const val HASH_HEX_LENGTH = 32

/** Digest used to name kept copies. */
private const val SHA_256 = "SHA-256"

/** Mask of the low byte, so a negative byte formats as its unsigned value. */
private const val BYTE_MASK = 0xFF

/** Added to a byte so the hex form always has two digits before the leading one is trimmed. */
private const val HEX_RADIX = 0x100

/** Base of the hex representation. */
private const val HEX_BITS = 16
