/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
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
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.storage.encrypt.SecretKeyEncryptor
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import java.security.KeyStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


@RunWith(AndroidJUnit4::class)
@SmallTest
class EncryptedDataStoreStorageTest {

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private val applicationContext: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }
    private val encryptor = SecretKeyEncryptor {
        logger = Logger.CONSOLE
        keyAlias = EncryptedDataStoreStorageTest::class.java.simpleName
    }
    private val Context.dataStore: DataStore<Data?> by dataStore(
        this.javaClass.simpleName, EncryptedDataToJsonSerializer(encryptor)
    )

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
        keyStore.deleteEntry(EncryptedDataStoreStorageTest::class.java.simpleName)
    }

    @TestRailCase(21628, 21629)
    @Test
    fun testDataStore() =
        runTest {
            val storage = DataStoreStorage(applicationContext.dataStore)
            val v = storage.get()
            assertNull(v)
            storage.save(Data(1, "some data"))
            val storedData = storage.get()
            assertEquals(1, storedData!!.a)
            assertEquals("some data", storedData.b)

            val dataStoreFile =
                applicationContext.filesDir.resolve("datastore/${EncryptedDataStoreStorageTest::class.java.simpleName}")
            val rawData = dataStoreFile.readBytes()
            //The rawData should be encrypted and dose not contain the original data
            assertFalse(String(rawData).contains("some data"))
            assertTrue(String(encryptor.decrypt(rawData)).contains("some data"))

        }
}