/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

/**
 * Manages the secure storage and retrieval of passphrases used for database encryption.
 * This class uses KeyStore for secure storage of passphrases.
 */
class PassphraseManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PassphraseManager"
        
        // Constants for passphrase storage
        private const val PASSPHRASE_KEY = "mfa_database_passphrase"
        private const val PASSPHRASE_LENGTH_BYTES = 32 // 256 bits
        
        // Storage method tracking
        private const val PREFS_NAME = "com.pingidentity.mfa.passphrase_prefs"
        private const val PREF_STORAGE_METHOD = "passphrase_storage_method"
        private const val STORAGE_METHOD_NONE = 0
        private const val STORAGE_METHOD_KEYSTORE = 1
        
        // Fixed test passphrase for instrumentation tests
        private const val TEST_PASSPHRASE = "test_passphrase"
    }
    
    // Storage helper
    private val keyStoreHelper by lazy { KeyStoreHelper(context) }
    
    // Flag indicating if we're running in a test environment
    private val isTestEnvironment by lazy { TestModeDetector.isRunningInTestEnvironment() }
    
    /**
     * Gets the existing passphrase or generates a new one if it doesn't exist.
     *
     * @return The database passphrase.
     */
    fun getOrCreatePassphrase(): String {
        // Use a fixed passphrase for testing
        if (isTestEnvironment) {
            Log.d(TAG, "Using test passphrase for SQLCipher")
            return TEST_PASSPHRASE
        }
        
        var passphrase: String? = null
        val storedMethod = getStoredMethod()
        
        // Try to retrieve existing passphrase using KeyStore
        if (storedMethod == STORAGE_METHOD_KEYSTORE) {
            passphrase = retrieveFromKeyStore()
        }
        
        // If no passphrase exists, generate and store a new one
        if (passphrase.isNullOrEmpty()) {
            passphrase = generateRandomPassphrase()
            storeNewPassphrase(passphrase)
        }
        
        return passphrase
    }
    
    /**
     * Retrieve the passphrase from the KeyStore.
     *
     * @return The passphrase, or null if it doesn't exist or can't be retrieved.
     */
    private fun retrieveFromKeyStore(): String? {
        try {
            val result = keyStoreHelper.decrypt(PASSPHRASE_KEY)
            if (!result.isNullOrEmpty()) {
                // Update storage method tracking
                setStoredMethod(STORAGE_METHOD_KEYSTORE)
                Log.d(TAG, "Successfully retrieved passphrase from KeyStore")
                return result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve passphrase from KeyStore: ${e.message}")
        }
        return null
    }
    
    /**
     * Generate a secure random passphrase.
     *
     * @return A new random passphrase.
     */
    private fun generateRandomPassphrase(): String {
        val random = SecureRandom()
        val bytes = ByteArray(PASSPHRASE_LENGTH_BYTES)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    
    /**
     * Store a new passphrase.
     *
     * @param passphrase The passphrase to store.
     */
    private fun storeNewPassphrase(passphrase: String) {
        try {
            // Try to store in KeyStore
            keyStoreHelper.encrypt(PASSPHRASE_KEY, passphrase)
            setStoredMethod(STORAGE_METHOD_KEYSTORE)
            Log.d(TAG, "Successfully stored passphrase in KeyStore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store passphrase: ${e.message}")
        }
    }
    
    /**
     * Get the previously used storage method.
     *
     * @return The storage method ID.
     */
    private fun getStoredMethod(): Int {
        val prefs = getSharedPreferences()
        return prefs.getInt(PREF_STORAGE_METHOD, STORAGE_METHOD_NONE)
    }
    
    /**
     * Set the storage method used.
     *
     * @param method The storage method ID.
     */
    private fun setStoredMethod(method: Int) {
        val prefs = getSharedPreferences()
        prefs.edit().putInt(PREF_STORAGE_METHOD, method).apply()
    }
    
    /**
     * Get the shared preferences for tracking storage method.
     *
     * @return The SharedPreferences instance.
     */
    private fun getSharedPreferences(): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

}
