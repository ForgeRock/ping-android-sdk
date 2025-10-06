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
import kotlinx.serialization.Serializable
import java.io.File
import java.security.KeyStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedDataStoreStorageFactoryTest {

    @Serializable
    data class Data(val id: Int, val value: String)

    @Serializable
    data class UserProfile(val name: String, val email: String, val age: Int)

    private val applicationContext: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }

    // Generate unique file names for each test run to prevent DataStore conflicts
    private val testId = System.currentTimeMillis().toString() + "_" + (0..9999).random()
    private val testFile1 = "datastore_test_file1_$testId"
    private val testFile2 = "datastore_test_file2_$testId"
    private val testFile3 = "datastore_test_file3_$testId"
    private val testFile4 = "datastore_test_file4_$testId"
    private val keyAlias1 = "${EncryptedDataStoreStorageFactoryTest::class.java.simpleName}1_$testId"
    private val keyAlias2 = "${EncryptedDataStoreStorageFactoryTest::class.java.simpleName}2_$testId"
    private val keyAlias3 = "${EncryptedDataStoreStorageFactoryTest::class.java.simpleName}3_$testId"
    private val keyAlias4 = "${EncryptedDataStoreStorageFactoryTest::class.java.simpleName}4_$testId"

    @BeforeTest
    fun setUp() {
        ContextProvider.init(applicationContext)

        // Clean up any existing files before test
        listOf(testFile1, testFile2, testFile3, testFile4).forEach { fileName ->
            val file = File(ContextProvider.context.filesDir, "datastore/$fileName")
            if (file.exists()) {
                file.delete()
            }
        }

        // Clean up any existing keys before test
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        listOf(keyAlias1, keyAlias2, keyAlias3, keyAlias4).forEach { keyAlias ->
            try {
                keyStore.deleteEntry(keyAlias)
            } catch (e: Exception) {
                // Ignore if key doesn't exist
            }
        }

        // Clear factory cache before each test to prevent DataStore conflicts
        EncryptedDataStoreStorageFactory.dataStoreMap.clear()
    }

    @AfterTest
    fun tearDown() {
        // Delete files
        listOf(testFile1, testFile2, testFile3, testFile4).forEach { fileName ->
            val file = File(ContextProvider.context.filesDir, "datastore/$fileName")
            if (file.exists()) {
                file.delete()
            }
        }

        // Delete keys from keystore
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        listOf(keyAlias1, keyAlias2, keyAlias3, keyAlias4).forEach { keyAlias ->
            try {
                keyStore.deleteEntry(keyAlias)
            } catch (e: Exception) {
                // Ignore if key doesn't exist
            }
        }

        // Clear factory cache after each test
        EncryptedDataStoreStorageFactory.dataStoreMap.clear()
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

    @Test
    fun getOrCreate_encryptsAndDecryptsDataCorrectly() = runTest {
        val storage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias1
        }

        val originalData = Data(42, "sensitive_data")
        storage.save(originalData)

        val retrievedData = storage.get()
        assertEquals(originalData, retrievedData)

        // Verify file exists and contains encrypted data (not plain text)
        val file = File(ContextProvider.context.filesDir, "datastore/$testFile1")
        assertTrue(file.exists())
        val fileContent = file.readText()
        assertTrue(fileContent.isNotEmpty())
        // Should not contain plain text
        assertTrue(!fileContent.contains("sensitive_data"))
    }

    @Test
    fun getOrCreate_handlesDifferentDataTypes() = runTest {
        val dataStorage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias1
        }

        val userStorage = EncryptedDataStoreStorageFactory.getOrCreate<UserProfile> {
            fileName = testFile2
            keyAlias = keyAlias2
        }

        val testData = Data(123, "test_value")
        val testUser = UserProfile("John Doe", "john@example.com", 30)

        dataStorage.save(testData)
        userStorage.save(testUser)

        assertEquals(testData, dataStorage.get())
        assertEquals(testUser, userStorage.get())
        assertEquals(2, EncryptedDataStoreStorageFactory.dataStoreMap.size)
    }

    @Test
    fun getOrCreate_handlesNullData() = runTest {
        val storage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias1
        }

        // Initially should be null
        assertNull(storage.get())

        // Save data then verify
        storage.save(Data(1, "test"))
        assertNotNull(storage.get())

        // Delete and verify null again
        storage.delete()
        assertNull(storage.get())
    }

    @Test
    fun getOrCreate_handlesDataOverwrite() = runTest {
        val storage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias1
        }

        val firstData = Data(1, "first")
        val secondData = Data(2, "second")

        storage.save(firstData)
        assertEquals(firstData, storage.get())

        storage.save(secondData)
        assertEquals(secondData, storage.get())
    }

    @Test
    fun getOrCreate_withDifferentCacheStrategies() = runTest {
        val noCacheStorage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias1
            cacheStrategy = CacheStrategy.NO_CACHE
        }

        val cacheStorage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile2
            keyAlias = keyAlias2
            cacheStrategy = CacheStrategy.CACHE
        }

        noCacheStorage.save(Data(1, "no_cache"))
        cacheStorage.save(Data(2, "cache"))

        assertEquals(Data(1, "no_cache"), noCacheStorage.get())
        assertEquals(Data(2, "cache"), cacheStorage.get())
    }

    @Test
    fun getOrCreate_deletesFileOnStorageDelete() = runTest {
        val storage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            removeFileOnDelete = true
            fileName = testFile1
            keyAlias = keyAlias1
        }

        storage.save(Data(123, "to_delete"))

        val file = File(ContextProvider.context.filesDir, "datastore/$testFile1")
        assertTrue(file.exists())
        assertNotNull(storage.get())

        storage.delete()
        assertTrue(!file.exists())
        assertNull(storage.get())
    }

    @Test
    fun getOrCreate_createsDataStoreDirectory() = runTest {
        val storage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias1
        }

        storage.save(Data(1, "test"))

        val datastoreDir = File(ContextProvider.context.filesDir, "datastore")
        assertTrue(datastoreDir.exists())
        assertTrue(datastoreDir.isDirectory)
    }

    @Test
    fun getOrCreate_handlesLargeData() = runTest {
        val storage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias1
        }

        val largeValue = "x".repeat(10000) // 10KB string
        val largeData = Data(1, largeValue)

        storage.save(largeData)
        assertEquals(largeData, storage.get())
    }

    @Test
    fun getOrCreate_maintainsCacheMapCorrectly() = runTest {
        assertEquals(0, EncryptedDataStoreStorageFactory.dataStoreMap.size)

        EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias1
        }
        assertEquals(1, EncryptedDataStoreStorageFactory.dataStoreMap.size)

        EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile2
            keyAlias = keyAlias2
        }
        assertEquals(2, EncryptedDataStoreStorageFactory.dataStoreMap.size)

        // Same filename should not increase cache size
        EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias3
        }
        assertEquals(2, EncryptedDataStoreStorageFactory.dataStoreMap.size)
    }

    @Test
    fun getOrCreate_handlesSpecialCharactersInData() = runTest {
        val storage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias1
        }

        val specialData = Data(1, "Special chars: !@#$%^&*()_+{}|:<>?[]\\;'\",./ åæø αβγ 中文 🚀")
        storage.save(specialData)
        assertEquals(specialData, storage.get())
    }

    @Test
    fun getOrCreate_handlesEmptyStrings() = runTest {
        val storage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias1
        }

        val emptyData = Data(0, "")
        storage.save(emptyData)
        assertEquals(emptyData, storage.get())
    }

    @Test
    fun getOrCreate_worksWithDifferentKeyAliases() = runTest {
        // Create storage with first key
        val storage1 = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias1
        }
        storage1.save(Data(1, "key1_data"))

        // Create storage with different key but same file should return same instance
        val storage2 = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias2
        }

        assertEquals(storage1, storage2)
        assertEquals(Data(1, "key1_data"), storage2.get())
    }

    @Test
    fun getOrCreate_handlesMultipleSaveOperations() = runTest {
        val storage = EncryptedDataStoreStorageFactory.getOrCreate<Data> {
            fileName = testFile1
            keyAlias = keyAlias1
        }

        // Perform multiple saves rapidly
        repeat(10) { i ->
            storage.save(Data(i, "iteration_$i"))
            assertEquals(Data(i, "iteration_$i"), storage.get())
        }
    }

    @Test
    fun getOrCreate_maintainsDataIntegrityAfterMultipleOperations() = runTest {
        val storage = EncryptedDataStoreStorageFactory.getOrCreate<UserProfile> {
            fileName = testFile1
            keyAlias = keyAlias1
        }

        val users = listOf(
            UserProfile("Alice", "alice@example.com", 25),
            UserProfile("Bob", "bob@example.com", 30),
            UserProfile("Charlie", "charlie@example.com", 35)
        )

        users.forEach { user ->
            storage.save(user)
            assertEquals(user, storage.get())
        }

        // Final user should be persisted
        assertEquals(users.last(), storage.get())
    }
}