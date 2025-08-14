/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.util

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Unit tests for the JwtUtils class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class JwtUtilsTest {

    companion object {
        private const val TEST_SECRET = "c2VjcmV0S2V5Rm9yVGVzdGluZ1RoaXNOZWVkc1RvQmVBdExlYXN0MjU2Yml0cw==" // At least 256 bits (32 bytes)
        private const val TEST_CHALLENGE = "dGVzdENoYWxsZW5nZQ==" // Base64 for "testChallenge"
    }

    @Test
    fun `test generateJwt creates valid JWT structure`() {
        // Set up claims for JWT
        val claims = mapOf(
            "sub" to "testUser",
            "iss" to "testIssuer",
            "exp" to (Date().time / 1000 + 3600),
            "intValue" to 123,
            "doubleValue" to 123.45,
            "boolValue" to true
        )

        val jwt = JwtUtils.generateJwt(TEST_SECRET, claims)

        // Verify JWT structure
        val jwtParts = jwt.split(".")
        Assert.assertEquals("JWT should have three parts", 3, jwtParts.size)

        // Verify header
        val headerJson = String(Base64.decode(jwtParts[0], Base64.URL_SAFE))
        val headerMap = Json.Default.parseToJsonElement(headerJson).jsonObject
        Assert.assertEquals("HS256", headerMap["alg"]?.toString()?.replace("\"", ""))
        Assert.assertEquals("JWT", headerMap["typ"]?.toString()?.replace("\"", ""))

        // Verify payload
        val payloadJson = String(Base64.decode(jwtParts[1], Base64.URL_SAFE))
        val payloadMap = Json.Default.parseToJsonElement(payloadJson).jsonObject
        Assert.assertEquals("\"testUser\"", payloadMap["sub"].toString())
        Assert.assertEquals("\"testIssuer\"", payloadMap["iss"].toString())
        Assert.assertEquals("123", payloadMap["intValue"].toString())
        Assert.assertEquals("123.45", payloadMap["doubleValue"].toString())
        Assert.assertEquals("true", payloadMap["boolValue"].toString())

        // Verify signature exists
        Assert.assertNotNull(jwtParts[2])
        Assert.assertTrue("Signature should not be empty", jwtParts[2].isNotEmpty())
    }

    @Test
    fun `test isValidJwt with valid JWT returns true`() {
        // Generate a valid JWT first
        val claims = mapOf("test" to "value", "required1" to "value1", "required2" to "value2")
        val jwt = JwtUtils.generateJwt(TEST_SECRET, claims)

        // Test with no required fields
        Assert.assertTrue(JwtUtils.isValidJwt(jwt))

        // Test with required fields that exist
        Assert.assertTrue(JwtUtils.isValidJwt(jwt, listOf("required1", "required2")))
    }

    @Test
    fun `test isValidJwt with invalid JWT returns false`() {
        // Test with an invalid JWT
        Assert.assertFalse(JwtUtils.isValidJwt("invalid.jwt.format"))

        // Test with a JWT missing required fields
        val claims = mapOf("test" to "value")
        val jwt = JwtUtils.generateJwt(TEST_SECRET, claims)
        Assert.assertFalse(JwtUtils.isValidJwt(jwt, listOf("missing")))
    }

    @Test
    fun `test parseJwtClaims extracts correct values with correct types`() {
        // Generate a JWT with various data types
        val originalClaims = mapOf<String, Any>(
            "stringValue" to "test",
            "intValue" to 123,
            "doubleValue" to 123.45,
            "boolValue" to true
        )

        val jwt = JwtUtils.generateJwt(TEST_SECRET, originalClaims)

        // Parse the JWT
        val parsedClaims = JwtUtils.parseJwtClaims(jwt)

        // Verify the parsed values
        Assert.assertEquals("test", parsedClaims["stringValue"])
        Assert.assertEquals(123, parsedClaims["intValue"])
        Assert.assertTrue(parsedClaims.containsKey("doubleValue"))
        Assert.assertTrue(parsedClaims["doubleValue"].toString().startsWith("123.4"))
        Assert.assertEquals(true, parsedClaims["boolValue"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parseJwtClaims throws exception for invalid JWT format`() {
        JwtUtils.parseJwtClaims("invalid.jwt")
    }

    @Test
    fun `test generateJwt creates expected Jwt output`() {
        val base64Secret = "2afd55692b492e60df7e9c0b4f55b0492afd55692b492e60df7e9c0b4f55b049"
        val claims = mapOf(
            "deviceId" to "test-device-token",
            "deviceName" to "Test Android Device",
            "deviceType" to "android",
            "communicationType" to "gcm",
        )

        val jwt = JwtUtils.generateJwt(base64Secret, claims)

        Assert.assertNotNull(jwt)
        val jwtParts = jwt.split(".")
        Assert.assertEquals("JWT should have three parts", 3, jwtParts.size)

        val expectedJwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJkZXZpY2VUeXBlIjoiYW5kcm9pZCIsImNvbW11bmljYXRpb25UeXBlIjoiZ2NtIiwiZGV2aWNlSWQiOiJ0ZXN0LWRldmljZS10b2tlbiIsImRldmljZU5hbWUiOiJUZXN0IEFuZHJvaWQgRGV2aWNlIn0.2u9onRjH6qg4-t08J19xkBqqpGdizzGdnJTMGUMb8uI"
        Assert.assertEquals(expectedJwt, jwt)
    }
}