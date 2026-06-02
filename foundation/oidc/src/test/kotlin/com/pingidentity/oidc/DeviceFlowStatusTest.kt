/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceFlowStatusTest {

    // ----- DeviceAuthorizationResponse JSON round-trip -----

    @Test
    fun `DeviceAuthorizationResponse deserializes all fields from JSON`() {
        val json = """
            {
              "device_code": "GmRhmhcxhwAzkoEqiMEg_DnyEysNkuNhszIySk9eS",
              "user_code": "WDJB-MJHT",
              "verification_uri": "https://example.com/device",
              "verification_uri_complete": "https://example.com/device?user_code=WDJB-MJHT",
              "expires_in": 1800,
              "interval": 5
            }
        """.trimIndent()

        val response = Json.decodeFromString<DeviceAuthorizationResponse>(json)

        assertEquals("GmRhmhcxhwAzkoEqiMEg_DnyEysNkuNhszIySk9eS", response.deviceCode)
        assertEquals("WDJB-MJHT", response.userCode)
        assertEquals("https://example.com/device", response.verificationUri)
        assertEquals("https://example.com/device?user_code=WDJB-MJHT", response.verificationUriComplete)
        assertEquals(1800, response.expiresIn)
        assertEquals(5, response.interval)
    }

    @Test
    fun `DeviceAuthorizationResponse verificationUriComplete is null when absent`() {
        val json = """
            {
              "device_code": "abc123",
              "user_code": "ABCD-1234",
              "verification_uri": "https://example.com/activate",
              "expires_in": 900
            }
        """.trimIndent()

        val response = Json.decodeFromString<DeviceAuthorizationResponse>(json)

        assertNull(response.verificationUriComplete)
    }

    @Test
    fun `DeviceAuthorizationResponse interval defaults to 5 when absent`() {
        val json = """
            {
              "device_code": "abc123",
              "user_code": "ABCD-1234",
              "verification_uri": "https://example.com/activate",
              "expires_in": 900
            }
        """.trimIndent()

        val response = Json.decodeFromString<DeviceAuthorizationResponse>(json)

        assertEquals(5, response.interval)
    }

    // ----- DeviceFlowStatus state construction -----

    @Test
    fun `DeviceFlowStatus Started holds DeviceAuthorizationResponse`() {
        val response = DeviceAuthorizationResponse(
            deviceCode = "dev-code",
            userCode = "USER-CODE",
            verificationUri = "https://example.com/device",
            expiresIn = 1800,
        )
        val status = DeviceFlowStatus.Started(response)

        assertTrue(status is DeviceFlowStatus.Started)
        assertEquals("USER-CODE", status.response.userCode)
        assertEquals("https://example.com/device", status.response.verificationUri)
    }

    @Test
    fun `DeviceFlowStatus Polling holds pollCount, pollInterval, nextPollAt`() {
        val nextPollAt = System.currentTimeMillis() + 5000L
        val status = DeviceFlowStatus.Polling(
            pollCount = 3,
            pollInterval = 5,
            nextPollAt = nextPollAt,
        )

        assertTrue(status is DeviceFlowStatus.Polling)
        assertEquals(3, status.pollCount)
        assertEquals(5, status.pollInterval)
        assertEquals(nextPollAt, status.nextPollAt)
    }

    @Test
    fun `DeviceFlowStatus Expired is a singleton data object`() {
        val a = DeviceFlowStatus.Expired
        val b = DeviceFlowStatus.Expired
        assertTrue(a === b)
    }

    @Test
    fun `DeviceFlowStatus Failure holds exception`() {
        val exception = RuntimeException("something went wrong")
        val status = DeviceFlowStatus.Failure(exception)

        assertTrue(status is DeviceFlowStatus.Failure)
        assertEquals("something went wrong", status.exception.message)
    }

    @Test
    fun `DeviceFlowStatus has exactly five states`() {
        val response = DeviceAuthorizationResponse(
            deviceCode = "d",
            userCode = "u",
            verificationUri = "https://example.com",
            expiresIn = 300,
        )
        val statuses: List<DeviceFlowStatus> = listOf(
            DeviceFlowStatus.Started(response),
            DeviceFlowStatus.Polling(1, 5, System.currentTimeMillis()),
            DeviceFlowStatus.Expired,
            DeviceFlowStatus.AccessDenied,
            DeviceFlowStatus.Failure(RuntimeException("err")),
        )
        // Success requires a User — verified by type check only
        val sealed = DeviceFlowStatus::class.sealedSubclasses
        assertEquals(6, sealed.size)
        assertNotNull(sealed.firstOrNull { it.simpleName == "Started" })
        assertNotNull(sealed.firstOrNull { it.simpleName == "Polling" })
        assertNotNull(sealed.firstOrNull { it.simpleName == "Success" })
        assertNotNull(sealed.firstOrNull { it.simpleName == "Expired" })
        assertNotNull(sealed.firstOrNull { it.simpleName == "AccessDenied" })
        assertNotNull(sealed.firstOrNull { it.simpleName == "Failure" })
        // Silence unused warning
        assertEquals(5, statuses.size)
    }
}
