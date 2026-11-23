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

package com.zyn.chat.app.crash

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.zyn.chat.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records every crash to a file and opens [CrashActivity] to show it.
 *
 * This exists because a crash during `Application.onCreate` kills the process before any screen can
 * appear: the app simply closes, with the stack trace only visible in logcat. Installing a handler in
 * `attachBaseContext` — the earliest code the process runs — and showing it in a **separate process**
 * means the report survives the process that died and is visible on the device.
 *
 * A report is kept until the crash screen is dismissed, so a crash that is too early or too severe for
 * the screen to open is still shown the next time the app starts.
 */
public object CrashHandler {

    private const val TAG = "ZynCrash"
    private const val DIRECTORY_NAME = "crashes"
    private const val FILE_NAME = "last-crash.txt"

    /** Suffix of the process the crash screen runs in; see `android:process` in the manifest. */
    private const val CRASH_PROCESS_SUFFIX = ":crash"

    private var installed = false
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * Installs the handler. Safe to call more than once and from any process.
     *
     * @param context Any context; the application context is kept.
     */
    public fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Nothing here may throw, or the original crash would be replaced by this one.
            runCatching {
                val file = reportFile(appContext)
                file.parentFile?.mkdirs()
                file.writeText(buildReport(thread, error))
            }.onFailure { failure -> Log.e(TAG, "Could not record the crash report", failure) }

            runCatching { launch(appContext) }
                .onFailure { failure -> Log.e(TAG, "Could not open the crash screen", failure) }

            Log.e(TAG, "Uncaught exception on ${thread.name}", error)

            // Let the platform report the crash and kill the process as usual.
            previousHandler?.uncaughtException(thread, error)
        }
    }

    /** Whether the current process is the one that shows crash reports. */
    public fun isCrashProcess(context: Context): Boolean =
        currentProcessName(context).endsWith(CRASH_PROCESS_SUFFIX)

    /** The crash report waiting to be read, or `null` when the last run ended cleanly. */
    public fun readPending(context: Context): String? =
        reportFile(context)
            .takeIf { file -> file.exists() }
            ?.let { file -> runCatching { file.readText() }.getOrNull() }
            ?.takeIf { report -> report.isNotBlank() }

    /** Opens the crash screen when an unacknowledged report exists. */
    public fun showPending(context: Context) {
        if (readPending(context) != null) {
            launch(context)
        }
    }

    /** Forgets the report, once the person has seen it. */
    public fun clear(context: Context) {
        runCatching { reportFile(context).delete() }
    }

    private fun launch(context: Context) {
        context.startActivity(
            Intent(context, CrashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }

    private fun buildReport(thread: Thread, error: Throwable): String = buildString {
        appendLine("Zyn Chat crash report")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Build: ${if (BuildConfig.DEBUG) "debug" else "release"}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Process: ${currentProcessName(null)}")
        appendLine("Thread: ${thread.name}")
        appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine()
        append(Log.getStackTraceString(error))
    }

    private fun reportFile(context: Context): File =
        File(File(context.filesDir, DIRECTORY_NAME), FILE_NAME)

    /**
     * Name of the current process. Falls back to `/proc/self/cmdline` before API 28, where the
     * platform has no direct equivalent.
     */
    private fun currentProcessName(context: Context?): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName().orEmpty()
        }

        val fromActivityManager = runCatching {
            val manager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val pid = Process.myPid()
            manager?.runningAppProcesses?.firstOrNull { info -> info.pid == pid }?.processName
        }.getOrNull()

        return fromActivityManager
            ?: runCatching { File("/proc/self/cmdline").readText() }
                .getOrDefault("")
                .trim('\u0000', '\n', ' ')
    }
}
