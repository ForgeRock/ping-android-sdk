/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.client

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceTest {

    @Test
    fun `Test device is an Immutable Device and is configured correctly`() = runTest {
        val testDeviceList = mutableListOf<Device>(
            TestDevice(id = "1", deviceName = "Test Device 1"),
            TestDevice(id = "2", deviceName = "Test Device 2")
        )
        val immutableDevice = TestImmutableDevice(testDeviceList)
        assertTrue { immutableDevice.getDevices().containsAll(testDeviceList) }
        assertTrue { immutableDevice.getDevices().size == 2 }
        val deviceToDelete = testDeviceList[0]
        immutableDevice.deleteDevice(deviceToDelete)
        assertTrue { immutableDevice.getDevices().size == 1 }
        assertFalse { immutableDevice.getDevices().contains(deviceToDelete) }
    }

    @Test
    fun `Test MutableDevice can update devices`() = runTest {
        val mutableDeviceList = mutableListOf<Device>(
            TestDevice(id = "1", deviceName = "Test Device 1"),
            TestDevice(id = "2", deviceName = "Test Device 2")
        )
        val mutableDevice = TestMutableDevice(mutableDeviceList)

        assertEquals(2, mutableDevice.getDevices().size)

        // Update device name
        val updatedDevice = TestDevice(id = "1", deviceName = "Updated Device 1")
        mutableDevice.updateDevice(updatedDevice)

        val devices = mutableDevice.getDevices()
        assertEquals(2, devices.size)
        assertEquals("Updated Device 1", devices.find { it.id == "1" }?.deviceName)
    }

    @Test
    fun `Test BoundDevice properties`() {
        val boundDevice = BoundDevice(
            id = "bound-1",
            deviceName = "My Bound Device",
            deviceId = "device-123",
            uuid = "uuid-456",
            createdDate = 1700000000L,
            lastAccessDate = 1700100000L
        )

        assertEquals("bound-1", boundDevice.id)
        assertEquals("My Bound Device", boundDevice.deviceName)
        assertEquals("device-123", boundDevice.deviceId)
        assertEquals("uuid-456", boundDevice.uuid)
        assertEquals(1700000000L, boundDevice.createdDate)
        assertEquals(1700100000L, boundDevice.lastAccessDate)
        assertEquals("devices/2fa/binding", boundDevice.urlSuffix)
    }

    @Test
    fun `Test BoundDevice can update deviceName`() {
        val boundDevice = BoundDevice(
            id = "bound-1",
            deviceName = "Original Name",
            deviceId = "device-123",
            uuid = "uuid-456",
            createdDate = 1700000000L,
            lastAccessDate = 1700100000L
        )

        boundDevice.deviceName = "Updated Name"
        assertEquals("Updated Name", boundDevice.deviceName)
    }

    @Test
    fun `Test OathDevice properties`() {
        val oathDevice = OathDevice(
            id = "oath-1",
            deviceName = "My OATH Device",
            uuid = "uuid-789",
            createdDate = 1700000000L,
            lastAccessDate = 1700100000L
        )

        assertEquals("oath-1", oathDevice.id)
        assertEquals("My OATH Device", oathDevice.deviceName)
        assertEquals("uuid-789", oathDevice.uuid)
        assertEquals(1700000000L, oathDevice.createdDate)
        assertEquals(1700100000L, oathDevice.lastAccessDate)
        assertEquals("devices/2fa/oath", oathDevice.urlSuffix)
    }

    @Test
    fun `Test PushDevice properties`() {
        val pushDevice = PushDevice(
            id = "push-1",
            deviceName = "My Push Device",
            uuid = "uuid-abc",
            createdDate = 1700000000L,
            lastAccessDate = 1700100000L
        )

        assertEquals("push-1", pushDevice.id)
        assertEquals("My Push Device", pushDevice.deviceName)
        assertEquals("uuid-abc", pushDevice.uuid)
        assertEquals(1700000000L, pushDevice.createdDate)
        assertEquals(1700100000L, pushDevice.lastAccessDate)
        assertEquals("devices/2fa/push", pushDevice.urlSuffix)
    }

    @Test
    fun `Test WebAuthnDevice properties`() {
        val webAuthnDevice = WebAuthnDevice(
            id = "webauthn-1",
            deviceName = "My WebAuthn Device",
            uuid = "uuid-def",
            credentialId = "cred-123",
            createdDate = 1700000000L,
            lastAccessDate = 1700100000L
        )

        assertEquals("webauthn-1", webAuthnDevice.id)
        assertEquals("My WebAuthn Device", webAuthnDevice.deviceName)
        assertEquals("uuid-def", webAuthnDevice.uuid)
        assertEquals("cred-123", webAuthnDevice.credentialId)
        assertEquals(1700000000L, webAuthnDevice.createdDate)
        assertEquals(1700100000L, webAuthnDevice.lastAccessDate)
        assertEquals("devices/2fa/webauthn", webAuthnDevice.urlSuffix)
    }

    @Test
    fun `Test WebAuthnDevice can update deviceName`() {
        val webAuthnDevice = WebAuthnDevice(
            id = "webauthn-1",
            deviceName = "Original Name",
            uuid = "uuid-def",
            credentialId = "cred-123",
            createdDate = 1700000000L,
            lastAccessDate = 1700100000L
        )

        webAuthnDevice.deviceName = "Updated WebAuthn Name"
        assertEquals("Updated WebAuthn Name", webAuthnDevice.deviceName)
    }

    @Test
    fun `Test ProfileDevice properties`() {
        val metadata = buildJsonObject {
            put("deviceType", "mobile")
            put("osVersion", "Android 14")
        }

        val profileDevice = ProfileDevice(
            id = "profile-1",
            deviceName = "My Profile Device",
            identifier = "identifier-123",
            metadata = metadata,
            location = Location(latitude = 37.7749, longitude = -122.4194),
            lastSelectedDate = 1700100000L
        )

        assertEquals("profile-1", profileDevice.id)
        assertEquals("My Profile Device", profileDevice.deviceName)
        assertEquals("identifier-123", profileDevice.identifier)
        assertNotNull(profileDevice.metadata)
        assertEquals(37.7749, profileDevice.location?.latitude)
        assertEquals(-122.4194, profileDevice.location?.longitude)
        assertEquals(1700100000L, profileDevice.lastSelectedDate)
        assertEquals("devices/profile", profileDevice.urlSuffix)
    }

    @Test
    fun `Test ProfileDevice without location`() {
        val metadata = buildJsonObject {
            put("deviceType", "desktop")
        }

        val profileDevice = ProfileDevice(
            id = "profile-2",
            deviceName = "Desktop Device",
            identifier = "identifier-456",
            metadata = metadata,
            location = null,
            lastSelectedDate = 1700100000L
        )

        assertEquals("profile-2", profileDevice.id)
        assertEquals("Desktop Device", profileDevice.deviceName)
        assertEquals(null, profileDevice.location)
    }

    @Test
    fun `Test ProfileDevice can update deviceName`() {
        val metadata = buildJsonObject {
            put("test", "data")
        }

        val profileDevice = ProfileDevice(
            id = "profile-1",
            deviceName = "Original Alias",
            identifier = "identifier-123",
            metadata = metadata,
            lastSelectedDate = 1700100000L
        )

        profileDevice.deviceName = "Updated Alias"
        assertEquals("Updated Alias", profileDevice.deviceName)
    }

    @Test
    fun `Test Location data class`() {
        val location = Location(latitude = 40.7128, longitude = -74.0060)

        assertEquals(40.7128, location.latitude)
        assertEquals(-74.0060, location.longitude)
    }

    @Test
    fun `Test ImmutableDevice getDevices returns empty list when no devices`() = runTest {
        val emptyDevice = TestImmutableDevice(mutableListOf())

        assertTrue { emptyDevice.getDevices().isEmpty() }
    }

    @Test
    fun `Test ImmutableDevice deleteDevice with non-existent device`() = runTest {
        val devices = mutableListOf<Device>(
            TestDevice(id = "1", deviceName = "Device 1")
        )
        val immutableDevice = TestImmutableDevice(devices)

        val nonExistentDevice = TestDevice(id = "999", deviceName = "Non-existent")
        immutableDevice.deleteDevice(nonExistentDevice)

        assertEquals(1, immutableDevice.getDevices().size)
    }

    @Test
    fun `Test MutableDevice delete and update operations`() = runTest {
        val devices = mutableListOf<Device>(
            TestDevice(id = "1", deviceName = "Device 1"),
            TestDevice(id = "2", deviceName = "Device 2"),
            TestDevice(id = "3", deviceName = "Device 3")
        )
        val mutableDevice = TestMutableDevice(devices)

        // Delete device
        mutableDevice.deleteDevice(devices[1])
        assertEquals(2, mutableDevice.getDevices().size)

        // Update remaining device
        val updatedDevice = TestDevice(id = "1", deviceName = "Updated Device 1")
        mutableDevice.updateDevice(updatedDevice)

        val result = mutableDevice.getDevices()
        assertEquals(2, result.size)
        assertEquals("Updated Device 1", result.find { it.id == "1" }?.deviceName)
    }

    @Test
    fun `Test Device urlSuffix can be modified`() {
        val boundDevice = BoundDevice(
            id = "bound-1",
            deviceName = "Test",
            deviceId = "device-123",
            uuid = "uuid-456",
            createdDate = 1700000000L,
            lastAccessDate = 1700100000L
        )

        assertEquals("devices/2fa/binding", boundDevice.urlSuffix)
        boundDevice.urlSuffix = "custom/url/suffix"
        assertEquals("custom/url/suffix", boundDevice.urlSuffix)
    }

    private class TestMutableDevice(
        private val deviceList: MutableList<Device> = mutableListOf()
    ) : MutableDevice<Device> {
        override suspend fun getDevices(): List<Device> {
            return deviceList
        }

        override suspend fun deleteDevice(device: Device) {
            deviceList.remove(device)
        }

        override suspend fun updateDevice(device: Device) {
            val index = deviceList.indexOfFirst { it.id == device.id }
            if (index != -1) {
                deviceList[index] = device
            }
        }
    }

    private class TestImmutableDevice(
        private val deviceList: MutableList<Device> = mutableListOf<Device>()
    ): ImmutableDevice<Device> {
        override suspend fun getDevices(): List<Device> {
            return deviceList
        }

        override suspend fun deleteDevice(device: Device) {
            deviceList.remove(device)
        }
    }

    data class TestDevice(
        override val id: String,
        override val deviceName: String,
        override val urlSuffix: String = "/test/device"
    ) : Device()
}