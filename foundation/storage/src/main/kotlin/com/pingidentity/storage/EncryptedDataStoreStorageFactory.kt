/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.pingidentity.android.ContextProvider
import com.pingidentity.storage.encrypt.SecretKeyEncryptor
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * A singleton factory for managing multiple [DataStoreStorage] instances.
 * Each Storage is identified by a unique filename.
 */
object EncryptedDataStoreStorageFactory {

    // Use ConcurrentHashMap for thread-safe access to the map
    val dataStoreMap = ConcurrentHashMap<String, Storage<*>>()
    val mutex = Any()

    /**
     * Creates and caches a DataStore instance for the given filename.
     * If a DataStore with the same filename already exists in the cache, returns the existing instance.
     * The store will not be recreated if provided with different configuration attributes on the provided [EncryptedDataStoreStorageConfig].
     *
     * @param block A lambda to configure the [EncryptedDataStoreStorageConfig].
     * @return The newly created and cached [DataStoreStorage] instance.
     * or if a DataStore with the given filename already exists in the cache.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : @Serializable Any> getOrCreate(
        block: EncryptedDataStoreStorageConfig.() -> Unit
    ): Storage<T> {
        val config = EncryptedDataStoreStorageConfig().apply(block)
        val fileName = config.fileName

        dataStoreMap[fileName]?.let { return it as Storage<T> }

        synchronized(mutex) {
            dataStoreMap[fileName]?.let { return it as Storage<T> }

            val file = File(
                ContextProvider.context.filesDir,
                "datastore/$fileName"
            )
            
            val storage: Storage<T> =
                DataStoreStorage(
                    DataStoreFactory.create(
                        serializer = EncryptedDataToJsonSerializer(SecretKeyEncryptor(config)),
                        produceFile = { file },
                        corruptionHandler = ReplaceFileCorruptionHandler { null }
                    ), 
                    config.cacheStrategy,
                    if (config.removeFileOnDelete) file else null
                )
            dataStoreMap[fileName] = storage
            return storage
        }
    }
}
