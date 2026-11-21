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

import okio.BufferedSource

/**
 * Reads server-sent events off a streaming response body.
 *
 * The wire format is line based: `data:` lines carry a payload, a blank line ends an event, and
 * `:`-prefixed lines are comments (providers send those as keep-alives). An event's payload is the
 * concatenation of its `data:` lines.
 *
 * This is deliberately a small blocking reader over the response source: it is driven from
 * `Dispatchers.IO` by the engine, keeps no state of its own between calls, and knows nothing about
 * the payload format, which keeps it readable and testable on its own.
 *
 * @param source Body of the streaming response.
 */
internal class SseReader(private val source: BufferedSource) {

    /**
     * Reads the next event.
     *
     * @return The event's payload, or `null` at the end of the stream.
     */
    fun next(): String? {
        val payload = StringBuilder()

        while (true) {
            val line = source.readUtf8Line()
                ?: return payload.toString().takeIf(String::isNotEmpty)

            when {
                line.isEmpty() -> if (payload.isNotEmpty()) return payload.toString()
                line.startsWith(COMMENT) -> Unit
                line.startsWith(FIELD) -> payload.append(line.removePrefix(FIELD).trim())
            }
        }
    }

    private companion object {
        const val FIELD = "data:"
        const val COMMENT = ":"
    }
}
