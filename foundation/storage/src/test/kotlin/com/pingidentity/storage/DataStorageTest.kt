/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.rules.TestWatcher

@RunWith(RobolectricTestRunner::class)
class DataStorageTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }

    private val Context.dataStore: DataStore<Data?> by dataStore("test", DataToJsonSerializer())
    private val Context.dataStoreList: DataStore<List<Data>?> by dataStore("test-list", DataToJsonSerializer())

    @AfterTest
    fun tearDown() =
        runTest {
            context.dataStore.updateData { null }
        }

    @TestRailCase(21605, 21611)
    @Test
    fun testDataStore() =
        runTest {
            val storage = DataStoreStorage(context.dataStore)
            storage.save(Data(1, "test"))
            val storedData = storage.get()
            assertEquals(1, storedData!!.a)
            assertEquals("test", storedData.b)
        }

    @TestRailCase(21606, 21612)
    @Test
    fun testMultipleData() =
        runTest {
            val storage = DataStoreStorage(context.dataStoreList)
            val dataList = listOf(Data(1, "test1"), Data(2, "test2"))
            storage.save(dataList)
            val storedData = storage.get()
            assertEquals(dataList, storedData)
        }

    @TestRailCase(21607)
    @Test
    fun testDeleteData() =
        runTest {
            val storage = DataStoreStorage(context.dataStore)
            val data = Data(1, "test")
            storage.save(data)
            storage.delete()
            val storedData = storage.get()
            assertEquals(null, storedData)
        }

    @TestRailCase(21613)
    @Test
    fun testOverwriteData() =
        runTest {
            val storage = DataStoreStorage(context.dataStore)
            storage.save(Data(1, "test1"))
            val storedData = storage.get()
            assertEquals(1, storedData!!.a)
            assertEquals("test1", storedData.b)

            storage.save(Data(2, "test2"))
            val storedData1 = storage.get()
            assertEquals(2, storedData1!!.a)
            assertEquals("test2", storedData1.b)
        }

    @TestRailCase(21614)
    @Test
    fun testDataStoreCacheDelete() =
        runTest {
            val storage = DataStoreStorage(context.dataStore, cacheable = true)
            storage.save(Data(1, "test1"))

            var storedData = storage.get()
            assertEquals(1, storedData!!.a)
            assertEquals("test1", storedData.b)

            storage.delete()
            storedData = storage.get()
            assertEquals(null, storedData)
        }

    @TestRailCase(21615)
    @Test
    fun testDataStoreCacheUpdate() =
        runTest {
            val storage = DataStoreStorage(context.dataStore, cacheable = true)
            storage.save(Data(1, "test1"))

            var storedData = storage.get()
            assertEquals(1, storedData!!.a)
            assertEquals("test1", storedData.b)

            storage.save(Data(2, "test2"))
            storedData = storage.get()
            assertEquals(2, storedData!!.a)
            assertEquals("test2", storedData.b)
        }
}
