/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A storage class that delegates its operations to a repository.
 * It can optionally cache the stored item in memory.
 *
 * @param T The type of the object to be stored.
 * @param delegate The repository to delegate the operations to.
 * @param cacheStrategy The strategy for caching the item in memory.
 */
class StorageDelegate<T : Any>(
    private val delegate: Storage<T>,
    private val cacheStrategy: CacheStrategy = CacheStrategy.NO_CACHE,
) : Storage<T> by delegate {
    private val lock = Mutex()
    private var cached: T? = null

    /**
     * Saves the given item in the repository and optionally in memory.
     *
     * @param item The item to save.
     */
    override suspend fun save(item: T) {

        lock.withLock {
            try {
                cached = null
                delegate.save(item)
                // Only set cache if successful and strategy is CACHE
                if (cacheStrategy == CacheStrategy.CACHE) {
                    cached = item
                }
            } catch (e: Exception) {
                // If the storage fails to persist, handle caching based on the strategy
                if (cacheStrategy == CacheStrategy.CACHE_ON_FAILURE || cacheStrategy == CacheStrategy.CACHE) {
                    cached = item
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * Retrieves the item from memory if it's cached, otherwise from the repository.
     *
     * @return The item if it exists, null otherwise.
     */
    override suspend fun get(): T? {
        lock.withLock {
            return cached ?: delegate.get()
        }
    }

    /**
     * Deletes the item from the repository and removes it from memory if it's cached.
     */
    override suspend fun delete() {
        lock.withLock {
            cached = null
            delegate.delete()
        }
    }
}

enum class CacheStrategy {
    /**
     * Cache the item in memory, even if the storage operation fails.
     */
    CACHE,

    /**
     * Do not cache the item in memory.
     */
    NO_CACHE,

    /**
     * Cache the item in memory only if the storage operation fails.
     */
    CACHE_ON_FAILURE
}
