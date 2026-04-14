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

class DefaultDeviceIdentifierTest {

    @Before
    fun setUp() {
        mockkObject(LegacyDeviceIdentifier)
        mockkObject(KeyManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `id should return legacy identifier when LegacyDeviceIdentifier returns non-null`() = runTest {
        // Given
        val expectedLegacyId = "legacy-device-id-12345"
        coEvery { LegacyDeviceIdentifier.identifier() } returns expectedLegacyId

        // When
        val result = DefaultDeviceIdentifier.id()

        // Then
        assertEquals(expectedLegacyId, result)
    }

    @Test
    fun `id should generate new identifier when LegacyDeviceIdentifier returns null`() = runTest {
        // Given
        val mockKey = mockk<Key>()
        val keyBytes = "mock-key-data".toByteArray()
        val expectedHash = MessageDigest.getInstance(KeyProperties.DIGEST_SHA256)
            .digest(keyBytes).toHexString()

        coEvery { LegacyDeviceIdentifier.identifier() } returns null
        every { mockKey.encoded } returns keyBytes
        every { KeyManager.identifierKey(DefaultDeviceIdentifier.KEY_ALIAS) } returns mockKey

        // When
        val result = DefaultDeviceIdentifier.id()

        // Then
        assertNotNull(result)
        assertEquals(expectedHash, result)
    }

    @Test
    fun `id should return hex string of SHA-256 hash when legacy identifier is null`() = runTest {
        // Given
        val mockKey = mockk<Key>()
        val keyBytes = "test-key-data-123".toByteArray()

        coEvery { LegacyDeviceIdentifier.identifier() } returns null
        every { mockKey.encoded } returns keyBytes
        every { KeyManager.identifierKey(DefaultDeviceIdentifier.KEY_ALIAS) } returns mockKey

        // When
        val result = DefaultDeviceIdentifier.id()

        // Then
        assertNotNull(result)
        // Verify it's a valid hex string
        assertTrue(result.matches(Regex("[0-9a-f]+")))
        // Verify the length is correct for SHA-256 (32 bytes = 64 hex characters)
        assertEquals(64, result.length)
    }

    @Test
    fun `id should prefer legacy identifier over generating new one`() = runTest {
        // Given
        val expectedLegacyId = "legacy-id-should-be-used"
        val mockKey = mockk<Key>()

        coEvery { LegacyDeviceIdentifier.identifier() } returns expectedLegacyId
        every { mockKey.encoded } returns "should-not-be-used".toByteArray()
        every { KeyManager.identifierKey(DefaultDeviceIdentifier.KEY_ALIAS) } returns mockKey

        // When
        val result = DefaultDeviceIdentifier.id()

        // Then
        // Should use the legacy identifier and not generate a new one
        assertEquals(expectedLegacyId, result)
    }
}

