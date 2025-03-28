/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/**
 * A repository for storing serializable objects in DataStore Preferences.
 *
 * @param T The type of the object to be stored. Must be serializable.
 * @param key The key to use for storing the object in the DataStore Preferences.
 * @param serializer The serializer to use for serializing the object.
 * @param dataStore The DataStore instance to use for storing the preferences.
 */
class DataStorePreferencesStorage<T : @Serializable Any>(
    key: String,
    private val serializer: KSerializer<T>,
    private val dataStore: DataStore<Preferences>,
) : Storage<T> {
    private val preferencesKey = stringPreferencesKey(key)

    /**
     * Saves the given item in the DataStore Preferences.
     *
     * @param item The item to save.
     */
    override suspend fun save(item: T) {
        dataStore.edit { preferences ->
            preferences[preferencesKey] = json.encodeToString(serializer, item)
        }
    }

    /**
     * Retrieves the item from the DataStore Preferences.
     *
     * @return The item if it exists, null otherwise.
     */
    override suspend fun get(): T? {
        return dataStore.data.map {
            it[preferencesKey]
        }.map {
            it?.let {
                json.decodeFromString(serializer, it)
            }
        }.first()
    }

    /**
     * Deletes the item from the DataStore Preferences.
     */
    override suspend fun delete() {
        dataStore.edit {
            it.remove(preferencesKey)
        }
    }
}

/**
 * Creates a new Storage instance for storing serializable objects in DataStore Preferences.
 *
 * @param T The type of the object to be stored. Must be serializable.
 * @param dataStore The DataStore instance to use for storing the preferences.
 * @param cacheable Whether the storage should cache the object in memory.
 *
 * @return A new Storage instance.
 */
inline fun <reified T : @Serializable Any> DataStorePreferencesStorage(
    dataStore: DataStore<Preferences>,
    cacheable: Boolean = false,
): StorageDelegate<T> =

    StorageDelegate(
        DataStorePreferencesStorage(
            key = T::class.java.name,
            dataStore = dataStore,
            serializer = json.serializersModule.serializer(),
        ),
        cacheable,
    )
