/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Helper class for encrypting and decrypting the passphrase using Android KeyStore.
 */
class PassphraseEncryptor(private val context: Context) {

    companion object {
        private const val TAG = "PassphraseEncryptor"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val IV_SEPARATOR = ":"
        private const val GCM_TAG_LENGTH = 128
    }

    /**
     * Encrypt the given data using the Android KeyStore.
     *
     * @param alias The key alias to use for encryption.
     * @param data The data to encrypt.
     */
    @Throws(Exception::class)
    fun encrypt(alias: String, data: String) {
        try {
            val cipher = getCipher()
            val key = getOrCreateKey(alias)

            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv

            val dataBytes = data.toByteArray(StandardCharsets.UTF_8)
            val encryptedBytes = cipher.doFinal(dataBytes)

            // Encode the IV and encrypted data for storage
            val ivEncoded = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedEncoded = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val combined = "$ivEncoded$IV_SEPARATOR$encryptedEncoded"

            // Store the encrypted data in shared preferences
            val sharedPrefs = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
            sharedPrefs.edit().putString(alias, combined).apply()

            Log.d(TAG, "Data encrypted and stored with alias: $alias")
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}")
            throw e
        }
    }

    /**
     * Decrypt data that was encrypted with the given alias.
     *
     * @param alias The key alias used for encryption.
     * @return The decrypted data, or null if it doesn't exist or can't be decrypted.
     */
    @Throws(Exception::class)
    fun decrypt(alias: String): String? {
        try {
            // Check if we have stored encrypted data
            val sharedPrefs = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
            val combined = sharedPrefs.getString(alias, null) ?: return null

            // Split IV and encrypted data
            val parts = combined.split(IV_SEPARATOR)
            if (parts.size != 2) {
                Log.e(TAG, "Invalid encrypted data format")
                return null
            }

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)

            // Get the key and initialize cipher for decryption
            val key = getKey(alias) ?: return null
            val cipher = getCipher()
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            // Decrypt and return as string
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}")
            throw e
        }
    }

    /**
     * Get or create a key with the given alias.
     *
     * @param alias The key alias.
     * @return The SecretKey for the alias.
     */
    @Throws(Exception::class)
    private fun getOrCreateKey(alias: String): SecretKey {
        return getKey(alias) ?: createKey(alias)
    }

    /**
     * Get an existing key from the KeyStore.
     *
     * @param alias The key alias.
     * @return The SecretKey for the alias, or null if it doesn't exist.
     */
    @Throws(Exception::class)
    private fun getKey(alias: String): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)

        return if (keyStore.containsAlias(alias)) {
            keyStore.getKey(alias, null) as SecretKey
        } else {
            null
        }
    }

    /**
     * Create a new key in the KeyStore.
     *
     * @param alias The key alias.
     * @return The newly created SecretKey.
     */
    @Throws(Exception::class)
    private fun createKey(alias: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM, ANDROID_KEY_STORE)

        val keyGenSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        .setBlockModes(BLOCK_MODE)
        .setEncryptionPaddings(PADDING)
        .setRandomizedEncryptionRequired(true)
        .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Get a cipher instance configured for AES/GCM/NoPadding.
     *
     * @return A configured Cipher instance.
     */
    @Throws(Exception::class)
    private fun getCipher(): Cipher {
        val transformation = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        return Cipher.getInstance(transformation)
    }
}