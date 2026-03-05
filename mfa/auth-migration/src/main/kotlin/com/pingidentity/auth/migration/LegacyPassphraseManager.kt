/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Legacy Passphrase Manager that retrieves the SQLCipher database passphrase
 * from the Legacy SDK's storage mechanisms.
 *
 * The Legacy SDK stores the passphrase using two methods:
 * 1. BlockStore (Google's cloud-backed encrypted storage) - Preferred
 * 2. LocalKeyStore (Android KeyStore-based local encryption) - Fallback
 *
 * This class implements the LocalKeyStore approach as it's deterministic and
 * doesn't require Google Play Services. BlockStore retrieval would require
 * async operations with Google Play Services which may not be available.
 *
 * Based on Legacy SDK:
 * - forgerock-authenticator/src/main/java/org/forgerock/android/auth/storage/PassphraseManager.java
 * - forgerock-authenticator/src/main/java/org/forgerock/android/auth/storage/LocalKeyStoreHelper.java
 */
class LegacyPassphraseManager(
    private val context: Context
) {
    companion object {
        // Legacy SDK constants
        private const val PASSPHRASE_KEY = "database_passphrase"
        private const val PREFS_NAME = "org.forgerock.android.auth.passphrase_prefs"
        private const val PREF_STORAGE_METHOD = "passphrase_storage_method"

        // Storage method IDs (from Legacy SDK)
        private const val STORAGE_METHOD_NONE = 0
        private const val STORAGE_METHOD_BLOCKSTORE = 1
        private const val STORAGE_METHOD_LOCALKEYSTORE = 2

        // LocalKeyStore encryption details
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12

        // Test environment
        private const val TEST_PASSPHRASE = "test_passphrase"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    /**
     * Retrieves the database passphrase using the same logic as Legacy SDK.
     *
     * Priority:
     * 1. Check if running in test environment → return TEST_PASSPHRASE
     * 2. Check stored storage method preference
     * 3. Try LocalKeyStore (the only method we can implement reliably)
     *
     * @return The database passphrase, or null if not found
     */
    suspend fun getPassphrase(): String? = withContext(Dispatchers.IO) {
        // Check for test environment
        if (isTestEnvironment()) {
            AuthMigration.logger.d("Test environment detected, using test passphrase")
            return@withContext TEST_PASSPHRASE
        }

        val storedMethod = getStoredMethod()
        AuthMigration.logger.d("Stored passphrase method: $storedMethod")

        // Try to retrieve using the stored method
        val passphrase = when (storedMethod) {
            STORAGE_METHOD_LOCALKEYSTORE -> retrieveFromLocalKeyStore()
            STORAGE_METHOD_BLOCKSTORE -> {
                // BlockStore requires Google Play Services and async operations
                // We'll skip it and try LocalKeyStore as fallback
                AuthMigration.logger.d("BlockStore method detected, trying LocalKeyStore as fallback")
                retrieveFromLocalKeyStore()
            }
            STORAGE_METHOD_NONE -> {
                // No method stored, try LocalKeyStore
                AuthMigration.logger.d("No storage method recorded, trying LocalKeyStore")
                retrieveFromLocalKeyStore()
            }
            else -> null
        }

        if (passphrase != null) {
            AuthMigration.logger.d("Successfully retrieved passphrase from legacy storage")
        } else {
            AuthMigration.logger.w("Could not retrieve passphrase from legacy storage")
        }

        passphrase
    }

    /**
     * Retrieves the passphrase from LocalKeyStore-encrypted file storage.
     *
     * Legacy SDK stores encrypted data using context.openFileOutput(key, ...):
     * - File location: {filesDir}/{keyAlias} (directly in files directory)
     * - File format: [IV (12 bytes)] + [Encrypted Data]
     * - Encryption: AES/GCM/NoPadding with Android KeyStore key
     */
    private fun retrieveFromLocalKeyStore(): String? {
        try {
            // Legacy SDK uses context.openFileOutput(key, ...) which stores at {filesDir}/{key}
            val passphraseFile = File(context.filesDir, PASSPHRASE_KEY)

            if (!passphraseFile.exists()) {
                AuthMigration.logger.d("LocalKeyStore passphrase file not found: ${passphraseFile.absolutePath}")
                return null
            }

            // Read encrypted data from file
            val encryptedBytes = passphraseFile.readBytes()

            if (encryptedBytes.size < IV_LENGTH) {
                AuthMigration.logger.w("Invalid passphrase file format (too small)")
                return null
            }

            // Extract IV and encrypted data
            // Format: [IV (12 bytes)] + [Encrypted Data]
            val iv = encryptedBytes.copyOfRange(0, IV_LENGTH)
            val encryptedData = encryptedBytes.copyOfRange(IV_LENGTH, encryptedBytes.size)

            // Get decryption key from Android KeyStore
            val secretKey = getOrCreateKey(PASSPHRASE_KEY)

            // Decrypt using AES/GCM
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedData)

            val passphrase = String(decryptedBytes, Charsets.UTF_8)
            AuthMigration.logger.d("Successfully decrypted passphrase from LocalKeyStore")
            return passphrase
        } catch (e: Exception) {
            AuthMigration.logger.e("Failed to retrieve passphrase from LocalKeyStore: ${e.message}", e)
            return null
        }
    }

    /**
     * Gets or creates a secret key in Android KeyStore for the given alias.
     *
     * This matches the Legacy SDK's key generation configuration EXACTLY.
     * The Legacy SDK does NOT set setRandomizedEncryptionRequired, which means:
     * - It defaults to true (system generates random IV for encryption)
     * - But decryption with caller-provided IV still works because the key was
     *   created without explicitly forbidding it (the flag only affects encryption)
     */
    private fun getOrCreateKey(keyAlias: String): SecretKey {
        // Check if key already exists
        if (keyStore.containsAlias(keyAlias)) {
            return keyStore.getKey(keyAlias, null) as SecretKey
        }

        // Generate new key with EXACT same configuration as Legacy SDK
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Do NOT set setRandomizedEncryptionRequired - Legacy SDK doesn't set it
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Gets the stored passphrase storage method from SharedPreferences.
     */
    private fun getStoredMethod(): Int {
        return prefs.getInt(PREF_STORAGE_METHOD, STORAGE_METHOD_NONE)
    }

    /**
     * Checks if running in a test environment.
     * Legacy SDK uses a fixed passphrase for tests.
     */
    private fun isTestEnvironment(): Boolean {
        return try {
            // Check for Robolectric
            Class.forName("org.robolectric.Robolectric")
            AuthMigration.logger.d("Running in Robolectric test environment")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    /**
     * Checks if a passphrase is stored in the legacy storage.
     */
    fun hasPassphrase(): Boolean {
        if (isTestEnvironment()) {
            return true
        }

        // Legacy SDK stores file directly in filesDir
        val passphraseFile = File(context.filesDir, PASSPHRASE_KEY)
        return passphraseFile.exists()
    }
}

