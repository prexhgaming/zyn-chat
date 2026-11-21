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

package com.zyn.chat.app.data.providers

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores provider API keys encrypted at rest.
 *
 * The AES-GCM key is generated inside the **Android Keystore** and never leaves it, so the ciphertext
 * in `SharedPreferences` is useless off the device and cannot be read even with root access to the
 * preferences file alone. This is done with the platform APIs rather than adding a dependency
 * (`androidx.security-crypto` is deprecated).
 *
 * One entry per provider id, so replacing a key overwrites it and removing a provider deletes it.
 * If the Keystore entry is ever invalidated (device credential reset, restore onto new hardware),
 * decryption fails and the affected key is dropped rather than crashing: the person is asked for
 * their key again.
 *
 * This class is the only place a key is read from. Callers pass keys in and never get them back
 * except through [get], which is used by the provider client alone.
 *
 * @param context Any context; the application context is used.
 */
public class ApiKeyStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /**
     * Saves (or replaces) the key for a provider.
     *
     * @param providerId Provider the key belongs to.
     * @param key The raw key as the person typed it.
     * @return `true` when the key was stored.
     */
    public fun save(providerId: String, key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return false

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
            preferences.edit()
                .putString(providerId, "${iv.encode()}${IV_SEPARATOR}${encrypted.encode()}")
                .apply()
        }.isSuccess
    }

    /**
     * Reads a key back, for the provider client only.
     *
     * @param providerId Provider to read the key for.
     * @return The key, or `null` when none is stored or it can no longer be decrypted.
     */
    public fun get(providerId: String): String? {
        val stored = preferences.getString(providerId, null) ?: return null
        val parts = stored.split(IV_SEPARATOR)
        if (parts.size != 2) {
            delete(providerId)
            return null
        }

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_BITS, parts[0].decode()),
            )
            String(cipher.doFinal(parts[1].decode()), Charsets.UTF_8)
        }.getOrElse {
            // The Keystore entry is gone or was invalidated: drop the unreadable value.
            delete(providerId)
            null
        }
    }

    /** Whether a key is stored for [providerId]. */
    public fun has(providerId: String): Boolean = preferences.contains(providerId)

    /** Removes the stored key for [providerId]. */
    public fun delete(providerId: String) {
        preferences.edit().remove(providerId).apply()
    }

    /**
     * Masked fragment of the stored key, safe to show in the UI: keeps a short prefix and suffix and
     * hides the middle, so nobody reading the screen learns the key.
     *
     * @param providerId Provider to describe.
     * @return The hint, or `null` when no key is stored.
     */
    public fun hint(providerId: String): String? = get(providerId)?.let(::maskKey)

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val PREFERENCES_NAME = "zyn_api_keys"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "zyn_chat_provider_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
        const val IV_SEPARATOR = ":"
    }
}

/** Visible prefix/suffix lengths of a masked key. */
private const val HINT_PREFIX = 3
private const val HINT_SUFFIX = 4

/** Shortest key that can still be partially revealed; anything else is masked completely. */
private const val HINT_MIN_LENGTH = HINT_PREFIX + HINT_SUFFIX + 1

/**
 * Masks a key for display, e.g. `sk-...f4a2`.
 *
 * @param key The key to mask.
 */
internal fun maskKey(key: String): String =
    if (key.length < HINT_MIN_LENGTH) {
        MASKED
    } else {
        "${key.take(HINT_PREFIX)}$ELLIPSIS${key.takeLast(HINT_SUFFIX)}"
    }

private const val MASKED = "\u2022\u2022\u2022\u2022\u2022\u2022"
private const val ELLIPSIS = "\u2026"
