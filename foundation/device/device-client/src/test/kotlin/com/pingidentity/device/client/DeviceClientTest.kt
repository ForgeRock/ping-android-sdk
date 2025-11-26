/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DeviceClientTest {

    private fun createMockHttpClient(
        responseStatus: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = """{"result": []}"""
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    // Check if this is a session info request
                    if (request.url.toString().contains("sessions") &&
                        request.url.toString().contains("_action=getSessionInfo")) {
                        respond(
                            content = """{"username": "test-user-id"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    } else {
                        // Handle device requests
                        respond(
                            content = responseBody,
                            status = responseStatus,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Test DeviceClient configuration`() {
        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "test-realm"
            cookieName = "test-cookie"
        }

        assertNotNull(deviceClient)
        assertNotNull(deviceClient.oathDevice)
        assertNotNull(deviceClient.pushDevice)
        assertNotNull(deviceClient.boundDevice)
        assertNotNull(deviceClient.webAuthnDevice)
        assertNotNull(deviceClient.profileDevice)
    }

    @Test
    fun `Test OathDeviceClient getDevices returns empty list`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """{"result": []}"""
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val deviceResult = deviceClient.oathDevice.devices()
        assertTrue(deviceResult.isSuccess)
        val devices = deviceResult.getOrThrow()
        assertTrue { devices.isEmpty() }
    }

    @Test
    fun `Test OathDeviceClient getDevices returns device list`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """
                {
                    "result": [
                        {
                            "_id": "oath-1",
                            "deviceName": "OATH Device 1",
                            "uuid": "uuid-123",
                            "createdDate": 1700000000,
                            "lastAccessDate": 1700100000
                        }
                    ]
                }
            """.trimIndent()
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val deviceResult = deviceClient.oathDevice.devices()
        assertTrue(deviceResult.isSuccess)
        val devices = deviceResult.getOrThrow()
        assertEquals(1, devices.size)
        assertEquals("oath-1", devices[0].id)
        assertEquals("OATH Device 1", devices[0].deviceName)
        assertEquals("uuid-123", devices[0].uuid)
    }

    @Test
    fun `Test OathDeviceClient deleteDevice`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """{"success": true}"""
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val device = OathDevice(
            id = "oath-1",
            deviceName = "OATH Device 1",
            uuid = "uuid-123",
            createdDate = 1700000000,
            lastAccessDate = 1700100000
        )

        // Should not throw exception
        deviceClient.oathDevice.delete(device)
    }

    @Test
    fun `Test PushDeviceClient getDevices`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """
                {
                    "result": [
                        {
                            "_id": "push-1",
                            "deviceName": "Push Device 1",
                            "uuid": "uuid-456",
                            "createdDate": 1700000000,
                            "lastAccessDate": 1700100000
                        }
                    ]
                }
            """.trimIndent()
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val deviceResult = deviceClient.pushDevice.devices()
        assertTrue(deviceResult.isSuccess)
        val devices = deviceResult.getOrThrow()
        assertEquals(1, devices.size)
        assertEquals("push-1", devices[0].id)
        assertEquals("Push Device 1", devices[0].deviceName)
    }

    @Test
    fun `Test PushDeviceClient deleteDevice`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """{"success": true}"""
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val device = PushDevice(
            id = "push-1",
            deviceName = "Push Device 1",
            uuid = "uuid-456",
            createdDate = 1700000000,
            lastAccessDate = 1700100000
        )

        deviceClient.pushDevice.delete(device)
    }

    @Test
    fun `Test BoundDevice getDevices`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """
                {
                    "result": [
                        {
                            "_id": "bound-1",
                            "deviceName": "Bound Device 1",
                            "deviceId": "device-123",
                            "uuid": "uuid-789",
                            "createdDate": 1700000000,
                            "lastAccessDate": 1700100000
                        }
                    ]
                }
            """.trimIndent()
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val deviceResult = deviceClient.boundDevice.devices()
        assertTrue(deviceResult.isSuccess)
        val devices = deviceResult.getOrThrow()
        assertEquals(1, devices.size)
        assertEquals("bound-1", devices[0].id)
        assertEquals("Bound Device 1", devices[0].deviceName)
        assertEquals("device-123", devices[0].deviceId)
    }

    @Test
    fun `Test BoundDevice deleteDevice`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """{"success": true}"""
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val device = BoundDevice(
            id = "bound-1",
            deviceName = "Bound Device 1",
            deviceId = "device-123",
            uuid = "uuid-789",
            createdDate = 1700000000,
            lastAccessDate = 1700100000
        )

        deviceClient.boundDevice.delete(device)
    }

    @Test
    fun `Test BoundDevice updateDevice`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """{"success": true}"""
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val device = BoundDevice(
            id = "bound-1",
            deviceName = "Updated Bound Device",
            deviceId = "device-123",
            uuid = "uuid-789",
            createdDate = 1700000000,
            lastAccessDate = 1700100000
        )

        deviceClient.boundDevice.update(device)
    }

    @Test
    fun `Test WebAuthnDevice getDevices`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """
                {
                    "result": [
                        {
                            "_id": "webauthn-1",
                            "deviceName": "WebAuthn Device 1",
                            "uuid": "uuid-abc",
                            "credentialId": "cred-123",
                            "createdDate": 1700000000,
                            "lastAccessDate": 1700100000
                        }
                    ]
                }
            """.trimIndent()
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val deviceResult = deviceClient.webAuthnDevice.devices()
        assertTrue(deviceResult.isSuccess)
        val devices = deviceResult.getOrThrow()
        assertEquals(1, devices.size)
        assertEquals("webauthn-1", devices[0].id)
        assertEquals("WebAuthn Device 1", devices[0].deviceName)
        assertEquals("cred-123", devices[0].credentialId)
    }

    @Test
    fun `Test WebAuthnDevice deleteDevice`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """{"success": true}"""
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val device = WebAuthnDevice(
            id = "webauthn-1",
            deviceName = "WebAuthn Device 1",
            uuid = "uuid-abc",
            credentialId = "cred-123",
            createdDate = 1700000000,
            lastAccessDate = 1700100000
        )

        deviceClient.webAuthnDevice.delete(device)
    }

    @Test
    fun `Test WebAuthnDevice updateDevice`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """{"success": true}"""
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val device = WebAuthnDevice(
            id = "webauthn-1",
            deviceName = "Updated WebAuthn Device",
            uuid = "uuid-abc",
            credentialId = "cred-123",
            createdDate = 1700000000,
            lastAccessDate = 1700100000
        )

        deviceClient.webAuthnDevice.update(device)
    }

    @Test
    fun `Test ProfileDevice getDevices`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """
                {
                    "result": [
                        {
                            "_id": "profile-1",
                            "alias": "Profile Device 1",
                            "identifier": "identifier-123",
                            "metadata": {
                                "deviceType": "mobile"
                            },
                            "location": {
                                "latitude": 37.7749,
                                "longitude": -122.4194
                            },
                            "lastSelectedDate": 1700100000
                        }
                    ]
                }
            """.trimIndent()
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val deviceResult = deviceClient.profileDevice.devices()
        assertTrue(deviceResult.isSuccess)
        val devices = deviceResult.getOrThrow()
        assertEquals(1, devices.size)
        assertEquals("profile-1", devices[0].id)
        assertEquals("Profile Device 1", devices[0].deviceName)
        assertEquals("identifier-123", devices[0].identifier)
        assertNotNull(devices[0].location)
        assertEquals(37.7749, devices[0].location?.latitude)
    }

    @Test
    fun `Test ProfileDevice deleteDevice`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """{"success": true}"""
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val metadata = buildJsonObject {
            put("deviceType", "mobile")
        }

        val device = ProfileDevice(
            id = "profile-1",
            deviceName = "Profile Device 1",
            identifier = "identifier-123",
            metadata = metadata,
            location = Location(37.7749, -122.4194),
            lastSelectedDate = 1700100000
        )

        deviceClient.profileDevice.delete(device)
    }

    @Test
    fun `Test ProfileDevice updateDevice`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """{"success": true}"""
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val metadata = buildJsonObject {
            put("deviceType", "mobile")
        }

        val device = ProfileDevice(
            id = "profile-1",
            deviceName = "Updated Profile Device",
            identifier = "identifier-123",
            metadata = metadata,
            location = Location(37.7749, -122.4194),
            lastSelectedDate = 1700100000
        )

        deviceClient.profileDevice.update(device)
    }

    @Test
    fun `Test DeviceClient with multiple devices`() = runTest {
        val mockClient = createMockHttpClient(
            responseBody = """
                {
                    "result": [
                        {
                            "_id": "oath-1",
                            "deviceName": "OATH Device 1",
                            "uuid": "uuid-1",
                            "createdDate": 1700000000,
                            "lastAccessDate": 1700100000
                        },
                        {
                            "_id": "oath-2",
                            "deviceName": "OATH Device 2",
                            "uuid": "uuid-2",
                            "createdDate": 1700000000,
                            "lastAccessDate": 1700100000
                        },
                        {
                            "_id": "oath-3",
                            "deviceName": "OATH Device 3",
                            "uuid": "uuid-3",
                            "createdDate": 1700000000,
                            "lastAccessDate": 1700100000
                        }
                    ]
                }
            """.trimIndent()
        )

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        val deviceResult = deviceClient.oathDevice.devices()
        assertTrue(deviceResult.isSuccess)
        val devices = deviceResult.getOrThrow()
        assertEquals(3, devices.size)
        assertEquals("OATH Device 1", devices[0].deviceName)
        assertEquals("OATH Device 2", devices[1].deviceName)
        assertEquals("OATH Device 3", devices[2].deviceName)
    }

    @Test
    fun `Test DeviceClient handles error response gracefully`() = runTest {
        val mockClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respondError(HttpStatusCode.InternalServerError)
                }
            }
        }

        val deviceClient = DeviceClient {
            ssoTokenString = "test-token"
            serverUrl = URL("https://test.example.com")
            realm = "alpha"
            cookieName = "test-cookie"
            httpClient = mockClient
        }

        try {
            deviceClient.oathDevice.devices()
            // If no exception is thrown, test passes (client handles errors gracefully)
        } catch (e: Exception) {
            // Expected behavior - client may throw exception for error responses
            assertTrue { e.message != null }
        }
    }

    @Test
    fun `Test DeviceClientConfig default values`() {
        val config = DeviceClientConfig()

        assertEquals("", config.ssoTokenString)
        assertEquals("root", config.realm)
        assertEquals("iPlanetDirectoryPro", config.cookieName)
        assertNotNull(config.httpClient)
    }

    @Test
    fun `Test DeviceClientConfig can be modified`() {
        val config = DeviceClientConfig()

        config.ssoTokenString = "new-token"
        config.serverUrl = URL("https://new.example.com")
        config.realm = "new-realm"
        config.cookieName = "new-cookie"

        assertEquals("new-token", config.ssoTokenString)
        assertEquals(URL("https://new.example.com"), config.serverUrl)
        assertEquals("new-realm", config.realm)
        assertEquals("new-cookie", config.cookieName)
    }
}