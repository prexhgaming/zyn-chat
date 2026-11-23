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

package com.zyn.chat.app.ui.chat

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a picture from a content URI at roughly [maxSizePx] on its longest side.
 *
 * The app has no image-loading library, and adding one for two surfaces would be the wrong trade, so
 * this does the one thing that is actually needed: read the bounds, work out how much to shrink by,
 * and decode into a bitmap small enough to hold in memory. Decoding happens off the main thread, and
 * a picture that cannot be read (a deleted file, an unsupported codec, a document that is not an
 * image at all) decodes to `null` so the caller can fall back to a placeholder.
 *
 * @param uri Picture to decode, normally from the photo picker.
 * @param maxSizePx Longest side of the result, in pixels.
 */
@Composable
internal fun rememberUriImage(
    uri: Uri,
    maxSizePx: Int,
): ImageBitmap? {
    val contentResolver = LocalContext.current.contentResolver
    var image by remember(uri, maxSizePx) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uri, maxSizePx) {
        image = withContext(Dispatchers.IO) { contentResolver.decodeScaled(uri, maxSizePx) }
    }

    return image
}

/**
 * Whether a picked URI is a picture the app should preview as one.
 *
 * The content resolver knows the type for a photo-picker URI, whose path is often just a numeric id
 * with no extension, so the media type is asked first and the file name is only a fallback.
 *
 * @param contentResolver Resolver that reports the URI's media type.
 */
internal fun Uri.looksLikeImage(contentResolver: ContentResolver): Boolean {
    val mimeType = runCatching { contentResolver.getType(this) }.getOrNull()
    if (mimeType != null && mimeType != GENERIC_MIME_TYPE) return mimeType.startsWith("image/")
    return lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty() in IMAGE_EXTENSIONS
}

/** Name of the picked file, or an empty string when the provider does not expose one. */
internal fun Uri.attachmentName(): String =
    lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank).orEmpty()

/** Filename extensions treated as pictures. */
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif")

/** Media type a provider falls back to when it cannot tell what a file is. */
private const val GENERIC_MIME_TYPE = "application/octet-stream"

/**
 * Reads a scaled bitmap, or `null` when the URI cannot be decoded as an image.
 *
 * Two passes are made over the stream: the first only reads the dimensions, the second decodes at a
 * power-of-two reduction that keeps the result above the requested size, which is both cheaper and
 * sharper than scaling afterwards.
 */
private fun ContentResolver.decodeScaled(uri: Uri, maxSizePx: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSizePx)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
}.getOrNull()?.asImageBitmap()

/** Largest power of two that still leaves both sides at or above [maxSizePx]. */
private fun sampleSizeFor(width: Int, height: Int, maxSizePx: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= maxSizePx && height / (sample * 2) >= maxSizePx) {
        sample *= 2
    }
    return sample
}
