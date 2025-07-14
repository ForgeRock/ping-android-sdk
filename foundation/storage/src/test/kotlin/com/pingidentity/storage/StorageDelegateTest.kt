/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class StorageDelegateTest {
    private class FakeStorage<T : Any> : Storage<T> {
        var saved: T? = null
        var shouldFail = false

        override suspend fun save(item: T) {
            if (shouldFail) throw RuntimeException("Save failed")
            saved = item
        }

        override suspend fun get(): T? = saved

        override suspend fun delete() {
            saved = null
        }
    }

    @Test
    fun `Saves and retrieves item with CACHE strategy`() = runTest {
        val storage = FakeStorage<String>()
        val delegate = StorageDelegate(storage, CacheStrategy.CACHE)
        delegate.save("item")
        assertEquals("item", delegate.get())
    }

    @Test
    fun `Retrieves item from delegate when not cached`() = runTest {
        val storage = FakeStorage<String>()
        storage.saved = "item"
        val delegate = StorageDelegate(storage, CacheStrategy.NO_CACHE)
        assertEquals("item", delegate.get())
    }

    @Test
    fun `Caches item on save failure when using CACHE_ON_FAILURE strategy`() = runTest {
        val storage = FakeStorage<String>()
        storage.shouldFail = true
        val delegate = StorageDelegate(storage, CacheStrategy.CACHE_ON_FAILURE)
        delegate.save("item")
        assertEquals("item", delegate.get())
    }

    @Test
    fun `Does not cache item on save failure when using NO_CACHE strategy`() = runTest {
        val storage = FakeStorage<String>()
        storage.shouldFail = true
        val delegate = StorageDelegate(storage, CacheStrategy.NO_CACHE)
        assertFailsWith<RuntimeException> { delegate.save("item") }
        assertNull(delegate.get())
    }

    @Test
    fun `Deletes item from cache and delegate storage`() = runTest {
        val storage = FakeStorage<String>()
        val delegate = StorageDelegate(storage, CacheStrategy.CACHE)
        delegate.save("item")
        delegate.delete()
        assertNull(delegate.get())
        assertNull(storage.saved)
    }
}