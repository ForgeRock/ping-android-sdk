/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pingidentity.utils.TestModeDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom

/**
 * Manages the secure storage and retrieval of passphrases used for database encryption.
 * This class supports storing a random or given passphrase either in Android KeyStore, Google BlockStore,
 * or locally.
 */
class PassphraseManager(
    private val context: Context, 
    private val customPassphrase: String? = null,
    private val blockStorePreferred: Boolean = false
) {

    companion object {
        private const val TAG = "PassphraseManager"

        // Constants for passphrase storage
        private const val PASSPHRASE_KEY = "database_passphrase"
        private const val PASSPHRASE_LENGTH_BYTES = 32 // 256 bits

        // Storage method tracking
        private const val DATASTORE_NAME = "com.pingidentity.storage.passphrase_prefs"
        private const val PREF_STORAGE_METHOD = "passphrase_storage_method"
        private const val STORAGE_METHOD_NONE = 0
        private const val STORAGE_METHOD_KEYSTORE = 1
        private const val STORAGE_METHOD_BLOCKSTORE = 2
        private const val STORAGE_METHOD_LOCALKEYSTORE = 3

        // Fixed test passphrase for instrumentation tests
        private const val TEST_PASSPHRASE = "test_passphrase"

        // Create a DataStore for the preferences
        private val Context.dataStore by preferencesDataStore(DATASTORE_NAME)
    }

    // DataStore storage for passphrase storage method
    private val storageMethodKey = intPreferencesKey(PREF_STORAGE_METHOD)
    private val dataStore = context.dataStore

    // Storage helpers
    private val passphraseEncryptor by lazy { PassphraseEncryptor(context) }
    private val blockStoreHelper by lazy { BlockStoreHelper(context) }
    private val localKeyStoreHelper by lazy { LocalKeyStoreHelper(context) }

    // Flag indicating if we're running in a test environment
    private val isTestEnvironment by lazy { TestModeDetector.isRunningInTestEnvironment() }

    /**
     * Gets the existing passphrase or generates a new one if it doesn't exist.
     * If a custom passphrase was provided in the constructor, it will be used instead.
     *
     * @return The database passphrase.
     */
    fun getOrCreatePassphrase(): String {
        // If a custom passphrase was provided, use it
        if (customPassphrase != null) {
            Log.d(TAG, "Using custom passphrase")
            return customPassphrase
        }
        
        // Use a fixed passphrase for testing
        if (isTestEnvironment) {
            Log.d(TAG, "Using test passphrase for SQLCipher")
            return TEST_PASSPHRASE
        }

        var passphrase: String? = null
        val storedMethod = getStoredMethod()

        // Try to retrieve existing passphrase using the previously used storage method
        when (storedMethod) {
            STORAGE_METHOD_KEYSTORE -> passphrase = retrieveFromKeyStore()
            STORAGE_METHOD_BLOCKSTORE -> passphrase = retrieveFromBlockStore()
            STORAGE_METHOD_LOCALKEYSTORE -> passphrase = retrieveFromLocalKeyStore()
        }

        // If no storage method is set yet or retrieval failed, try according to preference
        if (storedMethod == STORAGE_METHOD_NONE || passphrase == null) {
            if (blockStorePreferred) {
                // Try BlockStore first, then KeyStore, then LocalKeyStore
                Log.d(TAG, "Trying BlockStore first")
                passphrase = retrieveFromBlockStore()
                
                if (passphrase == null) {
                    Log.d(TAG, "BlockStore failed, trying KeyStore")
                    passphrase = retrieveFromKeyStore()
                    
                    if (passphrase == null) {
                        Log.d(TAG, "KeyStore failed, trying LocalKeyStore")
                        passphrase = retrieveFromLocalKeyStore()
                    }
                }
            } else {
                // Try LocalKeyStore first, then KeyStore, then BlockStore
                Log.d(TAG, "Trying LocalKeyStore first")
                passphrase = retrieveFromLocalKeyStore()
                
                if (passphrase == null) {
                    Log.d(TAG, "LocalKeyStore failed, trying KeyStore")
                    passphrase = retrieveFromKeyStore()
                    
                    if (passphrase == null) {
                        Log.d(TAG, "KeyStore failed, trying BlockStore")
                        passphrase = retrieveFromBlockStore()
                    }
                }
            }
        }

        // If still no passphrase, generate and store a new one
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
            val result = passphraseEncryptor.decrypt(PASSPHRASE_KEY)
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
     * Retrieves passphrase from BlockStore.
     * 
     * @return The passphrase or null if not found or error occurred.
     */
    private fun retrieveFromBlockStore(): String? {
        try {
            val passphraseBytes = blockStoreHelper.retrieveBytes(PASSPHRASE_KEY)
            if (passphraseBytes != null) {
                setStoredMethod(STORAGE_METHOD_BLOCKSTORE)
                val passphrase = String(passphraseBytes, Charsets.UTF_8)
                Log.d(TAG, "Successfully retrieved passphrase from BlockStore")
                return passphrase
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve passphrase from BlockStore: ${e.message}")
        }
        return null
    }
    
    /**
     * Retrieves passphrase from LocalKeyStore.
     * 
     * @return The passphrase or null if not found or error occurred.
     */
    private fun retrieveFromLocalKeyStore(): String? {
        try {
            if (localKeyStoreHelper.hasBlock(PASSPHRASE_KEY)) {
                val passphraseBytes = localKeyStoreHelper.retrieveBytes(PASSPHRASE_KEY)
                if (passphraseBytes != null) {
                    setStoredMethod(STORAGE_METHOD_LOCALKEYSTORE)
                    val passphrase = String(passphraseBytes, Charsets.UTF_8)
                    Log.d(TAG, "Successfully retrieved passphrase from LocalKeyStore")
                    return passphrase
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve passphrase from LocalKeyStore: ${e.message}")
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
     * Store a new passphrase using the preferred storage method.
     *
     * @param passphrase The passphrase to store.
     */
    private fun storeNewPassphrase(passphrase: String) {
        val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)
        var stored = false
        
        if (blockStorePreferred) {
            // Try BlockStore first
            try {
                stored = blockStoreHelper.storeBytes(PASSPHRASE_KEY, passphraseBytes)
                if (stored) {
                    setStoredMethod(STORAGE_METHOD_BLOCKSTORE)
                    Log.d(TAG, "Successfully stored passphrase in BlockStore")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to store passphrase in BlockStore: ${e.message}")
            }
            
            // Try LocalKeyStore second
            if (!stored) {
                try {
                    localKeyStoreHelper.storeBytes(PASSPHRASE_KEY, passphraseBytes)
                    setStoredMethod(STORAGE_METHOD_LOCALKEYSTORE)
                    Log.d(TAG, "Successfully stored passphrase in LocalKeyStore")
                    stored = true
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to store passphrase in LocalKeyStore: ${e.message}")
                }
            }
        } else {
            // Try LocalKeyStore first
            try {
                localKeyStoreHelper.storeBytes(PASSPHRASE_KEY, passphraseBytes)
                setStoredMethod(STORAGE_METHOD_LOCALKEYSTORE)
                Log.d(TAG, "Successfully stored passphrase in LocalKeyStore")
                stored = true
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to store passphrase in LocalKeyStore: ${e.message}")
            }
            
            // Try BlockStore second
            if (!stored) {
                try {
                    stored = blockStoreHelper.storeBytes(PASSPHRASE_KEY, passphraseBytes)
                    if (stored) {
                        setStoredMethod(STORAGE_METHOD_BLOCKSTORE)
                        Log.d(TAG, "Successfully stored passphrase in BlockStore")
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to store passphrase in BlockStore: ${e.message}")
                }
            }
        }
        
        // Finally, try KeyStore as last resort
        if (!stored) {
            try {
                passphraseEncryptor.encrypt(PASSPHRASE_KEY, passphrase)
                setStoredMethod(STORAGE_METHOD_KEYSTORE)
                Log.d(TAG, "Successfully stored passphrase in KeyStore")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store passphrase in any available storage: ${e.message}")
            }
        }
    }

    /**
     * Get the previously used storage method.
     *
     * @return The storage method ID.
     */
    private fun getStoredMethod(): Int {
        return runBlocking {
            val preferences = dataStore.data.first()
            preferences[storageMethodKey] ?: STORAGE_METHOD_NONE
        }
    }

    /**
     * Set the storage method used.
     *
     * @param method The storage method ID.
     */
    private fun setStoredMethod(method: Int) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[storageMethodKey] = method
            }
        }
    }
}