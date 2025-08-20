/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class DataStorageTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private val testContext: Context = ApplicationProvider.getApplicationContext()
    private val testCoroutineScheduler = TestCoroutineScheduler()
    @OptIn(ExperimentalCoroutinesApi::class)
    private val testScope = TestScope(UnconfinedTestDispatcher(testCoroutineScheduler))

    private lateinit var dataStore: DataStore<Data?>
    private lateinit var dataStoreList: DataStore<List<Data>?>
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreListFile: File

    @BeforeTest
    fun setup() {
        // Use a unique file name for each test to ensure isolation
        val fileName = "test_data_${System.currentTimeMillis()}.pb"
        dataStoreFile = testContext.dataStoreFile(fileName)

        dataStore = DataStoreFactory.create(
            serializer = DataToJsonSerializer(), // Your actual serializer
            scope = testScope, // Use the test scope
            produceFile = { dataStoreFile }
        )

        val fileNameList = "test_datalist_${System.currentTimeMillis()}.pb"
        dataStoreListFile = testContext.dataStoreFile(fileNameList)

        dataStoreList = DataStoreFactory.create(
            serializer = DataToJsonSerializer(), // Your actual serializer
            scope = testScope, // Use the test scope
            produceFile = { dataStoreListFile }
        )


    }

    @AfterTest
    fun tearDown() {
        // Crucially, delete the DataStore file after each test
        if (dataStoreFile.exists()) {
            dataStoreFile.delete()
        }

        if (dataStoreListFile.exists()) {
            dataStoreListFile.delete()
        }
    }

    @TestRailCase(21605, 21611)
    @Test
    fun testDataStore() =
        runTest {
            val storage = DataStoreStorage(dataStore)
            //val storage = DataStoreStorageFactory.getOrCreate<Data>("test")
            storage.save(Data(1, "test"))
            val storedData = storage.get()
            assertEquals(1, storedData!!.a)
            assertEquals("test", storedData.b)
        }

    @TestRailCase(21606, 21612)
    @Test
    fun testMultipleData() =
        runTest {
            val storage = DataStoreStorage(dataStoreList)
            val dataList = listOf(Data(1, "test1"), Data(2, "test2"))
            storage.save(dataList)
            val storedData = storage.get()
            assertEquals(dataList, storedData)
        }

    @TestRailCase(21607)
    @Test
    fun testDeleteData() =
        runTest {
            val storage = DataStoreStorage(dataStore)
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
            val storage = DataStoreStorage(dataStore)
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
            val storage = DataStoreStorage(dataStore, cacheStrategy = CacheStrategy.CACHE)
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
            val storage = DataStoreStorage(dataStore, CacheStrategy.CACHE)
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
