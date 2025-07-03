/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SQLStorageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDbName = "test_storage.db"
    private val testTableName = "test_items"

    @After
    fun tearDown() {
        // Delete the test database file
        context.getDatabasePath(testDbName).delete()
        // Also delete the journal file if it exists
        File(context.getDatabasePath(testDbName).path + "-journal").delete()
    }

    @Test
    fun testSaveAndGetSingleItem() = runTest {
        // Create a SQLStorage instance for Data objects
        val storage = SQLStorage<Data>(
            context = context,
            databaseName = testDbName,
            tableName = testTableName,
            encryptionEnabled = false // Disable encryption for testing
        )

        // Save a Data object
        val data = Data(1, "test")
        storage.save(data)

        // Retrieve the Data object
        val retrieved = storage.get()
        assertNotNull("Retrieved data should not be null", retrieved)
        assertEquals("Retrieved data should match saved data", data, retrieved)
    }

    @Test
    fun testSaveAndGetMultipleItems() = runTest {
        // Create a SQLStorage instance for Lists of Data objects
        val storage = SQLStorage<List<Data>>(
            context = context,
            databaseName = testDbName,
            tableName = testTableName,
            encryptionEnabled = false
        )

        // Save a list of Data objects
        val dataList = listOf(Data(1, "test1"), Data(2, "test2"))
        storage.save(dataList)

        // Retrieve the list
        val retrieved = storage.get()
        assertNotNull("Retrieved list should not be null", retrieved)
        assertEquals("Retrieved list should match saved list", dataList, retrieved)
    }

    @Test
    fun testDeleteItem() = runTest {
        // Create a SQLStorage instance
        val storage = SQLStorage<Data>(
            context = context,
            databaseName = testDbName,
            tableName = testTableName,
            encryptionEnabled = false
        )

        // Save a Data object
        val data = Data(1, "test")
        storage.save(data)

        // Verify it was saved
        val retrieved = storage.get()
        assertNotNull("Data should be saved before deletion", retrieved)

        // Delete the item
        storage.delete()

        // Verify it was deleted
        val retrievedAfterDelete = storage.get()
        assertNull("Data should be null after deletion", retrievedAfterDelete)
    }

    @Test
    fun testDifferentDataObjects() = runTest {
        // Create two different SQLStorage instances for different types
        val storageData = SQLStorage<Data>(
            context = context,
            databaseName = testDbName,
            tableName = testTableName,
            encryptionEnabled = false
        )
        
        val storageData2 = SQLStorage<Data2>(
            context = context,
            databaseName = testDbName,
            tableName = testTableName,
            encryptionEnabled = false
        )

        // Save data in both storages
        val data = Data(1, "test")
        val data2 = Data2(2, "test2")
        
        storageData.save(data)
        storageData2.save(data2)

        // Retrieve and verify data
        val retrievedData = storageData.get()
        val retrievedData2 = storageData2.get()
        
        assertEquals("First storage should contain correct data", data, retrievedData)
        assertEquals("Second storage should contain correct data", data2, retrievedData2)

        // Delete only the first storage's data
        storageData.delete()
        
        // Verify first storage's data is gone but second is still there
        assertNull("Data should be null after deletion", storageData.get())
        assertNotNull("Data2 should still be available", storageData2.get())
    }

    @Test
    fun testGetByIdAndDeleteById() = runTest {
        // For this test, we need to use the SQLStorage class directly to access its additional methods
        val storage = SQLStorage(
            context = context,
            serializer = kotlinx.serialization.serializer<Data>(),
            type = "TestData",
            idProvider = { it.a.toString() },
            databaseName = testDbName,
            tableName = testTableName,
            encryptionEnabled = false
        )

        // Save multiple items with different IDs
        val data1 = Data(1, "test1")
        val data2 = Data(2, "test2")
        
        storage.save(data1)
        storage.save(data2)

        // Retrieve by ID and verify
        val retrievedData1 = storage.getById("1")
        val retrievedData2 = storage.getById("2")
        
        assertEquals("Retrieved data1 should match", data1, retrievedData1)
        assertEquals("Retrieved data2 should match", data2, retrievedData2)

        // Delete one item by ID
        val deleteResult = storage.deleteById("1")
        assertTrue("Deletion should succeed", deleteResult)

        // Verify deletion
        assertNull("Data1 should be deleted", storage.getById("1"))
        assertNotNull("Data2 should still exist", storage.getById("2"))
    }

    @Test
    fun testGetAllAndDeleteAll() = runTest {
        // Use SQLStorage directly for additional methods
        val storage = SQLStorage(
            context = context,
            serializer = kotlinx.serialization.serializer<Data>(),
            type = "TestData",
            idProvider = { it.a.toString() },
            databaseName = testDbName,
            tableName = testTableName,
            encryptionEnabled = false
        )

        // Save multiple items
        val data1 = Data(1, "test1")
        val data2 = Data(2, "test2")
        val data3 = Data(3, "test3")
        
        storage.save(data1)
        storage.save(data2)
        storage.save(data3)

        // Get all items and verify
        val allItems = storage.getAll()
        assertEquals("Should have 3 items", 3, allItems.size)
        assertTrue("Should contain data1", allItems.contains(data1))
        assertTrue("Should contain data2", allItems.contains(data2))
        assertTrue("Should contain data3", allItems.contains(data3))

        // Delete all items
        storage.deleteAll()

        // Verify all items are deleted
        val allItemsAfterDelete = storage.getAll()
        assertEquals("Should have 0 items after deletion", 0, allItemsAfterDelete.size)
    }

    @Test
    fun testEncryptionSetting() = runTest {
        // Create storage with encryption enabled
        val encryptedStorage = SQLStorage<Data>(
            context = context,
            databaseName = testDbName,
            tableName = testTableName,
            encryptionEnabled = true
        )

        // Save and retrieve data with encryption enabled
        val data = Data(1, "encrypted")
        encryptedStorage.save(data)
        
        val retrievedEncrypted = encryptedStorage.get()
        assertEquals("Should retrieve correctly from encrypted storage", data, retrievedEncrypted)

        // Create storage with encryption disabled
        val unencryptedStorage = SQLStorage<Data>(
            context = context,
            databaseName = "unencrypted_test.db",
            tableName = testTableName,
            encryptionEnabled = false
        )

        // Save and retrieve data with encryption disabled
        val data2 = Data(2, "unencrypted")
        unencryptedStorage.save(data2)
        
        val retrievedUnencrypted = unencryptedStorage.get()
        assertEquals("Should retrieve correctly from unencrypted storage", data2, retrievedUnencrypted)

        // Clean up unencrypted database
        context.getDatabasePath("unencrypted_test.db").delete()
        File(context.getDatabasePath("unencrypted_test.db").path + "-journal").delete()
    }

    @Test
    fun testCustomIdProvider() = runTest {
        // Create storage with custom ID provider
        val storage = SQLStorage(
            context = context,
            serializer = kotlinx.serialization.serializer<Data>(),
            type = "TestData",
            idProvider = { "custom-${it.a}-${it.b}" }, // Custom ID based on both fields
            databaseName = testDbName,
            tableName = testTableName,
            encryptionEnabled = false
        )

        // Save an item
        val data = Data(1, "test")
        storage.save(data)

        // Retrieve by custom ID
        val retrieved = storage.getById("custom-1-test")
        assertNotNull("Should retrieve by custom ID", retrieved)
        assertEquals("Retrieved data should match", data, retrieved)

        // Try to retrieve with an invalid ID
        val nonExistent = storage.getById("invalid-id")
        assertNull("Should not retrieve with invalid ID", nonExistent)
    }
}
