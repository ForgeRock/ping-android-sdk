/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding

import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType
import com.pingidentity.storage.MemoryStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserKeysStorageTest {

    private val storage = MemoryStorage<List<UserKey>>()

    private val userKeysStorage = UserKeysStorage {
        storage = { this@UserKeysStorageTest.storage }
    }

    private val testUserKey1 = UserKey(
        id = "key1",
        userId = "user1",
        userName = "User One",
        kid = "kid1",
        authType = DeviceBindingAuthenticationType.BIOMETRIC_ONLY
    )

    private val testUserKey2 = UserKey(
        id = "key2",
        userId = "user2",
        userName = "User Two",
        kid = "kid2",
        authType = DeviceBindingAuthenticationType.APPLICATION_PIN
    )

    @Test
    fun `findByUserId returns correct user key when found`() = runTest {
        val userKeys = listOf(testUserKey1, testUserKey2)
        storage.save(userKeys)

        val result = userKeysStorage.findByUserId("user1")

        assertEquals(testUserKey1, result)
    }

    @Test
    fun `findByUserId returns null when user not found`() = runTest {
        val userKeys = listOf(testUserKey1, testUserKey2)
        storage.save(userKeys)

        val result = userKeysStorage.findByUserId("user3")

        assertNull(result)
    }

    @Test
    fun `findByUserId returns null when storage is empty`() = runTest {
        val result = userKeysStorage.findByUserId("user1")
        assertNull(result)
    }

    @Test
    fun `findAll returns all user keys`() = runTest {
        val userKeys = listOf(testUserKey1, testUserKey2)
        storage.save(userKeys)

        val result = userKeysStorage.findAll()

        assertEquals(userKeys, result)
    }

    @Test
    fun `findAll returns empty list when storage is null`() = runTest {
        val result = userKeysStorage.findAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `save adds new user key`() = runTest {
        val existingKeys = listOf(testUserKey1)
        storage.save(existingKeys)

        userKeysStorage.save(testUserKey2)

        val result = userKeysStorage.findAll()
        assertEquals(2, result.size)
        assertTrue(result.contains(testUserKey1))
        assertTrue(result.contains(testUserKey2))
    }

    @Test
    fun `save replaces existing user key with same userId`() = runTest {
        storage.save(listOf(testUserKey1))
        val updatedUserKey1 = testUserKey1.copy(userName = "Updated User One")

        userKeysStorage.save(updatedUserKey1)

        val result = userKeysStorage.findAll()
        assertEquals(1, result.size)
        assertEquals(updatedUserKey1, result.first())
        assertEquals("Updated User One", result.first().userName)
    }

    @Test
    fun `save works when storage is initially empty`() = runTest {
        userKeysStorage.save(testUserKey1)

        val result = userKeysStorage.findAll()
        assertEquals(listOf(testUserKey1), result)
    }

    @Test
    fun `delete removes user key`() = runTest {
        storage.save(listOf(testUserKey1, testUserKey2))

        userKeysStorage.delete(testUserKey1)

        val result = userKeysStorage.findAll()
        assertEquals(listOf(testUserKey2), result)
    }

    @Test
    fun `delete does nothing when user key not found`() = runTest {
        storage.save(listOf(testUserKey2))

        userKeysStorage.delete(testUserKey1)

        val result = userKeysStorage.findAll()
        assertEquals(listOf(testUserKey2), result)
    }

    @Test
    fun `delete works when storage is empty`() = runTest {
        userKeysStorage.delete(testUserKey1)

        val result = userKeysStorage.findAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `delete calls storage delete when last key is removed`() = runTest {
        storage.save(listOf(testUserKey1))

        userKeysStorage.delete(testUserKey1)

        val result = storage.get()
        assertNull(result)
    }

    @Test
    fun `deleteByUserId removes user key by userId`() = runTest {
        storage.save(listOf(testUserKey1, testUserKey2))

        userKeysStorage.deleteByUserId("user1")

        val result = userKeysStorage.findAll()
        assertEquals(listOf(testUserKey2), result)
    }

    @Test
    fun `deleteByUserId does nothing when userId not found`() = runTest {
        storage.save(listOf(testUserKey1, testUserKey2))

        userKeysStorage.deleteByUserId("user3")

        val result = userKeysStorage.findAll()
        assertEquals(2, result.size)
        assertTrue(result.contains(testUserKey1))
        assertTrue(result.contains(testUserKey2))
    }

    @Test
    fun `deleteByUserId works when storage is empty`() = runTest {
        userKeysStorage.deleteByUserId("user1")

        val result = userKeysStorage.findAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deleteByUserId calls storage delete when last key is removed`() = runTest {
        storage.save(listOf(testUserKey1))

        userKeysStorage.deleteByUserId("user1")

        val result = storage.get()
        assertNull(result)
    }

    @Test
    fun `companion invoke creates UserKeysStorage with custom config`() {
        val customStorage = UserKeysStorage {
            storage = { this@UserKeysStorageTest.storage }
        }
        assertEquals(storage, customStorage.storage)
    }

    @Test
    fun `saveAll with multiple user keys stores all keys`() = runTest {
        val userKeys = listOf(testUserKey1, testUserKey2)

        userKeysStorage.saveAll(userKeys)

        val result = userKeysStorage.findAll()
        assertEquals(2, result.size)
        assertTrue(result.contains(testUserKey1))
        assertTrue(result.contains(testUserKey2))
    }

    @Test
    fun `saveAll with empty list does nothing`() = runTest {
        // First save some keys
        storage.save(listOf(testUserKey1, testUserKey2))

        // Then save empty list - should do nothing
        userKeysStorage.saveAll(emptyList())

        val result = userKeysStorage.findAll()
        assertEquals(2, result.size)
        assertTrue(result.contains(testUserKey1))
        assertTrue(result.contains(testUserKey2))
    }

    @Test
    fun `saveAll adds new keys to existing keys`() = runTest {
        // First save some keys
        userKeysStorage.saveAll(listOf(testUserKey1, testUserKey2))

        val newUserKey = UserKey(
            id = "key3",
            userId = "user3",
            userName = "User Three",
            kid = "kid3",
            authType = DeviceBindingAuthenticationType.BIOMETRIC_ONLY
        )

        // Add new key to existing keys
        userKeysStorage.saveAll(listOf(newUserKey))

        val result = userKeysStorage.findAll()
        assertEquals(3, result.size)
        assertTrue(result.contains(testUserKey1))
        assertTrue(result.contains(testUserKey2))
        assertTrue(result.contains(newUserKey))
    }

    @Test
    fun `saveAll with single key works correctly`() = runTest {
        userKeysStorage.saveAll(listOf(testUserKey1))

        val result = userKeysStorage.findAll()
        assertEquals(1, result.size)
        assertEquals(testUserKey1, result.first())
    }

    @Test
    fun `saveAll adds to existing data in storage`() = runTest {
        // Pre-populate storage with existing data
        storage.save(listOf(testUserKey1))

        // Save new list that should add to existing data
        userKeysStorage.saveAll(listOf(testUserKey2))

        val result = userKeysStorage.findAll()
        assertEquals(2, result.size)
        assertTrue(result.contains(testUserKey1))
        assertTrue(result.contains(testUserKey2))
    }

    @Test
    fun `saveAll prevents duplicates by userId`() = runTest {
        // First save some keys
        userKeysStorage.saveAll(listOf(testUserKey1, testUserKey2))

        // Try to save keys with same userIds but different data
        val duplicateKey1 = testUserKey1.copy(userName = "Duplicate User One")
        val duplicateKey2 = testUserKey2.copy(userName = "Duplicate User Two")

        userKeysStorage.saveAll(listOf(duplicateKey1, duplicateKey2))

        val result = userKeysStorage.findAll()
        assertEquals(2, result.size)
        // Original keys should remain unchanged
        assertTrue(result.contains(testUserKey1))
        assertTrue(result.contains(testUserKey2))
        // Duplicates should not be added
        assertEquals("User One", userKeysStorage.findByUserId("user1")?.userName)
        assertEquals("User Two", userKeysStorage.findByUserId("user2")?.userName)
    }
}
