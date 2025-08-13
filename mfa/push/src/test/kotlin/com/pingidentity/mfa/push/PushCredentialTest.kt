/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Unit tests for PushCredential class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PushCredentialTest {

    @Test
    fun `test create PushCredential from JSON`() {
        // Prepare test data
        val json = """
            {
              "id": "test-id",
              "userId": "user3",
              "resourceId": "581dd3c8-3c69-49ac-b01a-074450b226c5",
              "issuer": "ForgeRock",
              "displayIssuer": "ForgeRock Display",
              "accountName": "user",
              "displayAccountName": "User Display",
              "serverEndpoint": "http://dev.openam.example.com:8081/openam/json/dev/push/sns/message",
              "sharedSecret": "b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E",
              "createdAt": 1659312000000,
              "imageURL": "http://forgerock.com/logo.jpg",
              "backgroundColor": "#519387",
              "policies": "{}",
              "lockingPolicy": "testPolicy",
              "isLocked": true,
              "platform": "PING_AM"
            }
        """.trimIndent()

        // Parse JSON to PushCredential
        val credential = PushCredential.fromJson(json)

        // Verify properties
        assertEquals("test-id", credential.id)
        assertEquals("user3", credential.userId)
        assertEquals("581dd3c8-3c69-49ac-b01a-074450b226c5", credential.resourceId)
        assertEquals("ForgeRock", credential.issuer)
        assertEquals("ForgeRock Display", credential.displayIssuer)
        assertEquals("user", credential.accountName)
        assertEquals("User Display", credential.displayAccountName)
        assertEquals("http://dev.openam.example.com:8081/openam/json/dev/push/sns/message", credential.serverEndpoint)
        assertEquals("b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E", credential.sharedSecret)
        assertEquals(1659312000000, credential.createdAt.time)
        assertEquals("http://forgerock.com/logo.jpg", credential.imageURL)
        assertEquals("#519387", credential.backgroundColor)
        assertEquals("{}", credential.policies)
        assertEquals("testPolicy", credential.lockingPolicy)
        assertTrue(credential.isLocked)
        assertEquals(PushPlatform.PING_AM.name, credential.platform)
    }

    @Test
    fun `test PushCredential to JSON conversion`() {
        // Create a PushCredential
        val date = Date(1659312000000) // Fixed timestamp for testing
        val credential = PushCredential(
            id = "test-id",
            userId = "user3",
            resourceId = "581dd3c8-3c69-49ac-b01a-074450b226c5",
            issuer = "ForgeRock",
            displayIssuer = "ForgeRock Display",
            accountName = "user",
            displayAccountName = "User Display",
            serverEndpoint = "http://dev.openam.example.com:8081/openam/json/dev/push/sns/message",
            sharedSecret = "b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E",
            createdAt = date,
            imageURL = "http://forgerock.com/logo.jpg",
            backgroundColor = "#519387",
            policies = "{}",
            lockingPolicy = "testPolicy",
            isLocked = true,
            platform = PushPlatform.PING_AM.name
        )

        // Convert to JSON
        val json = credential.toJson()

        // Parse back to verify
        val parsedCredential = PushCredential.fromJson(json)

        // Verify all properties match
        assertEquals(credential.id, parsedCredential.id)
        assertEquals(credential.userId, parsedCredential.userId)
        assertEquals(credential.resourceId, parsedCredential.resourceId)
        assertEquals(credential.issuer, parsedCredential.issuer)
        assertEquals(credential.displayIssuer, parsedCredential.displayIssuer)
        assertEquals(credential.accountName, parsedCredential.accountName)
        assertEquals(credential.displayAccountName, parsedCredential.displayAccountName)
        assertEquals(credential.serverEndpoint, parsedCredential.serverEndpoint)
        assertEquals(credential.sharedSecret, parsedCredential.sharedSecret)
        assertEquals(credential.createdAt.time, parsedCredential.createdAt.time)
        assertEquals(credential.imageURL, parsedCredential.imageURL)
        assertEquals(credential.backgroundColor, parsedCredential.backgroundColor)
        assertEquals(credential.policies, parsedCredential.policies)
        assertEquals(credential.lockingPolicy, parsedCredential.lockingPolicy)
        assertEquals(credential.isLocked, parsedCredential.isLocked)
        assertEquals(credential.platform, parsedCredential.platform)
    }

    @Test
    fun `test create PushCredential from URI`() = runBlocking {
        // Prepare test URI
        val uri = "pushauth://push/forgerock:user?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E&d=dXNlcjM&pid=NTgxZGQzYzgtM2M2OS00OWFjLWIwMWEtMDc0NDUwYjIyNmM1&image=aHR0cDovL2Zvcmdlcm9jay5jb20vbG9nby5qcGc&b=%23519387"

        // Parse URI to PushCredential
        val credential = PushCredential.fromUri(uri)

        // Verify properties
        assertNotNull(credential)
        assertEquals("forgerock", credential.issuer)
        assertEquals("forgerock", credential.displayIssuer)
        assertEquals("user", credential.accountName)
        assertEquals("user", credential.displayAccountName)
        assertEquals("http://dev.openam.example.com:8081/openam/json/dev/push/sns/message", credential.serverEndpoint)
        assertEquals("b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E", credential.sharedSecret)
        assertEquals("user3", credential.userId)
        assertEquals("581dd3c8-3c69-49ac-b01a-074450b226c5", credential.resourceId)
        assertEquals("http://forgerock.com/logo.jpg", credential.imageURL)
        assertEquals("#519387", credential.backgroundColor)
    }

    @Test
    fun `test PushCredential defaults`() {
        // Create credential with minimum required fields
        val credential = PushCredential(
            issuer = "ForgeRock",
            accountName = "user",
            serverEndpoint = "http://example.com/endpoint",
            sharedSecret = "secret"
        )

        // Verify default values
        assertNotNull(credential.id) // Should generate a UUID
        assertTrue(credential.id.isNotEmpty())
        assertEquals("ForgeRock", credential.issuer)
        assertEquals("ForgeRock", credential.displayIssuer)
        assertEquals("user", credential.accountName)
        assertEquals("user", credential.displayAccountName)
        assertEquals("http://example.com/endpoint", credential.serverEndpoint)
        assertEquals("secret", credential.sharedSecret)
        assertNull(credential.userId)
        assertEquals(credential.id, credential.resourceId)
        assertNotNull(credential.createdAt) // Should be current time
        assertNull(credential.imageURL)
        assertNull(credential.backgroundColor)
        assertNull(credential.policies)
        assertNull(credential.lockingPolicy)
        assertEquals(false, credential.isLocked)
        assertEquals(PushPlatform.PING_AM.name, credential.platform)
    }

    @Test
    fun `test toString representation`() {
        val credential = PushCredential(
            id = "test-id",
            userId = "user3",
            resourceId = "resource-id",
            issuer = "ForgeRock",
            displayIssuer = "ForgeRock",
            accountName = "user",
            displayAccountName = "user",
            serverEndpoint = "http://example.com/endpoint",
            sharedSecret = "secret"
        )

        val toString = credential.toString()

        // Verify toString contains essential information
        assertTrue(toString.contains("id='test-id'"))
        assertTrue(toString.contains("issuer='ForgeRock'"))
        assertTrue(toString.contains("accountName='user'"))
        assertTrue(toString.contains("userId='user3'"))
        assertTrue(toString.contains("resourceId='resource-id'"))
        assertTrue(toString.contains("serverEndpoint='http://example.com/endpoint'"))
    }

    @Test
    fun `test JSON with unknown properties`() {
        val json = """
            {
              "id": "test-id",
              "issuer": "ForgeRock",
              "displayIssuer": "ForgeRock",
              "accountName": "user",
              "displayAccountName": "user",
              "serverEndpoint": "http://example.com/endpoint",
              "sharedSecret": "secret",
              "unknownProperty": "should be ignored"
            }
        """.trimIndent()

        // Parse should succeed and ignore unknown properties
        val credential = PushCredential.fromJson(json)

        assertEquals("test-id", credential.id)
        assertEquals("ForgeRock", credential.issuer)
    }
}
