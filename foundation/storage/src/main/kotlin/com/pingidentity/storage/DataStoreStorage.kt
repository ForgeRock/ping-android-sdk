/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * A repository for storing serializable objects in DataStore.
 *
 * @param T The type of the object to be stored. Must be serializable.
 * @param dataStore The DataStore instance to use for storing the object.
 */
class DataStoreStorage<T : @Serializable Any>(
    private val dataStore: DataStore<T?>,
) : Storage<T> {
    /**
     * Saves the given item in the DataStore.
     *
     * @param item The item to save.
     */
    override suspend fun save(item: T) {
        dataStore.updateData {
            item
        }
    }

    /**
     * Retrieves the item from the DataStore.
     *
     * @return The item if it exists, null otherwise.
     */
    override suspend fun get(): T? {
        return dataStore.data.first()
    }

    /**
     * Deletes the item from the DataStore.
     */
    override suspend fun delete() {
        dataStore.updateData { null }
    }
}


/**
 * Creates a new Storage instance for storing serializable objects in DataStore.
 *
 * @param T The type of the object to be stored. Must be serializable.
 * @param dataStore The DataStore instance to use for storing the object.
 * @param cacheable Whether the storage should cache the object in memory.
 *
 * @return A new Storage instance.
 */
inline fun <reified T : @Serializable Any> DataStoreStorage(
    dataStore: DataStore<T?>,
    cacheable: Boolean = false,
): StorageDelegate<T> {


    return StorageDelegate(
        DataStoreStorage(dataStore = dataStore),
        cacheable,
    )
}