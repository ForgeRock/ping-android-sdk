/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite.passphrase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pingidentity.logger.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * PassphraseProvider implementation that stores the passphrase using Android's DataStore.
 * This is the recommended default provider as it provides a good balance of security and convenience.
 */
class DataStorePassphraseProvider(
    private val context: Context,
    private val initialPassphrase: String? = null,
    private val logger: Logger = Logger.logger
) : PassphraseProvider {

    companion object {
        private const val DATASTORE_NAME = "com.pingidentity.storage.passphrase_store"
        private val PASSPHRASE_KEY = stringPreferencesKey("database_passphrase")
        
        // Create a DataStore instance using a singleton pattern
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(DATASTORE_NAME)
    }

    // Get the DataStore from the context
    private val dataStore = context.dataStore

    override suspend fun getPassphrase(): String {
        // Try to retrieve existing passphrase first
        val existingPassphrase = retrievePassphrase()
        if (existingPassphrase != null) {
            logger.d("Retrieved existing passphrase from DataStore")
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
        logger.d("Generated and stored new passphrase in DataStore")
        return newPassphrase
    }

    /**
     * Retrieves the passphrase from DataStore.
     *
     * @return The passphrase, or null if it doesn't exist.
     */
    private suspend fun retrievePassphrase(): String? {
        return dataStore.data.map { preferences ->
            preferences[PASSPHRASE_KEY]
        }.first()
    }

    /**
     * Stores a passphrase in DataStore.
     *
     * @param passphrase The passphrase to store.
     */
    private suspend fun storePassphrase(passphrase: String) {
        dataStore.edit { preferences ->
            preferences[PASSPHRASE_KEY] = passphrase
        }
    }

}
