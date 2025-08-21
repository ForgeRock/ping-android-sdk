/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Unit tests for PushDeviceToken class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PushDeviceTokenTest {

    @Test
    fun `test create PushDeviceToken from JSON`() {
        // Prepare test data
        val json = """
            {
              "id": "test-id",
              "tokenId": "fcm-token-123456789",
              "createdAt": 1659312000000
            }
        """.trimIndent()

        // Parse JSON to PushDeviceToken
        val deviceToken = PushDeviceToken.fromJson(json)

        // Verify properties
        assertNotNull(deviceToken)
        assertEquals("test-id", deviceToken.id)
        assertEquals("fcm-token-123456789", deviceToken.tokenId)
        assertEquals(1659312000000, deviceToken.createdAt.time)
    }

    @Test
    fun `test PushDeviceToken to JSON conversion`() {
        // Create a PushDeviceToken
        val date = Date(1659312000000) // Fixed timestamp for testing
        val deviceToken = PushDeviceToken(
            id = "test-id",
            tokenId = "fcm-token-123456789",
            createdAt = date
        )

        // Convert to JSON
        val json = deviceToken.toJson()

        // Parse back to verify
        val parsedToken = PushDeviceToken.fromJson(json)

        // Verify all properties match
        assertNotNull(parsedToken)
        assertEquals(deviceToken.id, parsedToken.id)
        assertEquals(deviceToken.tokenId, parsedToken.tokenId)
        assertEquals(deviceToken.createdAt.time, parsedToken.createdAt.time)
    }

    @Test
    fun `test PushDeviceToken with default values`() {
        // Create token with only required fields
        val tokenId = "fcm-token-required"
        val deviceToken = PushDeviceToken(tokenId = tokenId)

        // Verify default values
        assertNotNull(deviceToken.id)
        assertTrue(deviceToken.id.isNotEmpty())
        assertEquals(tokenId, deviceToken.tokenId)
        assertNotNull(deviceToken.createdAt)
        assertTrue(System.currentTimeMillis() - deviceToken.createdAt.time < 5000) // Within 5 seconds of current time
    }

    @Test
    fun `test JSON with unknown properties`() {
        val json = """
            {
              "id": "test-id",
              "tokenId": "fcm-token-123456789",
              "createdAt": 1659312000000,
              "unknownProperty": "should be ignored"
            }
        """.trimIndent()

        // Parse should succeed and ignore unknown properties
        val deviceToken = PushDeviceToken.fromJson(json)

        assertNotNull(deviceToken)
        assertEquals("test-id", deviceToken.id)
        assertEquals("fcm-token-123456789", deviceToken.tokenId)
    }

    @Test
    fun `test JSON with missing optional properties`() {
        val json = """
            {
              "tokenId": "fcm-token-minimal"
            }
        """.trimIndent()

        // Parse with minimal required properties
        val deviceToken = PushDeviceToken.fromJson(json)

        assertNotNull(deviceToken)
        assertNotNull(deviceToken.id) // Should generate default UUID
        assertEquals("fcm-token-minimal", deviceToken.tokenId)
        assertNotNull(deviceToken.createdAt) // Should use current time
    }
}
