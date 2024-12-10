/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class DataStoragePreferencesTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    @AfterTest
    fun tearDown() =
        runTest {
            context.dataStore.edit {
                it.clear()
            }
        }

    @TestRailCase(21616, 21617)
    @Test
    fun testDataStore() =
        runTest {
            val storage = DataStorePreferencesStorage<Data>(context.dataStore)
            storage.save(Data(1, "test"))
            val storedData = storage.get()
            assertEquals(1, storedData!!.a)
            assertEquals("test", storedData.b)
        }

    @TestRailCase(21618, 21619)
    @Test
    fun testMultipleData() =
        runTest {
            val storage = DataStorePreferencesStorage<List<Data>>(context.dataStore)
            val dataList = listOf(Data(1, "test1"), Data(2, "test2"))
            storage.save(dataList)
            val storedData = storage.get()
            assertEquals(dataList, storedData)
        }

    @TestRailCase(21620)
    @Test
    fun testDeleteData() =
        runTest {
            val storage = DataStorePreferencesStorage<Data>(context.dataStore)
            val data = Data(1, "test")
            storage.save(data)
            storage.delete()
            val storedData = storage.get()
            assertEquals(null, storedData)
        }

    @TestRailCase(21621)
    @Test
    fun testDifferentDataObjectsWithSameDataStore() =
        runTest {
            val storageData = DataStorePreferencesStorage<Data>(context.dataStore)
            val storageData2 = DataStorePreferencesStorage<Data2>(context.dataStore)

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
