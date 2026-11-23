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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.zyn.chat.app.R
import java.io.File
import kotlinx.coroutines.launch

/**
 * Taking a picture with the device camera, as a callback that starts the camera.
 *
 * The camera app never hands the picture back: it writes it into a file of this app, given to it as
 * a URI of the app's own `FileProvider` (declared in `AndroidManifest.xml`, its paths in
 * `res/xml/zyn_file_paths.xml`), with the write permission travelling on the intent. The file lives
 * in the app's cache, so nothing reaches shared storage and no storage permission is involved.
 *
 * The same picture is staged like any picked one: whether the selected model can read it is the
 * provider's business, so a refusal comes back as an answer in the chat rather than a warning here.
 *
 * @param snackbarHostState Host that carries the device-level explanations, such as no camera app.
 * @param onCapture Invoked with the picture once the camera app has written it.
 * @return Starts the camera, or explains why it could not be started.
 */
@Composable
internal fun rememberCameraCapture(
    snackbarHostState: SnackbarHostState,
    onCapture: (Uri) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val unavailableMessage = stringResource(R.string.zyn_composer_camera_unavailable)
    val failedMessage = stringResource(R.string.zyn_composer_camera_failed)

    // The capture is launched with a file this app owns, so that URI has to be kept until the camera
    // app returns — the result of the launch says nothing about where the picture went.
    var pendingCapture by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val capture = pendingCapture
        pendingCapture = null
        if (capture == null) return@rememberLauncherForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            onCapture(capture)
        } else {
            // Backed out of the camera: the placeholder holds nothing worth keeping.
            runCatching { context.contentResolver.delete(capture, null, null) }
        }
    }

    return {
        val output = createCaptureOutput(context)

        if (output == null) {
            scope.launch { snackbarHostState.showSnackbar(failedMessage) }
        } else {
            pendingCapture = output
            // A device without a camera app fails here, synchronously, before anything is taken.
            val started = runCatching { launcher.launch(captureIntentFor(output)) }.isSuccess

            if (!started) {
                pendingCapture = null
                runCatching { context.contentResolver.delete(output, null, null) }
                scope.launch { snackbarHostState.showSnackbar(unavailableMessage) }
            }
        }
    }
}

/**
 * The intent that has the camera write its picture into [output].
 *
 * The write permission has to travel with the intent: the camera app is a different app, and it
 * would otherwise not be allowed to write into this app's provider.
 */
private fun captureIntentFor(output: Uri): Intent =
    Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        .putExtra(MediaStore.EXTRA_OUTPUT, output)
        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

/**
 * A fresh file in the app's cache for the camera to write into, as a URI it may write.
 *
 * The cache is the right home for it: the picture is staged for the composer, and a shot the person
 * abandons never ends up in shared storage. Returns `null` when the file cannot be created.
 */
private fun createCaptureOutput(context: Context): Uri? = runCatching {
    val directory = File(context.cacheDir, CAMERA_DIRECTORY).apply { mkdirs() }
    val file = File(directory, "$CAMERA_FILE_PREFIX${System.currentTimeMillis()}$CAMERA_FILE_SUFFIX")
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

/** Folder in the app's cache that the camera app writes pictures into. */
private const val CAMERA_DIRECTORY = "camera"
private const val CAMERA_FILE_PREFIX = "capture_"
private const val CAMERA_FILE_SUFFIX = ".jpg"
