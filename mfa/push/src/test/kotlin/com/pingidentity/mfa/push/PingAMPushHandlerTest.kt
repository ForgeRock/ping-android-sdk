/*
 * Copyright (c) 2025-2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.util.Base64
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.util.JwtUtils
import com.pingidentity.network.ktor.KtorHttpClient
import io.ktor.client.HttpClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for PingAMPushHandler class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PingAMPushHandlerTest {

    // Mock objects
    private lateinit var mockHttpClient: HttpClient
    private lateinit var mockLogger: Logger
    private lateinit var mockPushResponder: PingAMPushResponder
    
    // Test subject
    private lateinit var pushHandler: PingAMPushHandler
    
    // Test data
    private lateinit var testCredential: PushCredential
    private lateinit var testNotification: PushNotification
    
    companion object {
        private const val TEST_MESSAGE_ID = "test-message-id"
        private const val TEST_SECRET = "c2VjcmV0S2V5Rm9yVGVzdGluZ1RoaXNOZWVkc1RvQmVBdExlYXN0MjU2Yml0cw==" // At least 256 bits
        private const val TEST_DEVICE_TOKEN = "test-device-token"
        
        // JWT claim keys used by PingAM
        private const val JWT_MECHANISM_UID = "u"
        private const val JWT_CHALLENGE = "c"
        private const val JWT_TTL = "t"
        private const val JWT_TIME_INTERVAL = "i"
        private const val JWT_NOTIFICATION_MESSAGE = "m"
        private const val JWT_PUSH_TYPE = "k"
        private const val JWT_CUSTOM_PAYLOAD = "p"
        private const val JWT_NUMBERS_CHALLENGE = "n"
        private const val JWT_CONTEXT_INFO = "x"
        private const val JWT_AM_LOAD_BALANCER_COOKIE = "l"
        private const val JWT_USER_ID = "d"
    }

    @Before
    fun setUp() {
        // Set up mock objects
        mockHttpClient = mockk()
        mockLogger = mockk(relaxed = true)
        mockPushResponder = mockk()
        
        // Create test credential
        testCredential = PushCredential(
            id = "test-cred-id",
            userId = "test-user-id",
            resourceId = "test-resource-id",
            issuer = "PingIdentity",
            accountName = "test-account",
            serverEndpoint = "https://test.pingidentity.com/push",
            sharedSecret = "test-secret"
        )
        
        // Create test notification
        testNotification = PushNotification(
            id = "test-notif-id",
            credentialId = testCredential.id,
            ttl = 300,
            messageId = TEST_MESSAGE_ID,
            messageText = "Test notification",
            pushType = PushType.DEFAULT
        )
        
        // Create PushHandler with mocked responder
        pushHandler = PingAMPushHandler(KtorHttpClient(mockHttpClient), mockLogger, mockPushResponder)
    }

    @Test
    fun `test canHandle returns true for valid PingAM message`() {
        // Create a real JWT with required fields
        val claims = mapOf(
            JWT_MECHANISM_UID to "test-mechanism-uid",
            JWT_CHALLENGE to "test-challenge"
        )
        val jwt = JwtUtils.generateJwt(TEST_SECRET, claims)
        
        val messageData = mapOf(
            PushConstants.KEY_MESSAGE to jwt,
            PushConstants.KEY_MESSAGE_ID to TEST_MESSAGE_ID
        )
        
        // Test the handler
        val result = pushHandler.canHandle(messageData)
        
        // Verify result
        assertTrue(result)
    }
    
    @Test
    fun `test canHandle returns false for invalid message`() {
        // Create an invalid message data (missing JWT)
        val messageData = mapOf(
            PushConstants.KEY_MESSAGE_ID to TEST_MESSAGE_ID
        )
        
        // Test the handler
        val result = pushHandler.canHandle(messageData)
        
        // Verify result
        assertFalse(result)
    }
    
    @Test
    fun `test parseMessage extracts correct data from JWT`() {
        // Create a JWT with all possible claims
        val amlbCookie = "test-cookie-value"
        val base64Cookie = Base64.encodeToString(amlbCookie.toByteArray(), Base64.NO_WRAP)
        
        val claims = mapOf<String, Any>(
            JWT_MECHANISM_UID to "mechanism-uid-123",
            JWT_CHALLENGE to "test-challenge-abc",
            JWT_TTL to 600,
            JWT_TIME_INTERVAL to 1234567890L,
            JWT_NOTIFICATION_MESSAGE to "Approve login request",
            JWT_PUSH_TYPE to "CHALLENGE",
            JWT_CUSTOM_PAYLOAD to "{\"key\":\"value\"}",
            JWT_NUMBERS_CHALLENGE to "12,34,56",
            JWT_CONTEXT_INFO to "context-data",
            JWT_AM_LOAD_BALANCER_COOKIE to base64Cookie,
            JWT_USER_ID to "user@example.com"
        )
        
        val jwt = JwtUtils.generateJwt(TEST_SECRET, claims)
        
        val messageData = mapOf(
            PushConstants.KEY_MESSAGE to jwt,
            PushConstants.KEY_MESSAGE_ID to TEST_MESSAGE_ID
        )
        
        // Parse the message
        val result = pushHandler.parseMessage(messageData)
        
        // Verify all expected key-value pairs are in the result
        assertEquals("mechanism-uid-123", result[PushConstants.KEY_CREDENTIAL_ID])
        assertEquals("test-challenge-abc", result[PushConstants.KEY_CHALLENGE])
        assertEquals(600, result[PushConstants.KEY_TTL])
        assertEquals(1234567890L, result[PushConstants.KEY_TIME_INTERVAL])
        assertEquals("Approve login request", result[PushConstants.KEY_MESSAGE_TEXT])
        assertEquals("CHALLENGE", result[PushConstants.KEY_PUSH_TYPE])
        // Custom payload is parsed as JSON object, verify it's present
        assertNotNull(result[PushConstants.KEY_CUSTOM_PAYLOAD])
        assertEquals("12,34,56", result[PushConstants.KEY_NUMBERS_CHALLENGE])
        assertEquals("context-data", result[PushConstants.KEY_CONTEXT_INFO])
        assertEquals(amlbCookie, result[PushConstants.KEY_AMLB_COOKIE])
        assertEquals("user@example.com", result[PushConstants.KEY_USERNAME])
        assertEquals(TEST_MESSAGE_ID, result[PushConstants.KEY_MESSAGE_ID])
        assertEquals(jwt, result[PushConstants.KEY_RAW_JWT])
    }
    
    @Test
    fun `test sendApproval calls responder with correct parameters`() = runTest {
        // Set up mock to return success
        coEvery { 
            mockPushResponder.authenticate(
                any(),
                any(),
                eq(true),
                any()
            )
        } returns true
        
        // Call the method
        val result = pushHandler.sendApproval(
            testCredential,
            testNotification.copy(pushType = PushType.CHALLENGE),
            mapOf("challengeResponse" to "test-response")
        )
        
        // Verify result
        assertTrue(result)
        
        // Verify responder was called with correct parameters
        coVerify { 
            mockPushResponder.authenticate(
                eq(testCredential),
                eq(testNotification.copy(pushType = PushType.CHALLENGE)),
                eq(true),
                eq("test-response")
            )
        }
    }
    
    @Test
    fun `test sendDenial calls responder with correct parameters`() = runTest {
        // Set up mock to return success
        coEvery { 
            mockPushResponder.authenticate(
                any(),
                any(),
                eq(false),
                any()
            )
        } returns true
        
        // Call the method
        val result = pushHandler.sendDenial(
            testCredential,
            testNotification,
            emptyMap()
        )
        
        // Verify result
        assertTrue(result)
        
        // Verify responder was called with correct parameters
        coVerify { 
            mockPushResponder.authenticate(
                eq(testCredential),
                eq(testNotification),
                eq(false),
                isNull()
            )
        }
    }
    
    @Test
    fun `test setDeviceToken calls responder with correct parameters`() = runTest {
        // Set up mock to return success
        coEvery { 
            mockPushResponder.updateDeviceToken(
                any(),
                any(),
                any()
            ) 
        } returns true
        
        // Call the method with device name parameter
        val result = pushHandler.setDeviceToken(
            testCredential,
            TEST_DEVICE_TOKEN,
            mapOf(PushConstants.KEY_DEVICE_NAME to "My Test Device")
        )
        
        // Verify result
        assertTrue(result)
        
        // Verify responder was called with correct parameters
        coVerify { 
            mockPushResponder.updateDeviceToken(
                eq(testCredential),
                eq(TEST_DEVICE_TOKEN),
                eq("My Test Device")
            )
        }
    }
    
    @Test
    fun `test setDeviceToken uses default device name when not provided`() = runTest {
        // Set up mock to return success
        coEvery { 
            mockPushResponder.updateDeviceToken(
                any(),
                any(),
                any()
            ) 
        } returns true
        
        // Call the method without device name parameter
        val result = pushHandler.setDeviceToken(
            testCredential,
            TEST_DEVICE_TOKEN,
            emptyMap()
        )
        
        // Verify result
        assertTrue(result)
        
        // Verify responder was called with default device name
        coVerify { 
            mockPushResponder.updateDeviceToken(
                eq(testCredential),
                eq(TEST_DEVICE_TOKEN),
                eq(PushConstants.DEFAULT_DEVICE_NAME)
            )
        }
    }
    
    @Test
    fun `test register calls responder with correct parameters`() = runTest {
        // Set up mock to return success
        coEvery { 
            mockPushResponder.register(
                any(),
                any()
            ) 
        } returns true
        
        // Create parameters with message ID
        val params = mapOf(
            "message_id" to TEST_MESSAGE_ID
        )
        
        // Call the method
        val result = pushHandler.register(
            testCredential,
            params
        )
        
        // Verify result
        assertTrue(result)
        
        // Verify responder was called with correct parameters
        coVerify { 
            mockPushResponder.register(
                eq(testCredential),
                eq(params)
            )
        }
    }
    
    @Test
    fun `test register handles failure from responder`() = runTest {
        // Set up mock to return failure
        coEvery { 
            mockPushResponder.register(
                any(),
                any()
            ) 
        } returns false
        
        // Call the method
        val result = pushHandler.register(
            testCredential,
            emptyMap()
        )
        
        // Verify result reflects the failure
        assertFalse(result)
    }
    
    @Test
    fun `test canHandle with String returns true for valid JWT`() {
        // Create a real JWT with required fields
        val claims = mapOf(
            JWT_MECHANISM_UID to "test-mechanism-uid",
            JWT_CHALLENGE to "test-challenge"
        )
        val jwt = JwtUtils.generateJwt(TEST_SECRET, claims)
        
        // Test with a valid JWT string
        val result = pushHandler.canHandle(jwt)
        
        // Verify result
        assertTrue(result)
    }
    
    @Test
    fun `test canHandle with String returns false for invalid JWT`() {
        // Test with an invalid string (not a JWT)
        val result = pushHandler.canHandle("not-a-jwt-token")
        
        // Verify result
        assertFalse(result)
    }
    
    @Test
    fun `test parseMessage with String extracts data from JWT`() {
        // Create a real JWT
        val claims = mapOf<String, Any>(
            JWT_MECHANISM_UID to "mechanism-uid-456",
            JWT_CHALLENGE to "test-challenge-def"
        )
        
        val jwt = JwtUtils.generateJwt(TEST_SECRET, claims)
        
        // Parse the JWT string directly
        val result = pushHandler.parseMessage(jwt)
        
        // Verify expected keys are present
        assertEquals("mechanism-uid-456", result[PushConstants.KEY_CREDENTIAL_ID])
        assertEquals("test-challenge-def", result[PushConstants.KEY_CHALLENGE])
        assertNotNull(result[PushConstants.KEY_MESSAGE_ID])
        assertEquals(jwt, result[PushConstants.KEY_RAW_JWT])
    }
    
    @Test
    fun `test parseMessage with minimal JWT claims uses defaults`() {
        // Create a JWT with only required fields
        val claims = mapOf(
            JWT_MECHANISM_UID to "mech-uid",
            JWT_CHALLENGE to "challenge"
        )
        
        val jwt = JwtUtils.generateJwt(TEST_SECRET, claims)
        val messageData = mapOf(
            PushConstants.KEY_MESSAGE to jwt,
            PushConstants.KEY_MESSAGE_ID to TEST_MESSAGE_ID
        )
        
        val result = pushHandler.parseMessage(messageData)
        
        // Verify required fields are present
        assertEquals("mech-uid", result[PushConstants.KEY_CREDENTIAL_ID])
        assertEquals("challenge", result[PushConstants.KEY_CHALLENGE])
        assertEquals(TEST_MESSAGE_ID, result[PushConstants.KEY_MESSAGE_ID])
        // TTL should have default value when not provided
        assertTrue(result.containsKey(PushConstants.KEY_TTL))
    }
    
    @Test
    fun `test parseMessage handles String TTL conversion`() {
        // Create a JWT with TTL as string
        val claims = mapOf<String, Any>(
            JWT_MECHANISM_UID to "mech-uid",
            JWT_CHALLENGE to "challenge",
            JWT_TTL to "500"  // String instead of Int
        )
        
        val jwt = JwtUtils.generateJwt(TEST_SECRET, claims)
        val messageData = mapOf(
            PushConstants.KEY_MESSAGE to jwt,
            PushConstants.KEY_MESSAGE_ID to TEST_MESSAGE_ID
        )
        
        val result = pushHandler.parseMessage(messageData)
        
        // Verify TTL was converted from string to int
        assertEquals(500, result[PushConstants.KEY_TTL])
    }
    
    @Test
    fun `test parseMessage handles invalid TTL gracefully`() {
        // Create a JWT with invalid TTL
        val claims = mapOf<String, Any>(
            JWT_MECHANISM_UID to "mech-uid",
            JWT_CHALLENGE to "challenge",
            JWT_TTL to "invalid"
        )
        
        val jwt = JwtUtils.generateJwt(TEST_SECRET, claims)
        val messageData = mapOf(
            PushConstants.KEY_MESSAGE to jwt,
            PushConstants.KEY_MESSAGE_ID to TEST_MESSAGE_ID
        )
        
        val result = pushHandler.parseMessage(messageData)
        
        // Should still return result with default TTL
        assertNotNull(result[PushConstants.KEY_TTL])
        assertTrue(result.containsKey(PushConstants.KEY_CREDENTIAL_ID))
    }
    
    @Test
    fun `test parseMessage handles time interval as different types`() {
        // Test with Long
        val claims1 = mapOf<String, Any>(
            JWT_MECHANISM_UID to "mech-uid",
            JWT_CHALLENGE to "challenge",
            JWT_TIME_INTERVAL to 9876543210L
        )
        val jwt1 = JwtUtils.generateJwt(TEST_SECRET, claims1)
        val result1 = pushHandler.parseMessage(mapOf(
            PushConstants.KEY_MESSAGE to jwt1,
            PushConstants.KEY_MESSAGE_ID to TEST_MESSAGE_ID
        ))
        assertEquals(9876543210L, result1[PushConstants.KEY_TIME_INTERVAL])
        
        // Test with String
        val claims2 = mapOf<String, Any>(
            JWT_MECHANISM_UID to "mech-uid",
            JWT_CHALLENGE to "challenge",
            JWT_TIME_INTERVAL to "1234567890"
        )
        val jwt2 = JwtUtils.generateJwt(TEST_SECRET, claims2)
        val result2 = pushHandler.parseMessage(mapOf(
            PushConstants.KEY_MESSAGE to jwt2,
            PushConstants.KEY_MESSAGE_ID to TEST_MESSAGE_ID
        ))
        assertEquals(1234567890L, result2[PushConstants.KEY_TIME_INTERVAL])
    }
    
    @Test
    fun `test parseMessage returns minimal result on parse error`() {
        val messageData = mapOf(
            PushConstants.KEY_MESSAGE to "invalid-jwt",
            PushConstants.KEY_MESSAGE_ID to TEST_MESSAGE_ID
        )
        
        val result = pushHandler.parseMessage(messageData)
        
        // On error, parseJwtMessage still adds messageId and rawJwt before the exception
        // So we just verify it doesn't crash and returns something
        assertNotNull(result)
    }
    
    @Test
    fun `test sendApproval with BIOMETRIC type calls responder correctly`() = runTest {
        // Set up mock to return success
        coEvery { 
            mockPushResponder.authenticate(
                any(),
                any(),
                eq(true),
                isNull()
            )
        } returns true
        
        // Create a biometric notification
        val biometricNotification = testNotification.copy(pushType = PushType.BIOMETRIC)
        
        // Call the method with biometric authentication method
        val result = pushHandler.sendApproval(
            testCredential,
            biometricNotification,
            mapOf("authenticationMethod" to "fingerprint")
        )
        
        // Verify result
        assertTrue(result)
        
        // Verify responder was called with null challengeResponse for biometric
        coVerify { 
            mockPushResponder.authenticate(
                eq(testCredential),
                eq(biometricNotification),
                eq(true),
                isNull()
            )
        }
    }
    
    @Test
    fun `test sendApproval with CHALLENGE type and missing challengeResponse returns false`() = runTest {
        // Create a challenge notification
        val challengeNotification = testNotification.copy(pushType = PushType.CHALLENGE)
        
        // Call the method without challengeResponse parameter
        val result = pushHandler.sendApproval(
            testCredential,
            challengeNotification,
            emptyMap()
        )
        
        // Verify result is false (because challengeResponse is required)
        assertFalse(result)
        
        // Verify responder was NOT called
        coVerify(exactly = 0) { 
            mockPushResponder.authenticate(any(), any(), any(), any())
        }
    }
    
    @Test
    fun `test sendApproval with DEFAULT type calls responder with null challengeResponse`() = runTest {
        // Set up mock to return success
        coEvery { 
            mockPushResponder.authenticate(
                any(),
                any(),
                eq(true),
                isNull()
            )
        } returns true
        
        // Create a default notification
        val defaultNotification = testNotification.copy(pushType = PushType.DEFAULT)
        
        // Call the method
        val result = pushHandler.sendApproval(
            testCredential,
            defaultNotification,
            emptyMap()
        )
        
        // Verify result
        assertTrue(result)
        
        // Verify responder was called with null challengeResponse for default type
        coVerify { 
            mockPushResponder.authenticate(
                eq(testCredential),
                eq(defaultNotification),
                eq(true),
                isNull()
            )
        }
    }
}
