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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@SmallTest
class DataStoreStorageTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }
    private val Context.dataStore: DataStore<Data?> by dataStore("test", DataToJsonSerializer())

    @AfterTest
    fun tearDown() =
        runTest {
            context.dataStore.updateData { null }
        }

    @TestRailCase(21605, 21611)
    @Test
    fun testDataStore() = runTest {
            val storage = DataStoreStorage(context.dataStore)
            val v = storage.get()
            assertNull(v)
            storage.save(Data(1, "test"))
            val storedData = storage.get()
            assertEquals(1, storedData!!.a)
            assertEquals("test", storedData.b)
        }

}
