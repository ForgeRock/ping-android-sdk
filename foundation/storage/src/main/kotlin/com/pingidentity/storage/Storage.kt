/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import kotlinx.serialization.Serializable

/**
 * Interface to persist and retrieve [Serializable] object.
 *
 * @param T The type of the object to be stored, which must be serializable.
 */
interface Storage<T : @Serializable Any> {
    /**
     * Saves the given item.
     *
     * @param item The item to be saved.
     */
    suspend fun save(item: T)

    /**
     * Retrieves the stored item.
     *
     * @return The stored item, or null if no item is stored.
     */
    suspend fun get(): T?

    /**
     * Deletes the stored item.
     */
    suspend fun delete()
}