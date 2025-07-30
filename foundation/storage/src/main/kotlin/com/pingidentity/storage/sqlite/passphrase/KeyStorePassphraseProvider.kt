/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite.passphrase

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pingidentity.logger.Logger
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.coroutineContext

/**
 * PassphraseProvider implementation that securely stores the passphrase
 * using Android's KeyStore system.
 */
class KeyStorePassphraseProvider(
    private val context: Context,
    private val initialPassphrase: String? = null,
    private val logger: Logger = Logger.logger
) : PassphraseProvider {

    companion object {
        private const val TAG = "KeyStorePassphraseProvider"
        private const val DATASTORE_NAME = "com.pingidentity.storage.keystore_passphrase_store"
        private const val KEY_ALIAS = "ping_identity_db_passphrase_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val DEFAULT_PASSPHRASE_LENGTH_BYTES = 32 // 256 bits
        
        // DataStore preference keys
        private val ENCRYPTED_PASSPHRASE_KEY = stringPreferencesKey("encrypted_passphrase")
        private val PASSPHRASE_IV_KEY = stringPreferencesKey("passphrase_iv")
        
        // Define DataStore as a singleton
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(DATASTORE_NAME)
    }
    
    // Access the DataStore through the companion object
    private val dataStore = context.dataStore

    override suspend fun getPassphrase(): String {
        try {
            // Try to retrieve an existing passphrase first
            val existingPassphrase = retrievePassphrase()
            if (existingPassphrase != null) {
                logger.d("Retrieved existing passphrase from DataStore using KeyStore encryption")
                return existingPassphrase
            }
            
            // If an initial passphrase was provided, use and store it
            if (initialPassphrase != null) {
                logger.d("Using provided initial passphrase")
                storePassphrase(initialPassphrase)
                return initialPassphrase
            }

            // Generate and store a new passphrase
            val newPassphrase = PassphraseProvider.generateRandomPassphrase()
            storePassphrase(newPassphrase)
            logger.d("Generated and stored new passphrase in DataStore with KeyStore encryption")
            return newPassphrase
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Error in getPassphrase: ${e.message}", e)
            throw e
        }
    }

    /**
     * Retrieves the stored passphrase from the Android KeyStore.
     *
     * @return The passphrase, or null if it doesn't exist or can't be retrieved.
     */
    private suspend fun retrievePassphrase(): String? {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            // Check if our key exists
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                logger.d("No passphrase key found in KeyStore")
                return null
            }

            // Get the encryption key
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey

            // Get the stored encrypted data and IV from DataStore
            val preferences = dataStore.data.first()
            
            val encryptedData = preferences[ENCRYPTED_PASSPHRASE_KEY] ?: return null
            val ivPrefs = preferences[PASSPHRASE_IV_KEY] ?: return null

            val iv = Base64.decode(ivPrefs, Base64.DEFAULT)
            val encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT)

            // Decrypt the data
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve passphrase from KeyStore: ${e.message}", e)
            return null
        }
    }

    /**
     * Stores a passphrase in the Android KeyStore.
     *
     * @param passphrase The passphrase to store.
     */
    private suspend fun storePassphrase(passphrase: String) {
        try {
            // Get or create the encryption key
            val secretKey = getOrCreateSecretKey()

            // Generate a random IV
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv

            // Encrypt the passphrase
            val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)
            val encryptedBytes = cipher.doFinal(passphraseBytes)

            // Store the encrypted passphrase and IV in DataStore
            val encryptedPassphrase = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
            val encodedIv = Base64.encodeToString(iv, Base64.DEFAULT)
            
            dataStore.edit { preferences ->
                preferences[ENCRYPTED_PASSPHRASE_KEY] = encryptedPassphrase
                preferences[PASSPHRASE_IV_KEY] = encodedIv
            }

            logger.d("Successfully stored passphrase in DataStore with KeyStore encryption")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to store passphrase in KeyStore: ${e.message}", e)
            throw e
        }
    }

    /**
     * Gets or creates a secret key for encryption/decryption.
     *
     * @return The secret key.
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Check if key already exists
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }

        // Generate a new key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

}
