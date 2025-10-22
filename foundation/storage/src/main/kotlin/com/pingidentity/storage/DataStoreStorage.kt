/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import androidx.datastore.core.DataStore
import com.pingidentity.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * A repository for storing serializable objects in DataStore.
 *
 * @param T The type of the object to be stored. Must be serializable.
 * @param dataStore The DataStore instance to use for storing the object.
 * @param file Optional file reference for physical file deletion. If provided, enables deleteFile() functionality.
 */
class DataStoreStorage<T : @Serializable Any>(
    private val dataStore: DataStore<T?>,
    private val file: File? = null,
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
     * Deletes the item from the DataStore by setting it to null.
     * This clears the data content but does not delete the physical file.
     * To delete the physical file, use deleteFile() instead.
     */
    override suspend fun delete() {
        dataStore.updateData { null }
        withContext(Dispatchers.IO) {
            try {
                file?.delete()
            } catch (e: Exception) {
                // Ignore file deletion errors
                Logger.logger.w("Failed to delete file: ${file?.absolutePath}", e)
            }
        }
    }
}


/**
 * Creates a new Storage instance for storing serializable objects in DataStore.
 *
 * @param T The type of the object to be stored. Must be serializable.
 * @param dataStore The DataStore instance to use for storing the object.
 * @param cacheStrategy Cache strategy to use for caching the item in memory.
 *
 * @return A new Storage instance.
 */
inline fun <reified T : @Serializable Any> DataStoreStorage(
    dataStore: DataStore<T?>,
    cacheStrategy: CacheStrategy = CacheStrategy.NO_CACHE,
    file: File? = null,
): Storage<T> {

    return StorageDelegate(
        DataStoreStorage(dataStore = dataStore, file = file),
        cacheStrategy,
    )
}