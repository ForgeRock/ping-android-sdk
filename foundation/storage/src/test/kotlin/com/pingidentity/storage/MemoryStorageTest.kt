/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MemoryStorageTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @AfterTest
    fun tearDown() =
        runTest {
        }

    @TestRailCase(21622, 21623)
    @Test
    fun testDataStore() =
        runTest {
            val storage = MemoryStorage<Data>()
            storage.save(Data(1, "test"))
            val storedData = storage.get()
            assertEquals(1, storedData!!.a)
            assertEquals("test", storedData.b)
        }

    @TestRailCase(21624, 21625)
    @Test
    fun testMultipleData() =
        runTest {
            val storage = MemoryStorage<List<Data>>()
            val dataList = listOf(Data(1, "test1"), Data(2, "test2"))
            storage.save(dataList)
            val storedData = storage.get()
            assertEquals(dataList, storedData)
        }

    @TestRailCase(21626)
    @Test
    fun testDeleteData() =
        runTest {
            val storage = MemoryStorage<Data>()
            val data = Data(1, "test")
            storage.save(data)
            storage.delete()
            val storedData = storage.get()
            assertEquals(null, storedData)
        }

    @TestRailCase(21627)
    @Test
    fun testDifferentDataObjectsWithSameDataStore() =
        runTest {
            val storageData = MemoryStorage<Data>()
            val storageData2 = MemoryStorage<Data2>()

            val data = Data(1, "test")
            val data2 = Data2(2, "test1")

            storageData.save(data)
            storageData2.save(data2)

            val storedData = storageData.get()
            val storedData2 = storageData2.get()

            assertEquals(data, storedData)
            assertEquals(data2, storedData2)

            storageData.delete()
            assertNull(storageData.get())
            assertNotNull(storageData2.get())
        }
}
