/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.id

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class DefaultDeviceIdentifierTest {

    @AfterTest
    fun tearDown() {
        KeyManager.removeKey(DefaultDeviceIdentifier.KEY_ALIAS)
    }

    @Test
    fun testIdNotNull() = runTest {
        val id = DefaultDeviceIdentifier.id()
        assertNotNull(id, "Device ID should not be null")
    }

    @Test
    fun testIdConsistency() = runTest {
        val firstCall = DefaultDeviceIdentifier.id()
        val secondCall = DefaultDeviceIdentifier.id()

        assertEquals(firstCall, secondCall, "Device ID should be consistent between calls")
    }

    @Test
    fun testIdFormat() = runTest {
        val id = DefaultDeviceIdentifier.id()
        // SHA-256 produces 32 bytes which becomes a 64 character hex string
        assertEquals(64, id.length, "Device ID should be a 64 character hex string")
        // Verify it's a valid hex string
        assertTrue(
            "Device ID should only contain hex characters",
            id.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun globalDeviceIdentifier() = runTest {
        assertIs<DeviceIdentifierDelegate>(DeviceIdentifier.identifier)
        assertIs<DefaultDeviceIdentifier>((DeviceIdentifier.identifier as DeviceIdentifierDelegate).delegate)
        val deviceId = DeviceIdentifier.identifier.id()

        DeviceIdentifier.identifier = AndroidIDDeviceIdentifier
        assertIs<AndroidIDDeviceIdentifier>((DeviceIdentifier.identifier as DeviceIdentifierDelegate).delegate)
        val androidIdDeviceIdentifier = DeviceIdentifier.identifier.id()
        assertNotEquals(deviceId, androidIdDeviceIdentifier)
    }
}

