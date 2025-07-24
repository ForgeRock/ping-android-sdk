/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.File
import java.security.KeyStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EncryptedDataStoreStorageFactoryTest {

    private val applicationContext: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }
    private val testFile1 = "datastore_test_file1"
    private val testFile2 = "datastore_test_file2"
    private val keyAlias1 = "${DataStoreStorageWithEncryptorTest::class.java.simpleName}1"
    private val keyAlias2 = "${DataStoreStorageWithEncryptorTest::class.java.simpleName}2"

    @BeforeTest
    fun setUp() {
        ContextProvider.init(applicationContext)
    }

    @AfterTest
    fun tearDown() {
        // Delete file
        val file1 = File(ContextProvider.context.filesDir, "datastore/$testFile1")
        if (file1.exists()) {
            file1.delete()
        }
        val file2 = File(ContextProvider.context.filesDir, "datastore/$testFile2")
        if (file2.exists()) {
            file2.delete()
        }

        // Delete key from keystore
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(keyAlias1)
        keyStore.deleteEntry(keyAlias2)

    }

    @Test
    fun getOrCreate_returnsSameInstanceForSameFileName() =
        runTest {
            val storage1 = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
                fileName = testFile1
                keyAlias = keyAlias1
            }
            storage1.save(Data(1, "test1"))
            val storage2 = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
                fileName = testFile1
                keyAlias = keyAlias2
            }
            storage2.save(Data(1, "test2"))
            assertEquals(storage1, storage2)
        }

    @Test
    fun getOrCreate_createsDifferentInstancesForDifferentFileNames() =
        runTest {
            val storage1 = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
                fileName = testFile1
                keyAlias = keyAlias1
            }
            storage1.save(Data(1, "test1"))
            val storage2 = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
                fileName = testFile2
                keyAlias = keyAlias2
            }
            storage2.save(Data(1, "test2"))
            assertNotEquals(storage1, storage2)
        }

    @Test
    fun getOrCreate_returnsInstanceAfterConcurrentAccess() = runTest {
        val results = mutableListOf<Storage<Data>>()
        kotlinx.coroutines.coroutineScope {
            val jobs = List(10) {
                launch {
                    val storage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
                        fileName = testFile1
                        keyAlias = keyAlias1
                    }
                    synchronized(results) { results.add(storage) }
                }
            }
            jobs.forEach { it.join() }
        }
        assertTrue(results.all { it == results.first() })
    }
}