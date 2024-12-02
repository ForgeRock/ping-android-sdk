/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

/**
 * A repository for storing objects in memory.
 *
 * @param T The type of the object to be stored.
 */
class Memory<T : Any> : Storage<T> {
    private var data: T? = null

    /**
     * Saves the given item in memory.
     *
     * @param item The item to save.
     */
    override suspend fun save(item: T) {
        data = item
    }

    /**
     * Retrieves the item from memory.
     *
     * @return The item if it exists, null otherwise.
     */
    override suspend fun get(): T? = data

    /**
     * Deletes the item from memory.
     */
    override suspend fun delete() {
        data = null
    }
}

/**
 * Creates a new Storage instance for storing objects in memory.
 *
 * @param T The type of the object to be stored.
 *
 * @return A new Storage instance.
 */
inline fun <reified T : Any> MemoryStorage(): StorageDelegate<T> = StorageDelegate(Memory())
