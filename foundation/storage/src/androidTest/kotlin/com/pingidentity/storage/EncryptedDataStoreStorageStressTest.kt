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
import com.pingidentity.storage.encrypt.SecretKeyEncryptor
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import java.security.KeyStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test


@RunWith(AndroidJUnit4::class)
@SmallTest
class EncryptedDataStoreStorageStressTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private val applicationContext: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }
    private val Context.dataStore: DataStore<Data?> by dataStore(this.javaClass.simpleName, EncryptedDataToJsonSerializer(
        SecretKeyEncryptor {
            keyAlias = EncryptedDataStoreStorageStressTest::class.java.simpleName
        }
    ))

    @BeforeTest
    fun setUp() = runTest {
        clear()
    }

    @AfterTest
    fun tearDown() =
        runTest {
            clear()
        }

    private suspend fun clear() {
        applicationContext.dataStore.updateData { null }
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(EncryptedDataStoreStorageStressTest::class.java.simpleName)
    }

    @TestRailCase(21635)
    @Test
    fun testDataStoreStress() = runBlocking {
        val storage = DataStoreStorage(applicationContext.dataStore)

        //Can't really test the concurrency, the mutex for save and get are queueing the requests
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