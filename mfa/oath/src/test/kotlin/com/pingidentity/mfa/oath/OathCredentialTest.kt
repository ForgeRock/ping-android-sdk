/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID

/**
 * Unit tests for the OathCredential class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@ExperimentalCoroutinesApi
class OathCredentialTest {

    private val testSecret = "JBSWY3DPEHPK3PXP"
    private val testIssuer = "Test Issuer"
    private val testAccountName = "testuser@example.com"

    @Test
    fun testCreateOathCredential() {
        val credential = OathCredential(
            issuer = testIssuer,
            displayIssuer = testIssuer,
            accountName = testAccountName,
            displayAccountName = testAccountName,
            oathType = OathType.TOTP,
            secret = testSecret
        )

        assertEquals(testIssuer, credential.issuer)
        assertEquals(testIssuer, credential.displayIssuer)
        assertEquals(testAccountName, credential.accountName)
        assertEquals(testAccountName, credential.displayAccountName)
        assertEquals(OathType.TOTP, credential.oathType)
        assertEquals(testSecret, credential.secret)
        assertEquals(OathAlgorithm.SHA1, credential.oathAlgorithm)
        assertEquals(6, credential.digits)
        assertEquals(30, credential.period)
        assertEquals(0L, credential.counter)
        assertNotNull(credential.id)
        assertNotNull(credential.createdAt)
        assertEquals("", credential.userId)
        assertEquals("", credential.resourceId)
        assertNull(credential.imageURL)
        assertNull(credential.backgroundColor)
        assertNull(credential.policies)
        assertNull(credential.lockingPolicy)
        assertFalse(credential.isLocked)

        // Check transient properties
        assertEquals("TOTP", credential.type)
        assertEquals("SHA1", credential.algorithm)
    }

    @Test
    fun testCreateOathCredentialWithAllParameters() {
        val testId = UUID.randomUUID().toString()
        val testUserId = "user123"
        val testResourceId = "resource456"
        val testCreatedAt = Date()
        val testImageURL = "https://example.com/logo.png"
        val testBackgroundColor = "#FF5733"
        val testPolicies = "{\"policyType\":\"test\"}"
        val testLockingPolicy = "lockPolicy1"
        
        val credential = OathCredential(
            id = testId,
            userId = testUserId,
            resourceId = testResourceId,
            issuer = testIssuer,
            displayIssuer = testIssuer,
            accountName = testAccountName,
            displayAccountName = testAccountName,
            oathType = OathType.HOTP,
            secret = testSecret,
            oathAlgorithm = OathAlgorithm.SHA256,
            digits = 8,
            period = 60,
            counter = 5L,
            createdAt = testCreatedAt,
            imageURL = testImageURL,
            backgroundColor = testBackgroundColor,
            policies = testPolicies,
            lockingPolicy = testLockingPolicy,
            isLocked = true
        )

        assertEquals(testId, credential.id)
        assertEquals(testUserId, credential.userId)
        assertEquals(testResourceId, credential.resourceId)
        assertEquals(testIssuer, credential.issuer)
        assertEquals(testIssuer, credential.displayIssuer)
        assertEquals(testAccountName, credential.accountName)
        assertEquals(testAccountName, credential.displayAccountName)
        assertEquals(OathType.HOTP, credential.oathType)
        assertEquals(testSecret, credential.secret)
        assertEquals(OathAlgorithm.SHA256, credential.oathAlgorithm)
        assertEquals(8, credential.digits)
        assertEquals(60, credential.period)
        assertEquals(5L, credential.counter)
        assertEquals(testCreatedAt, credential.createdAt)
        assertEquals(testImageURL, credential.imageURL)
        assertEquals(testBackgroundColor, credential.backgroundColor)
        assertEquals(testPolicies, credential.policies)
        assertEquals(testLockingPolicy, credential.lockingPolicy)
        assertTrue(credential.isLocked)

        // Check transient properties
        assertEquals("HOTP", credential.type)
        assertEquals("SHA256", credential.algorithm)
    }

    @Test
    fun testFromUri_TOTP() = runTest {
        val uri = "otpauth://totp/Test%20Issuer:testuser@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Test%20Issuer&algorithm=SHA1&digits=6&period=30"
        val credential = OathCredential.fromUri(uri)

        assertEquals(testIssuer, credential.issuer)
        assertEquals(testIssuer, credential.displayIssuer)
        assertEquals(testAccountName, credential.accountName)
        assertEquals(testAccountName, credential.displayAccountName)
        assertEquals(OathType.TOTP, credential.oathType)
        assertEquals(testSecret, credential.secret)
        assertEquals(OathAlgorithm.SHA1, credential.oathAlgorithm)
        assertEquals(6, credential.digits)
        assertEquals(30, credential.period)
        assertEquals(0L, credential.counter)
        assertNotNull(credential.id)
    }

    @Test
    fun testFromUri_HOTP() = runTest {
        val uri = "otpauth://hotp/Test%20Issuer:testuser@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Test%20Issuer&algorithm=SHA1&digits=6&counter=10"
        val credential = OathCredential.fromUri(uri)

        assertEquals(testIssuer, credential.issuer)
        assertEquals(testIssuer, credential.displayIssuer)
        assertEquals(testAccountName, credential.accountName)
        assertEquals(testAccountName, credential.displayAccountName)
        assertEquals(OathType.HOTP, credential.oathType)
        assertEquals(testSecret, credential.secret)
        assertEquals(OathAlgorithm.SHA1, credential.oathAlgorithm)
        assertEquals(6, credential.digits)
        assertEquals(30, credential.period) // Default period should still be set
        assertEquals(10L, credential.counter)
        assertNotNull(credential.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromUri_InvalidScheme() = runTest {
        OathCredential.fromUri("invalid://totp/Test%20Issuer:testuser@example.com?secret=JBSWY3DPEHPK3PXP")
    }

    @Test
    fun testToUri_TOTP() = runTest {
        val credential = OathCredential(
            issuer = testIssuer,
            displayIssuer = testIssuer,
            accountName = testAccountName,
            displayAccountName = testAccountName,
            oathType = OathType.TOTP,
            secret = testSecret,
            oathAlgorithm = OathAlgorithm.SHA1,
            digits = 6,
            period = 30
        )

        val uri = credential.toUri()
        assertTrue(uri.startsWith("otpauth://totp/"))
        assertTrue(uri.contains("secret=$testSecret"))
        assertTrue(uri.contains("issuer=Test%20Issuer"))
    }

    @Test
    fun testToUri_HOTP() = runTest {
        val credential = OathCredential(
            issuer = testIssuer,
            displayIssuer = testIssuer,
            accountName = testAccountName,
            displayAccountName = testAccountName,
            oathType = OathType.HOTP,
            secret = testSecret,
            oathAlgorithm = OathAlgorithm.SHA256,
            digits = 8,
            counter = 5
        )

        val uri = credential.toUri()
        assertTrue(uri.startsWith("otpauth://hotp/"))
        assertTrue(uri.contains("secret=$testSecret"))
        assertTrue(uri.contains("issuer=Test%20Issuer"))
        assertTrue(uri.contains("algorithm=SHA256"))
        assertTrue(uri.contains("digits=8"))
        assertTrue(uri.contains("counter=5"))
    }

    @Test
    fun testToJson() {
        val credential = OathCredential(
            id = "test-id-123",
            userId = "user123",
            resourceId = "resource456",
            issuer = testIssuer,
            displayIssuer = testIssuer,
            accountName = testAccountName,
            displayAccountName = testAccountName,
            oathType = OathType.TOTP,
            secret = testSecret,
            oathAlgorithm = OathAlgorithm.SHA1,
            digits = 6,
            period = 30,
            counter = 0L,
            imageURL = "https://example.com/logo.png",
            backgroundColor = "#FF5733",
            policies = null,
            lockingPolicy = null,
            isLocked = false
        )

        val json = credential.toJson()
        assertTrue(json.contains("\"id\":\"test-id-123\""))
        assertTrue(json.contains("\"userId\":\"user123\""))
        assertTrue(json.contains("\"resourceId\":\"resource456\""))
        assertTrue(json.contains("\"issuer\":\"$testIssuer\""))
        assertTrue(json.contains("\"displayIssuer\":\"$testIssuer\""))
        assertTrue(json.contains("\"accountName\":\"$testAccountName\""))
        assertTrue(json.contains("\"displayAccountName\":\"$testAccountName\""))
        assertTrue(json.contains("\"oathType\":\"TOTP\""))
        assertTrue(json.contains("\"secret\":\"$testSecret\""))
        assertTrue(json.contains("\"oathAlgorithm\":\"SHA1\""))
        assertTrue(json.contains("\"digits\":6"))
        assertTrue(json.contains("\"period\":30"))
        assertTrue(json.contains("\"counter\":0"))
        assertTrue(json.contains("\"imageURL\":\"https://example.com/logo.png\""))
        assertTrue(json.contains("\"backgroundColor\":\"#FF5733\""))
        assertTrue(json.contains("\"isLocked\":false"))
    }

    @Test
    fun testFromJson() {
        val jsonString = """
            {
                "id": "test-id-123",
                "userId": "user123",
                "resourceId": "resource456",
                "issuer": "Test Issuer",
                "displayIssuer": "Test Issuer",
                "accountName": "testuser@example.com",
                "displayAccountName": "testuser@example.com",
                "oathType": "TOTP",
                "secret": "JBSWY3DPEHPK3PXP",
                "oathAlgorithm": "SHA1",
                "digits": 6,
                "period": 30,
                "counter": 0,
                "createdAt": 1688300000000,
                "imageURL": "https://example.com/logo.png",
                "backgroundColor": "#FF5733",
                "isLocked": false
            }
        """.trimIndent()

        val credential = OathCredential.fromJson(jsonString)

        assertEquals("test-id-123", credential.id)
        assertEquals("user123", credential.userId)
        assertEquals("resource456", credential.resourceId)
        assertEquals(testIssuer, credential.issuer)
        assertEquals(testIssuer, credential.displayIssuer)
        assertEquals(testAccountName, credential.accountName)
        assertEquals(testAccountName, credential.displayAccountName)
        assertEquals(OathType.TOTP, credential.oathType)
        assertEquals(testSecret, credential.secret)
        assertEquals(OathAlgorithm.SHA1, credential.oathAlgorithm)
        assertEquals(6, credential.digits)
        assertEquals(30, credential.period)
        assertEquals(0L, credential.counter)
        assertEquals(1688300000000L, credential.createdAt.time)
        assertEquals("https://example.com/logo.png", credential.imageURL)
        assertEquals("#FF5733", credential.backgroundColor)
        assertFalse(credential.isLocked)
    }

    @Test
    fun testJsonSerialization() {
        val originalCredential = OathCredential(
            id = "test-id-123",
            userId = "user123",
            resourceId = "resource456",
            issuer = testIssuer,
            displayIssuer = testIssuer,
            accountName = testAccountName,
            displayAccountName = testAccountName,
            oathType = OathType.TOTP,
            secret = testSecret,
            oathAlgorithm = OathAlgorithm.SHA1,
            digits = 6,
            period = 30,
            counter = 0L,
            imageURL = "https://example.com/logo.png",
            backgroundColor = "#FF5733",
            policies = "{\"policyType\":\"test\"}",
            lockingPolicy = "lockPolicy1",
            isLocked = true
        )

        // Convert to JSON
        val json = originalCredential.toJson()
        
        // Parse back from JSON
        val parsedCredential = OathCredential.fromJson(json)
        
        // Verify the parsed credential matches the original
        assertEquals(originalCredential, parsedCredential)
    }

    @Test
    fun testToString() {
        val credential = OathCredential(
            id = "test-id-123",
            issuer = testIssuer,
            displayIssuer = testIssuer,
            accountName = testAccountName,
            displayAccountName = testAccountName,
            oathType = OathType.TOTP,
            secret = testSecret
        )

        val toString = credential.toString()
        assertTrue(toString.contains("id='test-id-123'"))
        assertTrue(toString.contains("issuer='$testIssuer'"))
        assertTrue(toString.contains("displayIssuer='$testIssuer'"))
        assertTrue(toString.contains("accountName='$testAccountName'"))
        assertTrue(toString.contains("displayAccountName='$testAccountName'"))
        assertTrue(toString.contains("type=TOTP"))
        assertTrue(toString.contains("algorithm=SHA1"))
        assertTrue(toString.contains("digits=6"))
        assertTrue(toString.contains("period=30"))
        assertTrue(toString.contains("counter=0"))
        assertTrue(toString.contains("isLocked=false"))
    }
}
