/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.client

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for DeviceClient with in-memory storage.
 */
class DeviceClientTest {

    private lateinit var context: Context
    private lateinit var deviceClient: DeviceClient

    @Before
    fun setup() = runTest {
        context = mockk(relaxed = true)
        deviceClient = DeviceClient.create(context) {
            storage = InMemoryDeviceStorage()
        }
    }

    @Test
    fun `test create OATH device`() = runTest {
        val device = OathDevice(
            id = UUID.randomUUID().toString(),
            issuer = "Test Corp",
            accountName = "test@example.com",
            oathType = "TOTP",
            secret = "BASE32SECRET"
        )

        val result = deviceClient.create(device)
        assertTrue(result.isSuccess)

        result.onSuccess { created ->
            assertEquals(device.id, created.id)
            assertEquals(DeviceType.OATH, created.type)
        }
    }

    @Test
    fun `test get device by id`() = runTest {
        val device = OathDevice(
            id = "test-id",
            issuer = "Test Corp",
            accountName = "test@example.com",
            oathType = "TOTP",
            secret = "BASE32SECRET"
        )

        deviceClient.create(device)

        val result = deviceClient.get("test-id")
        assertTrue(result.isSuccess)

        result.onSuccess { retrieved ->
            assertNotNull(retrieved)
            assertEquals("test-id", retrieved.id)
        }
    }

    @Test
    fun `test get all devices`() = runTest {
        val device1 = OathDevice(
            id = "id-1",
            issuer = "Test Corp",
            accountName = "test1@example.com",
            oathType = "TOTP",
            secret = "SECRET1"
        )

        val device2 = PushDevice(
            id = "id-2",
            issuer = "Test Corp",
            accountName = "test2@example.com",
            serverEndpoint = "https://example.com",
            sharedSecret = "SECRET2"
        )

        deviceClient.create(device1)
        deviceClient.create(device2)

        val result = deviceClient.getAll()
        assertTrue(result.isSuccess)

        result.onSuccess { devices ->
            assertEquals(2, devices.size)
        }
    }

    @Test
    fun `test get devices by type`() = runTest {
        val oathDevice = OathDevice(
            id = "oath-1",
            issuer = "Test Corp",
            accountName = "test@example.com",
            oathType = "TOTP",
            secret = "SECRET"
        )

        val pushDevice = PushDevice(
            id = "push-1",
            issuer = "Test Corp",
            accountName = "test@example.com",
            serverEndpoint = "https://example.com",
            sharedSecret = "SECRET"
        )

        deviceClient.create(oathDevice)
        deviceClient.create(pushDevice)

        val oathResult = deviceClient.getByType(DeviceType.OATH)
        assertTrue(oathResult.isSuccess)

        oathResult.onSuccess { devices ->
            assertEquals(1, devices.size)
            assertEquals(DeviceType.OATH, devices[0].type)
        }
    }

    @Test
    fun `test delete device`() = runTest {
        val device = OathDevice(
            id = "delete-id",
            issuer = "Test Corp",
            accountName = "test@example.com",
            oathType = "TOTP",
            secret = "SECRET"
        )

        deviceClient.create(device)

        val deleteResult = deviceClient.delete("delete-id")
        assertTrue(deleteResult.isSuccess)

        deleteResult.onSuccess { deleted ->
            assertTrue(deleted)
        }

        val getResult = deviceClient.get("delete-id")
        getResult.onSuccess { retrieved ->
            assertEquals(null, retrieved)
        }
    }

    @Test
    fun `test clear all devices`() = runTest {
        val device1 = OathDevice(
            id = "id-1",
            issuer = "Test Corp",
            accountName = "test1@example.com",
            oathType = "TOTP",
            secret = "SECRET1"
        )

        val device2 = OathDevice(
            id = "id-2",
            issuer = "Test Corp",
            accountName = "test2@example.com",
            oathType = "TOTP",
            secret = "SECRET2"
        )

        deviceClient.create(device1)
        deviceClient.create(device2)

        val clearResult = deviceClient.clear()
        assertTrue(clearResult.isSuccess)

        val getAllResult = deviceClient.getAll()
        getAllResult.onSuccess { devices ->
            assertEquals(0, devices.size)
        }
    }
}

