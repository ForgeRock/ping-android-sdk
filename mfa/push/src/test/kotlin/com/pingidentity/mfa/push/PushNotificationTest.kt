/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Unit tests for PushNotification class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PushNotificationTest {

    @Test
    fun `test create PushNotification from JSON`() {
        // Prepare test data
        val json = """
            {
              "id": "notification-123",
              "credentialId": "credential-456",
              "ttl": 300,
              "messageId": "AUTHENTICATE:63ca6f18-7cfb-4198-bcd0-ac5041fbbea01583798229441",
              "messageText": "Authenticate login request",
              "customPayload": "{ \"customKey\": \"customValue\" }",
              "challenge": "12345",
              "numbersChallenge": "1, 2, 3",
              "loadBalancer": "lb-cookie",
              "contextInfo": "{\"location\":{\"latitude\":49.2208569,\"longitude\":-123.1174431},\"userAgent\":\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36\",\"platform\":\"MacIntel\"}",
              "pushType": "challenge",
              "createdAt": 1659312000000,
              "additionalData": "{\"city\":\"New York\",\"registeredDeviceId\":\"Chrome Browser\",\"ipAddress\":\"192.168.1.1\"}",
              "approved": false,
              "pending": true
            }
        """.trimIndent()

        // Parse JSON to PushNotification
        val notification = PushNotification.fromJson(json)

        // Verify properties
        assertEquals("notification-123", notification.id)
        assertEquals("credential-456", notification.credentialId)
        assertEquals(300, notification.ttl)
        assertEquals("AUTHENTICATE:63ca6f18-7cfb-4198-bcd0-ac5041fbbea01583798229441", notification.messageId)
        assertEquals("Authenticate login request", notification.messageText)
        assertEquals("{ \"customKey\": \"customValue\" }", notification.customPayload)
        assertEquals("12345", notification.challenge)
        assertEquals("1, 2, 3", notification.numbersChallenge)
        assertEquals("lb-cookie", notification.loadBalancer)
        assertEquals("{\"location\":{\"latitude\":49.2208569,\"longitude\":-123.1174431},\"userAgent\":\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36\",\"platform\":\"MacIntel\"}", notification.contextInfo)
        assertEquals(PushType.CHALLENGE, notification.pushType)
        assertEquals("challenge", notification.type)
        assertEquals(1659312000000, notification.createdAt.time)

        // Check additional data was parsed correctly
        assertNotNull(notification.additionalData)
        assertEquals("New York", notification.additionalData?.get("city"))
        assertEquals("Chrome Browser", notification.additionalData?.get("registeredDeviceId"))
        assertEquals("192.168.1.1", notification.additionalData?.get("ipAddress"))

        // Check state properties
        assertFalse(notification.approved)
        assertTrue(notification.pending)
        assertFalse(notification.responded)
    }

    @Test
    fun `test PushNotification to JSON conversion`() {
        // Create a fixed date for testing
        val createdAt = Date(1659312000000)

        // Create a notification
        val notification = PushNotification(
            id = "notification-123",
            credentialId = "credential-456",
            ttl = 300,
            messageId = "msg-789",
            messageText = "Authenticate login request",
            customPayload = "{ \"customKey\": \"customValue\" }",
            challenge = "12345",
            numbersChallenge = "1, 2, 3, 4, 5",
            loadBalancer = "lb-cookie",
            contextInfo = "context information",
            pushType = PushType.CHALLENGE,
            createdAt = createdAt,
            additionalData = mapOf(
                "location" to "New York",
                "deviceName" to "Chrome Browser",
                "ipAddress" to "192.168.1.1"
            ),
            approved = false,
            pending = true
        )

        // Convert to JSON
        val json = notification.toJson()

        // Parse back to verify
        val parsedNotification = PushNotification.fromJson(json)

        // Verify all properties match
        assertEquals(notification.id, parsedNotification.id)
        assertEquals(notification.credentialId, parsedNotification.credentialId)
        assertEquals(notification.ttl, parsedNotification.ttl)
        assertEquals(notification.messageId, parsedNotification.messageId)
        assertEquals(notification.messageText, parsedNotification.messageText)
        assertEquals(notification.customPayload, parsedNotification.customPayload)
        assertEquals(notification.challenge, parsedNotification.challenge)
        assertEquals(notification.numbersChallenge, parsedNotification.numbersChallenge)
        assertEquals(notification.loadBalancer, parsedNotification.loadBalancer)
        assertEquals(notification.contextInfo, parsedNotification.contextInfo)
        assertEquals(notification.pushType, parsedNotification.pushType)
        assertEquals(notification.createdAt.time, parsedNotification.createdAt.time)
        assertEquals(notification.approved, parsedNotification.approved)
        assertEquals(notification.pending, parsedNotification.pending)

        // Verify additional data was preserved
        assertEquals("New York", parsedNotification.additionalData?.get("location"))
        assertEquals("Chrome Browser", parsedNotification.additionalData?.get("deviceName"))
        assertEquals("192.168.1.1", parsedNotification.additionalData?.get("ipAddress"))
    }

    @Test
    fun `test mark notification as approved`() {
        // Create a notification
        val notification = PushNotification(
            credentialId = "credential-456",
            ttl = 300,
            messageId = "msg-789",
            pushType = PushType.DEFAULT
        )

        // Initially notification should be pending and not approved
        assertTrue(notification.pending)
        assertFalse(notification.approved)
        assertFalse(notification.responded)

        // Mark as approved
        notification.markApproved()

        // Verify state changes
        assertFalse(notification.pending)
        assertTrue(notification.approved)
        assertTrue(notification.responded)
        assertNotNull(notification.respondedAt)
    }

    @Test
    fun `test mark notification as denied`() {
        // Create a notification
        val notification = PushNotification(
            credentialId = "credential-456",
            ttl = 300,
            messageId = "msg-789",
            pushType = PushType.DEFAULT
        )

        // Initially notification should be pending and not approved
        assertTrue(notification.pending)
        assertFalse(notification.approved)
        assertFalse(notification.responded)

        // Mark as denied
        notification.markDenied()

        // Verify state changes
        assertFalse(notification.pending)
        assertFalse(notification.approved)
        assertTrue(notification.responded)
        assertNotNull(notification.respondedAt)
    }

    @Test
    fun `test notification expiration`() {
        // Create an expired notification (createdAt set to long ago)
        val longAgo = Date(System.currentTimeMillis() - 301 * 1000) // 301 seconds ago
        val notification = PushNotification(
            credentialId = "credential-456",
            ttl = 300, // 300 seconds TTL
            messageId = "msg-789",
            pushType = PushType.DEFAULT,
            createdAt = longAgo
        )

        // Should be expired
        assertTrue(notification.expired)

        // Create a non-expired notification
        val recent = Date() // now
        val freshNotification = PushNotification(
            credentialId = "credential-456",
            ttl = 300, // 300 seconds TTL
            messageId = "msg-789",
            pushType = PushType.DEFAULT,
            createdAt = recent
        )

        // Should not be expired
        assertFalse(freshNotification.expired)
    }

    @Test
    fun `test getNumbersChallenge with valid input`() {
        val notification = PushNotification(
            credentialId = "credential-456",
            ttl = 300,
            messageId = "msg-789",
            pushType = PushType.CHALLENGE,
            numbersChallenge = "30,22,81"
        )

        val numbers = notification.getNumbersChallenge()
        assertEquals(3, numbers.size)
        assertEquals(30, numbers[0])
        assertEquals(22, numbers[1])
        assertEquals(81, numbers[2])
    }

    @Test
    fun `test getNumbersChallenge with invalid input`() {
        val notification = PushNotification(
            credentialId = "credential-456",
            ttl = 300,
            messageId = "msg-789",
            pushType = PushType.CHALLENGE,
            numbersChallenge = "1, a, 3, invalid, 5"
        )

        val numbers = notification.getNumbersChallenge()
        assertEquals(3, numbers.size)
        assertEquals(1, numbers[0])
        assertEquals(3, numbers[1])
        assertEquals(5, numbers[2])
    }

    @Test
    fun `test getNumbersChallenge with null challenge`() {
        val notification = PushNotification(
            credentialId = "credential-456",
            ttl = 300,
            messageId = "msg-789",
            pushType = PushType.CHALLENGE
        )

        val numbers = notification.getNumbersChallenge()
        assertTrue(numbers.isEmpty())
    }

    @Test
    fun `test JSON with complex additionalData`() {
        val json = """
            {
              "id": "notification-123",
              "credentialId": "credential-456",
              "ttl": 300,
              "messageId": "msg-789",
              "pushType": "DEFAULT",
              "additionalData": "{\"user\":{\"name\":\"John Doe\",\"age\":30,\"isVerified\":true},\"device\":{\"type\":\"mobile\",\"id\":12345},\"tags\":[\"important\",\"critical\"]}"
            }
        """.trimIndent()

        val notification = PushNotification.fromJson(json)

        // Check nested objects were parsed correctly
        val user = notification.additionalData?.get("user") as? Map<*, *>
        val device = notification.additionalData?.get("device") as? Map<*, *>
        val tags = notification.additionalData?.get("tags") as? List<*>

        assertNotNull(user)
        assertEquals("John Doe", user?.get("name"))
        assertEquals(30, user?.get("age"))
        assertEquals(true, user?.get("isVerified"))

        assertNotNull(device)
        assertEquals("mobile", device?.get("type"))
        assertEquals(12345, device?.get("id"))

        assertNotNull(tags)
        assertEquals(2, tags?.size)
        assertEquals("important", tags?.get(0))
        assertEquals("critical", tags?.get(1))
    }

    @Test(expected = SerializationException::class)
    fun `test invalid JSON throws exception`() {
        val invalidJson = """
            {
              "id": "notification-123",
              "credentialId": "credential-456",
              "ttl": 300,
              "messageId": "msg-789",
              "pushType": "INVALID_TYPE"
            }
        """.trimIndent()

        PushNotification.fromJson(invalidJson) // Should throw SerializationException
    }
}
