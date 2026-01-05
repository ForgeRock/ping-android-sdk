/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.id

import android.security.keystore.KeyProperties
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.Key
import java.security.MessageDigest

class LegacyDeviceIdentifierTest {

    private val testAndroidId = "test-android-id-12345"

    @Before
    fun setUp() {
        mockkObject(KeyManager)
        mockkObject(AndroidIDDeviceIdentifier)
        mockkObject(DefaultDeviceIdentifier)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `identifier should return composite ID when legacy key exists`() = runTest {
        // Given
        val mockKey = mockk<Key>()
        val keyBytes = "mock-key-data".toByteArray()
        val expectedHash = MessageDigest.getInstance(KeyProperties.DIGEST_SHA1)
            .digest(keyBytes).toHexString()
        val expectedId = "$testAndroidId-$expectedHash"

        coEvery { AndroidIDDeviceIdentifier.id() } returns testAndroidId
        every { KeyManager.containsKey(testAndroidId) } returns true
        every { KeyManager.identifierKey(testAndroidId) } returns mockKey
        every { mockKey.encoded } returns keyBytes

        // When
        val result = LegacyDeviceIdentifier.identifier()

        // Then
        assertNotNull(result)
        assertEquals(expectedId, result)
    }

    @Test
    fun `identifier should return null when legacy key does not exist`() = runTest {
        // Given
        coEvery { AndroidIDDeviceIdentifier.id() } returns testAndroidId
        every { KeyManager.containsKey(testAndroidId) } returns false

        // When
        val result = LegacyDeviceIdentifier.identifier()

        // Then
        assertEquals(null, result)
    }

    @Test
    fun `id should return legacy identifier when key exists`() = runTest {
        // Given
        val mockKey = mockk<Key>()
        val keyBytes = "test-key-bytes".toByteArray()
        val expectedHash = MessageDigest.getInstance(KeyProperties.DIGEST_SHA1)
            .digest(keyBytes).toHexString()
        val expectedId = "$testAndroidId-$expectedHash"

        coEvery { AndroidIDDeviceIdentifier.id() } returns testAndroidId
        every { KeyManager.containsKey(testAndroidId) } returns true
        every { KeyManager.identifierKey(testAndroidId) } returns mockKey
        every { mockKey.encoded } returns keyBytes

        // When
        val result = LegacyDeviceIdentifier.id()

        // Then
        assertEquals(expectedId, result)
    }

    @Test
    fun `id should fallback to DefaultDeviceIdentifier when legacy key does not exist`() = runTest {
        // Given
        val expectedDefaultId = "default-device-id"

        coEvery { AndroidIDDeviceIdentifier.id() } returns testAndroidId
        every { KeyManager.containsKey(testAndroidId) } returns false
        coEvery { DefaultDeviceIdentifier.id() } returns expectedDefaultId

        // When
        val result = LegacyDeviceIdentifier.id()

        // Then
        assertEquals(expectedDefaultId, result)
    }

    @Test
    fun `identifier should generate correct SHA1 hash of key`() = runTest {
        // Given
        val mockKey = mockk<Key>()
        val keyBytes = "specific-test-data".toByteArray()
        // Calculate the expected SHA-1 hash
        val expectedHash = MessageDigest.getInstance(KeyProperties.DIGEST_SHA1)
            .digest(keyBytes).toHexString()

        coEvery { AndroidIDDeviceIdentifier.id() } returns testAndroidId
        every { KeyManager.containsKey(testAndroidId) } returns true
        every { KeyManager.identifierKey(testAndroidId) } returns mockKey
        every { mockKey.encoded } returns keyBytes

        // When
        val result = LegacyDeviceIdentifier.identifier()

        // Then
        assertNotNull(result)
        assertTrue(result!!.contains(expectedHash))
        assertTrue(result.startsWith(testAndroidId))
    }

    @Test
    fun `identifier should handle different Android IDs`() = runTest {
        // Given
        val differentAndroidId = "different-android-id"
        val mockKey = mockk<Key>()
        val keyBytes = "key-data".toByteArray()

        coEvery { AndroidIDDeviceIdentifier.id() } returns differentAndroidId
        every { KeyManager.containsKey(differentAndroidId) } returns true
        every { KeyManager.identifierKey(differentAndroidId) } returns mockKey
        every { mockKey.encoded } returns keyBytes

        // When
        val result = LegacyDeviceIdentifier.identifier()

        // Then
        assertNotNull(result)
        assertTrue(result!!.startsWith(differentAndroidId))
    }

    @Test
    fun `identifier should use Android ID as key alias`() = runTest {
        // Given
        val customAndroidId = "custom-android-id-999"

        coEvery { AndroidIDDeviceIdentifier.id() } returns customAndroidId
        every { KeyManager.containsKey(customAndroidId) } returns false

        // When
        val result = LegacyDeviceIdentifier.identifier()

        // Then
        // Should return null when key doesn't exist for the Android ID alias
        assertEquals(null, result)
    }
}

