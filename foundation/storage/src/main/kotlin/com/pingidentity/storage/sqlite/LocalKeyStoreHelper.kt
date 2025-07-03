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
import com.pingidentity.utils.TestModeDetector
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * This class is designed to securely store and retrieve byte arrays (blocks of data) within a
 * file in the application's internal storage. It uses AndroidKeyStore to protect the data.
 */
class LocalKeyStoreHelper(private val context: Context) {

    companion object {
        private const val TAG = "LocalKeyStoreHelper"
        
        // Constants for encryption
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val CIPHER_TRANSFORMATION = "$ENCRYPTION_ALGORITHM/$ENCRYPTION_BLOCK_MODE/$ENCRYPTION_PADDING"
        private const val GCM_TAG_LENGTH = 128 // in bits
        private const val GCM_IV_LENGTH = 12 // in bytes
        
        // For test environment fallback
        private const val TEST_KEY_PREFS = "com.pingidentity.storage.block_manager_test_keys"
        private const val TEST_KEY_PREFIX = "test_key_"
        
        // File prefix for stored blocks
        private const val BLOCK_FILE_PREFIX = "block_"
    }
    
    // Flag indicating if we're running in a test environment
    private val isTestEnvironment = TestModeDetector.isRunningInTestEnvironment()
    
    /**
     * Store a byte array in the Block Store.
     *
     * @param key The key to store the data under.
     * @param data The data to store.
     * @throws Exception If there's an error storing the data.
     */
    @Throws(Exception::class)
    fun storeBytes(key: String, data: ByteArray) {
        try {
            // Get the secret key for encryption
            val secretKey = getOrCreateSecretKey(key)
            
            // Create cipher for encryption
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            
            // Encrypt the data
            val encryptedData = cipher.doFinal(data)
            
            // Combine IV and encrypted data
            val outputStream = ByteArrayOutputStream()
            outputStream.write(iv.size) // Write the IV length as a single byte
            outputStream.write(iv) // Write the IV
            outputStream.write(encryptedData) // Write the encrypted data
            val combined = outputStream.toByteArray()
            
            // Write to file
            val file = File(context.filesDir, getFileNameForKey(key))
            FileOutputStream(file).use { it.write(combined) }
            
            Log.d(TAG, "Successfully stored data for key: $key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store bytes for key $key: ${e.message}")
            throw e
        }
    }
    
    /**
     * Retrieve a byte array from the Block Store.
     *
     * @param key The key to retrieve the data from.
     * @return The retrieved data or null if not found.
     * @throws Exception If there's an error retrieving the data.
     */
    @Throws(Exception::class)
    fun retrieveBytes(key: String): ByteArray? {
        try {
            val file = File(context.filesDir, getFileNameForKey(key))
            if (!file.exists()) {
                Log.d(TAG, "No data file found for key: $key")
                return null
            }
            
            // Read the combined data from file
            val combined = FileInputStream(file).use { it.readBytes() }
            
            // Extract IV and encrypted data
            val ivSize = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivSize)
            val encryptedData = combined.copyOfRange(1 + ivSize, combined.size)
            
            // Get the secret key for decryption
            val secretKey = getOrCreateSecretKey(key)
            
            // Create cipher for decryption
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            
            // Decrypt the data
            val decryptedData = cipher.doFinal(encryptedData)
            Log.d(TAG, "Successfully retrieved data for key: $key")
            return decryptedData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve bytes for key $key: ${e.message}")
            throw e
        }
    }
    
    /**
     * Check if a block exists in the Block Store.
     *
     * @param key The key to check.
     * @return True if the block exists, false otherwise.
     */
    fun hasBlock(key: String): Boolean {
        val file = File(context.filesDir, getFileNameForKey(key))
        return file.exists()
    }
    
    /**
     * Delete a block from the Block Store.
     *
     * @param key The key of the block to delete.
     * @return True if the block was deleted, false otherwise.
     */
    fun deleteBlock(key: String): Boolean {
        val file = File(context.filesDir, getFileNameForKey(key))
        if (file.exists()) {
            val result = file.delete()
            Log.d(TAG, "Deleted block for key $key: $result")
            return result
        }
        return false
    }
    
    /**
     * Get or create a secret key for encryption/decryption.
     *
     * @param keyAlias The alias for the key.
     * @return The secret key.
     * @throws Exception If there's an error getting or creating the key.
     */
    @Throws(Exception::class)
    private fun getOrCreateSecretKey(keyAlias: String): SecretKey {
        if (isTestEnvironment) {
            return getOrCreateTestKey(keyAlias)
        }
        
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        // Check if key already exists
        if (keyStore.containsAlias(keyAlias)) {
            return keyStore.getKey(keyAlias, null) as SecretKey
        }
        
        // Generate a new key
        val keyGenerator = KeyGenerator.getInstance(
            ENCRYPTION_ALGORITHM,
            ANDROID_KEYSTORE
        )
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(ENCRYPTION_BLOCK_MODE)
            .setEncryptionPaddings(ENCRYPTION_PADDING)
            .setKeySize(256)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
    
    /**
     * Get or create a test key for use in test environments.
     * This is a fallback for when AndroidKeyStore is unavailable.
     *
     * @param keyAlias The alias for the key.
     * @return A secret key for test use.
     */
    private fun getOrCreateTestKey(keyAlias: String): SecretKey {
        val prefs = context.getSharedPreferences(TEST_KEY_PREFS, Context.MODE_PRIVATE)
        val prefKey = TEST_KEY_PREFIX + keyAlias
        
        // Check if we already have this key
        val existingKey = prefs.getString(prefKey, null)
        if (existingKey != null) {
            val keyBytes = Base64.decode(existingKey, Base64.DEFAULT)
            return SecretKeySpec(keyBytes, ENCRYPTION_ALGORITHM)
        }
        
        // Generate a new key
        val keyGenerator = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM)
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()
        
        // Store the key
        val editor = prefs.edit()
        editor.putString(prefKey, Base64.encodeToString(secretKey.encoded, Base64.DEFAULT))
        editor.apply()
        
        return secretKey
    }
    
    /**
     * Generate a consistent file name for a key.
     *
     * @param key The key.
     * @return A file name for the key.
     */
    private fun getFileNameForKey(key: String): String {
        return BLOCK_FILE_PREFIX + key.replace(":", "_").replace("/", "_")
    }
}