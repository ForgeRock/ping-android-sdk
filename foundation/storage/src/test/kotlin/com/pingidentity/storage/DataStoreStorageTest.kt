/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class DataStoreStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Serializable
    data class TestData(val value: String, val number: Int = 0)

    object TestDataSerializer : Serializer<TestData?> {
        override val defaultValue: TestData? = null

        override suspend fun readFrom(input: InputStream): TestData? {
            return try {
                val json = input.readBytes().decodeToString()
                if (json.isBlank()) null else Json.decodeFromString<TestData>(json)
            } catch (e: Exception) {
                null
            }
        }

        override suspend fun writeTo(t: TestData?, output: OutputStream) {
            val json = if (t != null) Json.encodeToString(t) else ""
            output.write(json.encodeToByteArray())
        }
    }

    @Test
    fun `save and get item successfully`() = runTest {
        val tempFile = tempFolder.newFile("datastore_test")
        val dataStore: DataStore<TestData?> = DataStoreFactory.create(
            serializer = TestDataSerializer,
            produceFile = { tempFile }
        )
        val storage = DataStoreStorage(dataStore, file = tempFile)

        val testData = TestData("test_value", 42)
        storage.save(testData)

        val retrievedData = storage.get()
        assertEquals(testData, retrievedData)
    }

    @Test
    fun `get returns null when no data exists`() = runTest {
        val tempFile = tempFolder.newFile("datastore_empty")
        val dataStore: DataStore<TestData?> = DataStoreFactory.create(
            serializer = TestDataSerializer,
            produceFile = { tempFile }
        )
        val storage = DataStoreStorage(dataStore, file = tempFile)

        val result = storage.get()
        assertNull(result)
    }

    @Test
    fun `save overwrites existing data`() = runTest {
        val tempFile = tempFolder.newFile("datastore_overwrite")
        val dataStore: DataStore<TestData?> = DataStoreFactory.create(
            serializer = TestDataSerializer,
            produceFile = { tempFile }
        )
        val storage = DataStoreStorage(dataStore, file = tempFile)

        val firstData = TestData("first", 1)
        val secondData = TestData("second", 2)

        storage.save(firstData)
        assertEquals(firstData, storage.get())

        storage.save(secondData)
        assertEquals(secondData, storage.get())
    }

    @Test
    fun `delete clears data and deletes file when provided`() = runTest {
        val tempFile = tempFolder.newFile("datastore_delete")
        val dataStore: DataStore<TestData?> = DataStoreFactory.create(
            serializer = TestDataSerializer,
            produceFile = { tempFile }
        )
        val storage = DataStoreStorage(dataStore, file = tempFile)

        storage.save(TestData("to_delete", 999))
        assertTrue(tempFile.exists())
        assertNotNull(storage.get())

        storage.delete()
        assertFalse(tempFile.exists())
        assertNull(storage.get())
    }

    @Test
    fun `delete clears data but does not affect file when file is null`() = runTest {
        val tempFile = tempFolder.newFile("datastore_no_file_delete")
        val dataStore: DataStore<TestData?> = DataStoreFactory.create(
            serializer = TestDataSerializer,
            produceFile = { tempFile }
        )
        val storage = DataStoreStorage(dataStore, file = null) // No file reference

        storage.save(TestData("persistent", 123))
        assertTrue(tempFile.exists()) // File still exists
        assertNotNull(storage.get())

        storage.delete()
        assertTrue(tempFile.exists()) // File should still exist
        assertNull(storage.get()) // But data should be cleared
    }

    @Test
    fun `delete handles file deletion errors gracefully`() = runTest {
        val tempFile = tempFolder.newFile("datastore_error")
        val dataStore: DataStore<TestData?> = DataStoreFactory.create(
            serializer = TestDataSerializer,
            produceFile = { tempFile }
        )

        // Create a file that cannot be deleted (make parent directory read-only)
        val readOnlyDir = tempFolder.newFolder("readonly")
        val protectedFile = File(readOnlyDir, "protected_file")
        protectedFile.createNewFile()
        readOnlyDir.setWritable(false)

        val storage = DataStoreStorage(dataStore, file = protectedFile)
        storage.save(TestData("protected", 456))

        // This should not throw an exception even though file deletion fails
        storage.delete()
        assertNull(storage.get()) // Data should still be cleared

        // Cleanup
        readOnlyDir.setWritable(true)
    }

    @Test
    fun `factory function creates storage with cache strategy`() = runTest {
        val tempFile = tempFolder.newFile("datastore_factory")
        val dataStore: DataStore<TestData?> = DataStoreFactory.create(
            serializer = TestDataSerializer,
            produceFile = { tempFile }
        )

        val storage: Storage<TestData> = DataStoreStorage(
            dataStore = dataStore,
            cacheStrategy = CacheStrategy.NO_CACHE,
            file = tempFile
        )

        val testData = TestData("factory_test", 789)
        storage.save(testData)
        assertEquals(testData, storage.get())
    }

    @Test
    fun `factory function creates storage with default cache strategy`() = runTest {
        val tempFile = tempFolder.newFile("datastore_factory_default")
        val dataStore: DataStore<TestData?> = DataStoreFactory.create(
            serializer = TestDataSerializer,
            produceFile = { tempFile }
        )

        val storage: Storage<TestData> = DataStoreStorage(
            dataStore = dataStore,
            file = tempFile
        )

        val testData = TestData("default_cache", 101112)
        storage.save(testData)
        assertEquals(testData, storage.get())
    }
}

