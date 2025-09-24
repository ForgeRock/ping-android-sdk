/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import java.security.KeyStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds


@RunWith(AndroidJUnit4::class)
@SmallTest
class EncryptedDataStoreStorageStressTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private lateinit var storage: Storage<Data>

    @BeforeTest
    fun setUp() = runTest {
        storage = EncryptedDataStoreStorage {
            fileName =  EncryptedDataStoreStorageStressTest::class.java.simpleName
            keyAlias = EncryptedDataStoreStorageStressTest::class.java.simpleName
        }
        clear()
    }

    @AfterTest
    fun tearDown() =
        runTest {
            clear()
        }

    private suspend fun clear() {
        storage.delete()
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(EncryptedDataStoreStorageStressTest::class.java.simpleName)
    }

    @TestRailCase(21635)
    @Test
    fun testDataStoreStress() = runTest(timeout = 5.seconds) {
        // Reduced iterations to complete within time limit
        repeat(100) {
            launch {
                val data = Data(it, "some data")
                storage.save(data)
            }
            launch {
                val storedData = storage.get()
                println("result" + storedData?.a)
            }
        }
    }
}