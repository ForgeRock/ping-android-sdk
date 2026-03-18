/*
 * Copyright (c) 2025-2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.util.Base64
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.push.PushConstants.ANDROID
import com.pingidentity.mfa.push.PushConstants.GCM
import com.pingidentity.mfa.push.PushConstants.KEY_ALMB_COOKIE
import com.pingidentity.mfa.push.PushConstants.KEY_CHALLENGE
import com.pingidentity.mfa.push.PushConstants.KEY_CHALLENGE_RESPONSE
import com.pingidentity.mfa.push.PushConstants.KEY_COMMUNICATION_TYPE
import com.pingidentity.mfa.push.PushConstants.KEY_DENY
import com.pingidentity.mfa.push.PushConstants.KEY_DEVICE_ID
import com.pingidentity.mfa.push.PushConstants.KEY_DEVICE_NAME
import com.pingidentity.mfa.push.PushConstants.KEY_DEVICE_TYPE
import com.pingidentity.mfa.push.PushConstants.KEY_JWT
import com.pingidentity.mfa.push.PushConstants.KEY_MECHANISM_UID
import com.pingidentity.mfa.push.PushConstants.KEY_MESSAGE_ID
import com.pingidentity.mfa.push.PushConstants.KEY_RESPONSE
import com.pingidentity.mfa.push.PushConstants.KEY_USERNAME
import com.pingidentity.network.ktor.KtorHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Unit tests for PingAMPushResponder class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PingAMPushResponderTest {

    companion object {
        private const val BASE64_SECRET = "b3uYLkQ7dRPjBaIzV0t/aijoXRgMq+NP5AwVAvRfa/E="
        private const val BASE64_CHALLENGE = "9giiBAdUHjqpo0XE4YdZ7pRlv0hrQYwDz8Z1wwLLbkg="
        private const val EXPECTED_CHALLENGE_RESPONSE = "Df02AwA3Ra+sTGkL5+QvkEtN3eLdZiFmL5nxAV1m0k8="
        private const val EXPECTED_NUMBER_CHALLENGE_RESPONSE = "80"
        private const val TEST_ENDPOINT = "http://test.example.com/push"
        private const val TEST_MESSAGE_ID = "test-message-id"
        private const val TEST_DEVICE_TOKEN = "test-fcm-token"
        private const val TEST_DEVICE_NAME = "Test Device"
        private const val TEST_AMLB_COOKIE = "testCookie"
        private const val TEST_ACTION = "test-action"
    }

    private lateinit var mockLogger: Logger
    private lateinit var testCredential: PushCredential
    private lateinit var testNotification: PushNotification

    @Before
    fun setUp() {
        // Set up mock logger
        mockLogger = mockk(relaxed = true)

        // Create a test credential
        testCredential = PushCredential(
            id = "test-id",
            userId = "user3",
            resourceId = "test-id",
            issuer = "ForgeRock",
            displayIssuer = "ForgeRock Display",
            accountName = "user",
            displayAccountName = "User Display",
            serverEndpoint = TEST_ENDPOINT,
            sharedSecret = BASE64_SECRET,
            createdAt = Date(),
            platform = PushPlatform.PING_AM.name
        )

        // Create a test notification
        testNotification = PushNotification(
            id = "test-notification-id",
            messageId = TEST_MESSAGE_ID,
            credentialId = testCredential.id,
            challenge = BASE64_CHALLENGE,
            loadBalancer = TEST_AMLB_COOKIE,
            ttl = 60,
            pushType = PushType.DEFAULT,
            approved = false,
            pending = true,
            messageText = "Approve login?"
        )
    }

    @Test
    fun `test generateJwt creates valid JWT structure`() {
        // Set up claims for JWT
        val claims = mapOf(
            "messageId" to TEST_MESSAGE_ID,
            "action" to TEST_ACTION
        )

        val mockEngine = MockEngine.Companion {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        val jwt = pushResponder.generateJwt(BASE64_SECRET, claims)

        // Verify JWT structure
        val jwtParts = jwt.split(".")
        Assert.assertEquals("JWT should have three parts", 3, jwtParts.size)

        // Verify header
        val headerJson = String(Base64.decode(jwtParts[0], Base64.URL_SAFE))
        val headerMap = Json.parseToJsonElement(headerJson).jsonObject
        Assert.assertEquals("HS256", headerMap["alg"]?.toString()?.replace("\"", ""))
        Assert.assertEquals("JWT", headerMap["typ"]?.toString()?.replace("\"", ""))

        // Verify payload
        val payloadJson = String(Base64.decode(jwtParts[1], Base64.URL_SAFE))
        val payloadMap = Json.parseToJsonElement(payloadJson).jsonObject
        Assert.assertEquals("\"${TEST_MESSAGE_ID}\"", payloadMap["messageId"].toString())
        Assert.assertEquals("\"$TEST_ACTION\"", payloadMap["action"].toString())

        // Verify signature exists (we don't validate the actual signature here)
        Assert.assertNotNull(jwtParts[2])
        Assert.assertTrue("Signature should not be empty", jwtParts[2].isNotEmpty())
    }

    @Test
    fun `test generateJwt handles various data types correctly`() {
        // Set up claims with different data types
        val claims = mapOf(
            "stringValue" to "test",
            "intValue" to 123,
            "doubleValue" to 123.45,
            "booleanValue" to true,
            "complexValue" to mapOf("key" to "value")  // This should be converted to string
        )

        val mockEngine = MockEngine.Companion {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        val jwt = pushResponder.generateJwt(BASE64_SECRET, claims)

        // Verify JWT structure
        val jwtParts = jwt.split(".")
        Assert.assertEquals("JWT should have three parts", 3, jwtParts.size)

        // Verify payload contains all values
        val payloadJson = String(Base64.decode(jwtParts[1], Base64.URL_SAFE))
        val payloadMap = Json.parseToJsonElement(payloadJson).jsonObject

        Assert.assertEquals("\"test\"", payloadMap["stringValue"].toString())
        Assert.assertEquals("123", payloadMap["intValue"].toString())
        Assert.assertEquals("123.45", payloadMap["doubleValue"].toString())
        Assert.assertEquals("true", payloadMap["booleanValue"].toString())
        // Complex value should be converted to string representation
        Assert.assertTrue(payloadMap.containsKey("complexValue"))
    }

    @Test
    fun `test generateChallengeResponse produces correct response`() {
        // Create a basic mock setup for this test
        val mockEngine = MockEngine.Companion {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        val response = pushResponder.generateChallengeResponse(BASE64_SECRET, BASE64_CHALLENGE)
        Assert.assertEquals(EXPECTED_CHALLENGE_RESPONSE, response)

        httpClient.close()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test generateChallengeResponse with empty secret throws exception`() {
        // Create a basic mock setup for this test
        val mockEngine = MockEngine.Companion {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        pushResponder.generateChallengeResponse("", BASE64_CHALLENGE)

        httpClient.close()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test generateChallengeResponse with invalid secret throws exception`() {
        // Create a basic mock setup for this test
        val mockEngine = MockEngine.Companion {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        pushResponder.generateChallengeResponse("invalid base64 !@#", BASE64_CHALLENGE)

        httpClient.close()
    }

    @Test
    fun `test register with valid parameters returns success`() = runTest {
        // Set up the mock engine to return success
        val mockEngine = MockEngine.Companion { request ->
            // Verify request headers and URL
            Assert.assertEquals(
                PushConstants.ACCEPT_API_VERSION,
                request.headers[PushConstants.HEADER_ACCEPT_API_VERSION]
            )
            Assert.assertEquals(TEST_AMLB_COOKIE, request.headers[PushConstants.HEADER_COOKIE])

            // Check that the URL includes the action=register parameter
            val url = request.url.toString()
            Assert.assertEquals("${TEST_ENDPOINT}?_action=register", url)

            // Capture and verify the request body content
            val requestBodyText = String(request.body.toByteArray())
            val requestBodyJson = Json.parseToJsonElement(requestBodyText).jsonObject

            // Body contains expected keys
            Assert.assertTrue(requestBodyJson.containsKey(KEY_MESSAGE_ID))

            // Verify JWT is present and has expected structure
            Assert.assertTrue(requestBodyJson.containsKey(KEY_JWT))
            val jwt = requestBodyJson[KEY_JWT].toString().replace("\"", "")
            val jwtParts = jwt.split(".")
            Assert.assertEquals(3, jwtParts.size)

            // Jwt payload should contain messageId, deviceId, and deviceName
            val jwtPayload = String(Base64.decode(jwtParts[1], Base64.URL_SAFE))
            val jwtPayloadJson = Json.parseToJsonElement(jwtPayload).jsonObject
            Assert.assertEquals("\"${ANDROID}\"", jwtPayloadJson[KEY_DEVICE_TYPE].toString())
            Assert.assertEquals("\"${GCM}\"", jwtPayloadJson[KEY_COMMUNICATION_TYPE].toString())
            Assert.assertEquals("\"${TEST_DEVICE_TOKEN}\"", jwtPayloadJson[KEY_DEVICE_ID].toString())
            Assert.assertEquals("\"${TEST_DEVICE_NAME}\"", jwtPayloadJson[KEY_DEVICE_NAME].toString())
            Assert.assertEquals("\"${testCredential.id}\"", jwtPayloadJson[KEY_MECHANISM_UID].toString())
            Assert.assertEquals("\"${EXPECTED_CHALLENGE_RESPONSE}\"", jwtPayloadJson[KEY_RESPONSE].toString())

            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Set up test parameters
        val params = mapOf<String, Any>(
            KEY_MESSAGE_ID to TEST_MESSAGE_ID,
            KEY_ALMB_COOKIE to TEST_AMLB_COOKIE,
            KEY_DEVICE_ID to TEST_DEVICE_TOKEN,
            KEY_DEVICE_NAME to TEST_DEVICE_NAME,
            KEY_CHALLENGE to BASE64_CHALLENGE
        )

        // Call the method under test
        val result = pushResponder.register(testCredential, params)

        // Verify result
        Assert.assertTrue(result)

        httpClient.close()
    }

    @Test
    fun `test register with server error returns failure`() = runTest {
        // Set up the mock engine to return an error
        val mockEngine = MockEngine.Companion { _ ->
            respondError(HttpStatusCode.NotFound)
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Set up test parameters
        val params = mapOf<String, Any>(
            KEY_MESSAGE_ID to TEST_MESSAGE_ID,
            KEY_ALMB_COOKIE to TEST_AMLB_COOKIE,
            KEY_DEVICE_ID to TEST_DEVICE_TOKEN,
            KEY_DEVICE_NAME to TEST_DEVICE_NAME
        )

        // Call the method under test
        val result = pushResponder.register(testCredential, params)

        // Verify result
        Assert.assertFalse(result)

        httpClient.close()
    }

    @Test
    fun `test register with exception returns failure`() = runTest {
        // Set up the mock engine to throw an exception
        val mockEngine = MockEngine.Companion { _ ->
            throw RuntimeException("Network error")
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Set up test parameters
        val params = mapOf<String, Any>(
            KEY_MESSAGE_ID to TEST_MESSAGE_ID,
            KEY_ALMB_COOKIE to TEST_AMLB_COOKIE,
            KEY_DEVICE_ID to TEST_DEVICE_TOKEN,
            KEY_DEVICE_NAME to TEST_DEVICE_NAME
        )

        // Call the method under test
        val result = pushResponder.register(testCredential, params)

        // Verify result
        Assert.assertFalse(result)

        httpClient.close()
    }

    @Test
    fun `test updateDeviceToken with valid parameters returns success`() = runTest {
        // Set up the mock engine to return success
        val mockEngine = MockEngine.Companion { request ->
            // Verify request headers and URL
            Assert.assertEquals(
                PushConstants.ACCEPT_API_VERSION,
                request.headers[PushConstants.HEADER_ACCEPT_API_VERSION]
            )

            // Check that the URL includes the action=refresh parameter
            val url = request.url.toString()
            Assert.assertEquals("${TEST_ENDPOINT}?_action=refresh", url)

            // Capture and verify the request body content
            val requestBodyText = String(request.body.toByteArray())
            val requestBodyJson = Json.parseToJsonElement(requestBodyText).jsonObject

            // Body contains expected keys
            Assert.assertTrue(requestBodyJson.containsKey(KEY_MECHANISM_UID))
            Assert.assertTrue(requestBodyJson.containsKey(KEY_USERNAME))

            // Verify JWT is present and has expected structure
            Assert.assertTrue(requestBodyJson.containsKey(KEY_JWT))
            val jwt = requestBodyJson[KEY_JWT].toString().replace("\"", "")
            val jwtParts = jwt.split(".")
            Assert.assertEquals(3, jwtParts.size)

            // Jwt payload should contain messageId, deviceId, and deviceName
            val jwtPayload = String(Base64.decode(jwtParts[1], Base64.URL_SAFE))
            val jwtPayloadJson = Json.parseToJsonElement(jwtPayload).jsonObject
            Assert.assertEquals("\"${ANDROID}\"", jwtPayloadJson[KEY_DEVICE_TYPE].toString())
            Assert.assertEquals("\"${GCM}\"", jwtPayloadJson[KEY_COMMUNICATION_TYPE].toString())
            Assert.assertEquals("\"${TEST_DEVICE_TOKEN}\"", jwtPayloadJson[KEY_DEVICE_ID].toString())
            Assert.assertEquals("\"${TEST_DEVICE_NAME}\"", jwtPayloadJson[KEY_DEVICE_NAME].toString())

            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Call the method under test
        val result =
            pushResponder.updateDeviceToken(testCredential, TEST_DEVICE_TOKEN, TEST_DEVICE_NAME)

        // Verify result
        Assert.assertTrue(result)

        httpClient.close()
    }

    @Test
    fun `test updateDeviceToken with server error returns failure`() = runTest {
        // Set up the mock engine to return an error
        val mockEngine = MockEngine.Companion { _ ->
            respondError(HttpStatusCode.NotFound)
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Call the method under test
        val result =
            pushResponder.updateDeviceToken(testCredential, TEST_DEVICE_TOKEN, TEST_DEVICE_NAME)

        // Verify result
        Assert.assertFalse(result)

        httpClient.close()
    }

    @Test
    fun `test updateDeviceToken with exception returns failure`() = runTest {
        // Set up the mock engine to throw an exception
        val mockEngine = MockEngine.Companion { _ ->
            throw RuntimeException("Network error")
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Call the method under test
        val result =
            pushResponder.updateDeviceToken(testCredential, TEST_DEVICE_TOKEN, TEST_DEVICE_NAME)

        // Verify result
        Assert.assertFalse(result)

        httpClient.close()
    }

    @Test
    fun `test authenticate approve with valid parameters returns success`() = runTest {
        // Set up the mock engine to return success
        val mockEngine = MockEngine.Companion { request ->
            // Verify request headers and URL
            Assert.assertEquals(
                PushConstants.ACCEPT_API_VERSION,
                request.headers[PushConstants.HEADER_ACCEPT_API_VERSION]
            )
            Assert.assertEquals(TEST_AMLB_COOKIE, request.headers[PushConstants.HEADER_COOKIE])

            // Check that the URL includes the action=authenticate parameter
            val url = request.url.toString()
            Assert.assertEquals("${TEST_ENDPOINT}?_action=authenticate", url)

            // Capture and verify the request body content
            val requestBodyText = String(request.body.toByteArray())
            val requestBodyJson = Json.parseToJsonElement(requestBodyText).jsonObject

            // Body contains expected keys
            Assert.assertTrue(requestBodyJson.containsKey(KEY_MESSAGE_ID))

            // Verify JWT is present and has expected structure
            Assert.assertTrue(requestBodyJson.containsKey(KEY_JWT))
            val jwt = requestBodyJson[KEY_JWT].toString().replace("\"", "")
            val jwtParts = jwt.split(".")
            Assert.assertEquals(3, jwtParts.size)

            // Jwt payload should contain messageId, deviceId, and deviceName
            val jwtPayload = String(Base64.decode(jwtParts[1], Base64.URL_SAFE))
            val jwtPayloadJson = Json.parseToJsonElement(jwtPayload).jsonObject
            Assert.assertEquals("\"${EXPECTED_CHALLENGE_RESPONSE}\"", jwtPayloadJson[KEY_RESPONSE].toString())

            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Call the method under test
        val result = pushResponder.authenticate(testCredential, testNotification, true, null)

        // Verify result
        Assert.assertTrue(result)

        httpClient.close()
    }

    @Test
    fun `test authenticate deny with valid parameters returns success`() = runTest {
        // Set up the mock engine to return success
        val mockEngine = MockEngine.Companion { request ->
            // Verify request headers and URL
            Assert.assertEquals(
                PushConstants.ACCEPT_API_VERSION,
                request.headers[PushConstants.HEADER_ACCEPT_API_VERSION]
            )
            Assert.assertEquals(TEST_AMLB_COOKIE, request.headers[PushConstants.HEADER_COOKIE])

            // Check that the URL includes the action=authenticate parameter
            val url = request.url.toString()
            Assert.assertEquals("${TEST_ENDPOINT}?_action=authenticate", url)

            // Capture and verify the request body content
            val requestBodyText = String(request.body.toByteArray())
            val requestBodyJson = Json.parseToJsonElement(requestBodyText).jsonObject

            // Body contains expected keys
            Assert.assertTrue(requestBodyJson.containsKey(KEY_MESSAGE_ID))

            // Verify JWT is present and has expected structure
            Assert.assertTrue(requestBodyJson.containsKey(KEY_JWT))
            val jwt = requestBodyJson[KEY_JWT].toString().replace("\"", "")
            val jwtParts = jwt.split(".")
            Assert.assertEquals(3, jwtParts.size)

            // Jwt payload should contain messageId, deviceId, and deviceName
            val jwtPayload = String(Base64.decode(jwtParts[1], Base64.URL_SAFE))
            val jwtPayloadJson = Json.parseToJsonElement(jwtPayload).jsonObject
            Assert.assertTrue(jwtPayloadJson[KEY_DENY].toString().toBoolean())
            Assert.assertEquals("\"${EXPECTED_CHALLENGE_RESPONSE}\"", jwtPayloadJson[KEY_RESPONSE].toString())

            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Call the method under test
        val result = pushResponder.authenticate(testCredential, testNotification, false, null)

        // Verify result
        Assert.assertTrue(result)

        httpClient.close()
    }

    @Test
    fun `test authenticate with number challenge response returns success`() = runTest {
        // Set up the mock engine to return success
        val mockEngine = MockEngine.Companion { request ->
            // Check that the URL includes the action=authenticate parameter
            val url = request.url.toString()
            Assert.assertEquals("${TEST_ENDPOINT}?_action=authenticate", url)

            // Capture and verify the request body content
            val requestBodyText = String(request.body.toByteArray())
            val requestBodyJson = Json.parseToJsonElement(requestBodyText).jsonObject

            // Body contains expected keys
            Assert.assertTrue(requestBodyJson.containsKey(KEY_MESSAGE_ID))

            // Verify JWT is present and has expected structure
            Assert.assertTrue(requestBodyJson.containsKey(KEY_JWT))
            val jwt = requestBodyJson[KEY_JWT].toString().replace("\"", "")
            val jwtParts = jwt.split(".")
            Assert.assertEquals(3, jwtParts.size)

            // Jwt payload should contain messageId, deviceId, and deviceName
            val jwtPayload = String(Base64.decode(jwtParts[1], Base64.URL_SAFE))
            val jwtPayloadJson = Json.parseToJsonElement(jwtPayload).jsonObject
            Assert.assertEquals("\"${EXPECTED_NUMBER_CHALLENGE_RESPONSE}\"", jwtPayloadJson[KEY_CHALLENGE_RESPONSE].toString())
            Assert.assertEquals("\"${EXPECTED_CHALLENGE_RESPONSE}\"", jwtPayloadJson[KEY_RESPONSE].toString())

            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Call the method under test
        val result = pushResponder.authenticate(testCredential, testNotification, true, "80")

        // Verify result
        Assert.assertTrue(result)

        httpClient.close()
    }

    @Test
    fun `test authenticate with server error returns failure`() = runTest {
        // Set up the mock engine to return an error
        val mockEngine = MockEngine.Companion { _ ->
            respondError(HttpStatusCode.NotFound)
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Call the method under test
        val result = pushResponder.authenticate(testCredential, testNotification, true, null)

        // Verify result
        Assert.assertFalse(result)

        httpClient.close()
    }

    @Test
    fun `test authenticate with exception returns failure`() = runTest {
        // Set up the mock engine to throw an exception
        val mockEngine = MockEngine.Companion { _ ->
            throw RuntimeException("Network error")
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Call the method under test
        val result = pushResponder.authenticate(testCredential, testNotification, true, null)

        // Verify result
        Assert.assertFalse(result)

        httpClient.close()
    }

    @Test
    fun `test authenticate with null challenge throws exception and returns false`() = runTest {
        // Set up the mock engine to return success (won't be reached)
        val mockEngine = MockEngine.Companion {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Create notification with null challenge
        val notificationWithoutChallenge = PushNotification(
            id = "test-notification-id",
            messageId = TEST_MESSAGE_ID,
            credentialId = testCredential.id,
            challenge = null,  // null challenge
            loadBalancer = TEST_AMLB_COOKIE,
            ttl = 60,
            pushType = PushType.DEFAULT,
            approved = false,
            pending = true,
            messageText = "Approve login?"
        )

        // Call the method under test - should return false due to exception
        val result = pushResponder.authenticate(testCredential, notificationWithoutChallenge, true, null)

        // Verify result is false
        Assert.assertFalse(result)

        httpClient.close()
    }

    @Test
    fun `test register without deviceId throws exception and returns false`() = runTest {
        // Set up the mock engine to return success (won't be reached)
        val mockEngine = MockEngine.Companion {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Set up test parameters without deviceId
        val params = mapOf<String, Any>(
            KEY_MESSAGE_ID to TEST_MESSAGE_ID,
            KEY_ALMB_COOKIE to TEST_AMLB_COOKIE,
            KEY_DEVICE_NAME to TEST_DEVICE_NAME,
            KEY_CHALLENGE to BASE64_CHALLENGE
            // Missing KEY_DEVICE_ID
        )

        // Call the method under test - should return false due to exception
        val result = pushResponder.register(testCredential, params)

        // Verify result is false
        Assert.assertFalse(result)

        httpClient.close()
    }

    @Test
    fun `test register without messageId throws exception and returns false`() = runTest {
        // Set up the mock engine to return success (won't be reached)
        val mockEngine = MockEngine.Companion {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Set up test parameters without messageId
        val params = mapOf<String, Any>(
            KEY_ALMB_COOKIE to TEST_AMLB_COOKIE,
            KEY_DEVICE_ID to TEST_DEVICE_TOKEN,
            KEY_DEVICE_NAME to TEST_DEVICE_NAME,
            KEY_CHALLENGE to BASE64_CHALLENGE
            // Missing KEY_MESSAGE_ID
        )

        // Call the method under test - should return false due to exception
        val result = pushResponder.register(testCredential, params)

        // Verify result is false
        Assert.assertFalse(result)

        httpClient.close()
    }

    @Test
    fun `test register without challenge throws exception and returns false`() = runTest {
        // Set up the mock engine to return success (won't be reached)
        val mockEngine = MockEngine.Companion {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        val pushResponder = PingAMPushResponder(httpClient, mockLogger)

        // Set up test parameters without challenge
        val params = mapOf<String, Any>(
            KEY_MESSAGE_ID to TEST_MESSAGE_ID,
            KEY_ALMB_COOKIE to TEST_AMLB_COOKIE,
            KEY_DEVICE_ID to TEST_DEVICE_TOKEN,
            KEY_DEVICE_NAME to TEST_DEVICE_NAME
            // Missing KEY_CHALLENGE
        )

        // Call the method under test - should return false due to exception
        val result = pushResponder.register(testCredential, params)

        // Verify result is false
        Assert.assertFalse(result)

        httpClient.close()
    }

}