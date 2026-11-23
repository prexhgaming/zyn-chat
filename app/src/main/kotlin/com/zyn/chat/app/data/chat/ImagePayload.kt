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
import java.util.Base64

/**
 * Turns staged pictures into something a request can carry.
 *
 * Pictures are sent inline, as base64 inside a `data:` URL, because that is the one form every
 * OpenAI-compatible endpoint takes without an upload step of its own. An untouched phone photo would
 * cost several megabytes per message, so anything larger than [MAX_EDGE] on its longest side is
 * decoded at a power-of-two reduction and re-encoded as JPEG; a picture that is already small enough
 * is sent exactly as it was, without a needless re-encode.
 *
 * Every call runs on the IO dispatcher: reading, decoding and encoding pictures is real work and must
 * never happen on the main thread. A URI that is not a readable picture is skipped rather than failing
 * the send, because documents belong to the provider's own file handling, not to this one.
 */
internal object ImagePayload {

    /**
     * Reads the pictures among [uris], in the order they were staged.
     *
     * @param context Context used to open the URIs.
     * @param uris Staged attachment URIs, which may include documents.
     * @return The readable pictures, at most [MAX_IMAGES] of them.
     */
    suspend fun load(context: Context, uris: List<Uri>): List<ChatImage> = withContext(Dispatchers.IO) {
        uris.take(MAX_IMAGES).mapNotNull { uri -> read(context, uri) }
    }

    /** Reads one URI as a picture, or `null` when it is not one or cannot be read. */
    private fun read(context: Context, uri: Uri): ChatImage? = runCatching {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val bytes = if (mimeType.startsWith(IMAGE_MIME_PREFIX)) open(context, uri) else null
        bytes?.let { picture -> toSend(bytes = picture, mimeType = mimeType) }
    }.getOrNull()

    /** The bytes behind a URI, or `null` when it cannot be opened. */
    private fun open(context: Context, uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }

    /** The picture as it will be sent: untouched when small, a re-encoded JPEG otherwise. */
    private fun toSend(bytes: ByteArray, mimeType: String): ChatImage = shrunk(bytes)
        ?.let { smaller -> encoded(mimeType = JPEG_MIME_TYPE, bytes = smaller) }
        ?: encoded(mimeType = mimeType, bytes = bytes)

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

    /** Largest power of two that keeps the longer side at or above [MAX_EDGE]. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * 2) >= MAX_EDGE) {
            sample *= 2
        }
        return sample
    }

    /** Base64 form of a picture, which is what the `data:` URL will carry. */
    private fun encoded(mimeType: String, bytes: ByteArray): ChatImage = ChatImage(
        mimeType = mimeType,
        base64 = Base64.getEncoder().encodeToString(bytes),
    )
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
