/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
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
 * @param cacheable Whether the storage should cache the object in memory.
 */
class StorageDelegate<T : Any>(
    private val delegate: Storage<T>,
    private val cacheable: Boolean = false,
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
            delegate.save(item)
            if (cacheable) {
                cached = item
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
            delegate.delete()
            if (cacheable) {
                cached = null
            }
        }
    }
}
