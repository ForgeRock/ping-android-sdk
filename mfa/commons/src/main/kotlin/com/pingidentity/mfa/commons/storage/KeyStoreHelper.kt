/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.storage

import android.content.Context
import android.os.Build
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
 * Helper class for encrypting and decrypting data using Android KeyStore.
 * This provides a secure way to store sensitive data such as passphrases.
 */
class KeyStoreHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "KeyStoreHelper"
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
            
            // Initialize cipher for encryption
            cipher.init(Cipher.ENCRYPT_MODE, key)
            
            // Get initialization vector
            val iv = cipher.iv
            
            // Encrypt the data
            val dataBytes = data.toByteArray(StandardCharsets.UTF_8)
            val encryptedBytes = cipher.doFinal(dataBytes)
            
            // Encode IV and encrypted data as Base64 with IV as prefix
            val ivAndEncryptedData = iv + encryptedBytes
            val encodedData = Base64.encodeToString(ivAndEncryptedData, Base64.NO_WRAP)
            
            // Store encrypted data in SharedPreferences
            val prefs = context.getSharedPreferences("MFA_KEYSTORE_PREFS", Context.MODE_PRIVATE)
            prefs.edit().putString(alias, encodedData).apply()
            
            Log.d(TAG, "Data encrypted successfully for alias: $alias")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt data for alias $alias: ${e.message}")
            throw e
        }
    }

    /**
     * Decrypt data using the Android KeyStore.
     *
     * @param alias The key alias used for encryption.
     * @return The decrypted data, or null if it doesn't exist or can't be decrypted.
     */
    @Throws(Exception::class)
    fun decrypt(alias: String): String? {
        try {
            // Get encrypted data from SharedPreferences
            val prefs = context.getSharedPreferences("MFA_KEYSTORE_PREFS", Context.MODE_PRIVATE)
            val encodedData = prefs.getString(alias, null) ?: return null
            
            // Decode from Base64
            val ivAndEncryptedData = Base64.decode(encodedData, Base64.NO_WRAP)
            
            // Extract IV (first 12 bytes for GCM)
            val iv = ivAndEncryptedData.copyOfRange(0, 12)
            
            // Extract encrypted data (remainder of array)
            val encryptedBytes = ivAndEncryptedData.copyOfRange(12, ivAndEncryptedData.size)
            
            // Get the key and initialize cipher for decryption
            val key = getOrCreateKey(alias)
            val cipher = getCipher()
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            
            // Decrypt the data
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            
            Log.d(TAG, "Data decrypted successfully for alias: $alias")
            return String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt data for alias $alias: ${e.message}")
            throw e
        }
    }

    /**
     * Get or create a key in the Android KeyStore.
     *
     * @param alias The key alias.
     * @return The SecretKey.
     */
    @Throws(Exception::class)
    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        
        // Check if key exists
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null)
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }
        
        // Create a new key if it doesn't exist
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setKeySize(256)
            .build()
        
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM, ANDROID_KEY_STORE)
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Get an instance of the Cipher for AES/GCM/NoPadding.
     *
     * @return The Cipher instance.
     */
    @Throws(Exception::class)
    private fun getCipher(): Cipher {
        val transformation = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        return Cipher.getInstance(transformation)
    }
}
