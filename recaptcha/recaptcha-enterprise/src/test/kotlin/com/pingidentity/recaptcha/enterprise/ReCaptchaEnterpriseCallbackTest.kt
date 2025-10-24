/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recaptcha.enterprise

import android.app.Application
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient
import com.pingidentity.android.ContextProvider
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.*

/**
 * Unit tests for [ReCaptchaEnterpriseCallback].
 *
 * This class verifies the correct initialization, configuration, and execution
 * of the ReCaptcha verification process.
 */
class ReCaptchaEnterpriseCallbackTest {

    private val mockApplication = mockk<Application>()
    private val mockRecaptchaClient = mockk<RecaptchaClient>()
    private val siteKey = "test-site-key"

    @BeforeTest
    fun setup() {
        // Mock Android context and ReCaptcha static methods
        every { mockApplication.applicationContext } returns mockApplication
        ContextProvider.init(mockApplication)
        mockkObject(Recaptcha)
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
        unmockkObject(Recaptcha)
    }

    /**
     * Test that the callback correctly sets the site key when initialized.
     */
    @Test
    fun `init sets reCaptchaSiteKey when property name matches`() {
        val callback = createCallbackWithFullInputFields()
        assertEquals(siteKey, callback.reCaptchaSiteKey)
    }

    /**
     * Test default values of ReCaptchaEnterpriseConfig.
     */
    @Test
    fun `ReCaptchaEnterpriseConfig has correct defaults`() {
        val config = ReCaptchaEnterpriseConfig()
        assertEquals(RecaptchaAction.LOGIN, config.recaptchaAction)
        assertEquals(10000L, config.timeoutInMills)
    }


    /**
     * Test that verifies the callback result includes all required input fields:
     * - IDToken1token: The ReCaptcha verification token
     * - IDToken1action: The action type (e.g., "login")
     * - IDToken1clientError: Client error field (empty on success)
     * - IDToken1payload: Custom payload as JSON string
     */
    @Test
    fun `Test verify result with complete input fields including token, action, clientError, and payload`() = runTest {
        // Mock a realistic ReCaptcha token
        val expectedToken = "0cAFcWeA53BjeGBfuZXsD008heJeYAYR6d0scOUyGZ2C0BTkJJCn0GoeZPcl_0fngK8A35nexoO-YtTFBNIcNVIMUAaqBjCA28VtQtqjzbvAQ6_As7for3aClmSj7Zw9Wbe9nmAZgDNuAWvHqnUhng4wCChMVF9ESs6FCU9oG2XIP1ZTGO7tS1mOEX7kg5PjgPXi6"

        coEvery { Recaptcha.fetchClient(mockApplication, siteKey) } returns mockRecaptchaClient
        coEvery { mockRecaptchaClient.execute(any(), any()) } returns Result.success(expectedToken)

        // Create callback with all four input fields
        val callback = createCallbackWithFullInputFields()

        // Create custom payload
        val customPayload = buildJsonObject {
            put("userId", "user-12345")
            put("deviceId", "device-67890")
            put("loginAttempts", 1)
        }

        // Execute verification with LOGIN action
        val result = callback.verify {
            recaptchaAction = RecaptchaAction.LOGIN
            timeoutInMills = 15000L
            this.customPayload = customPayload
        }

        // Verify the result is successful
        assertTrue(result.isSuccess)
        assertEquals(expectedToken, result.getOrNull())

        // Verify all input fields are populated correctly
        val payload = callback.payload()
        val inputArray = payload["input"]?.jsonArray

        assertNotNull(inputArray, "Input array should not be null")
        assertEquals(4, inputArray.size, "Should have exactly 4 input fields")

        // Verify IDToken1token field
        val tokenField = inputArray[0].jsonObject
        assertEquals("IDToken1token", tokenField["name"]?.jsonPrimitive?.content)
        assertEquals(expectedToken, tokenField["value"]?.jsonPrimitive?.content)

        // Verify IDToken1action field
        val actionField = inputArray[1].jsonObject
        assertEquals("IDToken1action", actionField["name"]?.jsonPrimitive?.content)
        assertEquals("login", actionField["value"]?.jsonPrimitive?.content)

        // Verify IDToken1clientError field (empty on success)
        val clientErrorField = inputArray[2].jsonObject
        assertEquals("IDToken1clientError", clientErrorField["name"]?.jsonPrimitive?.content)
        assertEquals("", clientErrorField["value"]?.jsonPrimitive?.content)

        // Verify IDToken1payload field
        val payloadField = inputArray[3].jsonObject
        assertEquals("IDToken1payload", payloadField["name"]?.jsonPrimitive?.content)

        // The payload value should be a JSON string representation of customPayload
        val payloadValue = payloadField["value"]?.jsonPrimitive?.content
        assertNotNull(payloadValue, "Payload value should not be null")
        assertTrue(payloadValue.contains("userId"), "Payload should contain userId")
        assertTrue(payloadValue.contains("deviceId"), "Payload should contain deviceId")
        assertTrue(payloadValue.contains("loginAttempts"), "Payload should contain loginAttempts")

        // Verify the order of execution
        coVerify(ordering = Ordering.ORDERED) {
            Recaptcha.fetchClient(mockApplication, siteKey)
            mockRecaptchaClient.execute(RecaptchaAction.LOGIN, 15000L)
        }
    }

    /**
     * Test that verifies the callback handles failure scenario with error wrapping.
     */
    @Test
    fun `Test verify result with error wrapped as UNKNOWN_ERROR on failure`() = runTest {
        val testException = Exception("Network timeout occurred")

        coEvery { Recaptcha.fetchClient(mockApplication, siteKey) } returns mockRecaptchaClient
        coEvery { mockRecaptchaClient.execute(any(), any()) } returns Result.failure(testException)

        // Create callback with all four input fields
        val callback = createCallbackWithFullInputFields()

        // Execute verification
        val result = callback.verify {
            recaptchaAction = RecaptchaAction.SIGNUP
            timeoutInMills = 10000L
        }

        // Verify the result is failure
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())

        val exception = result.exceptionOrNull()
        assertEquals("Network timeout occurred", exception?.message)

        // Verify execution order
        coVerify(ordering = Ordering.ORDERED) {
            Recaptcha.fetchClient(mockApplication, siteKey)
            mockRecaptchaClient.execute(RecaptchaAction.SIGNUP, 10000L)
        }

        val payload = callback.payload()
        val inputArray = payload["input"]?.jsonArray
        assertNotNull(inputArray)
        val clientErrorField = inputArray[2].jsonObject
        assertEquals("IDToken1clientError", clientErrorField["name"]?.jsonPrimitive?.content)
        assertEquals("Network timeout occurred", clientErrorField["value"]?.jsonPrimitive?.content)
    }

    /**
     * Test with realistic long token and different action types.
     */
    @Test
    fun `Test verify with realistic long token and SIGNUP action`() = runTest {
        // Use a realistic long ReCaptcha token similar to production (truncated for test)
        val longToken = "0cAFcWeA53BjeGBfuZXsD008heJeYAYR6d0scOUyGZ2C0BTkJJCn0GoeZPcl_0fngK8A35nexoO-YtTFBNIcNVIMUAaqBjCA28VtQtqjzbvAQ6_As7for3aClmSj7Zw9Wbe9nmAZgDNuAWvHqnUhng4wCChMVF9ESs6FCU9oG2XIP1ZTGO7tS1mOEX7kg5PjgPXi6-XXX-efURhwi77MPp9p9g4bj1UpGwSafOvZvT5WpwAXvbag6UsgTwHmkBNDF1HYQzSgMRQfltjemcHKktNgNxWOls4NE3Xd6y8WWol6tsAHI8wG6M91flCsfEw-H7MYAOiDjQsUEmvbTmKxMOhecT5L4mTNSAWUKmAlnyiHxIF_3XJhH8xskJTGaE2lHtE2hydabV6KDxHB3u0kXzpUmPLJNX1Nl24czAYl1Fw4Er0"

        coEvery { Recaptcha.fetchClient(mockApplication, siteKey) } returns mockRecaptchaClient
        coEvery { mockRecaptchaClient.execute(any(), any()) } returns Result.success(longToken)

        val callback = createCallbackWithFullInputFields()

        val result = callback.verify {
            recaptchaAction = RecaptchaAction.SIGNUP
        }

        assertTrue(result.isSuccess)
        assertEquals(longToken, result.getOrNull())

        val payload = callback.payload()
        val inputArray = payload["input"]?.jsonArray

        assertNotNull(inputArray)
        assertEquals(4, inputArray.size, "Should have 4 input fields")

        // Verify token field contains the long token
        val tokenField = inputArray[0].jsonObject
        assertEquals("IDToken1token", tokenField["name"]?.jsonPrimitive?.content)
        assertEquals(longToken, tokenField["value"]?.jsonPrimitive?.content)
        assertTrue((tokenField["value"]?.jsonPrimitive?.content?.length ?: 0) > 100,
            "Token should be a realistic token")

        // Verify action field exists
        val actionField = inputArray[1].jsonObject
        assertEquals("IDToken1action", actionField["name"]?.jsonPrimitive?.content)
        assertNotNull(actionField["value"]?.jsonPrimitive?.content)
        assertEquals(RecaptchaAction.SIGNUP.action, actionField["value"]?.jsonPrimitive?.content)

        // Verify clientError field exists
        val clientErrorField = inputArray[2].jsonObject
        assertEquals("IDToken1clientError", clientErrorField["name"]?.jsonPrimitive?.content)

        // Verify payload field exists
        val payloadField = inputArray[3].jsonObject
        assertEquals("IDToken1payload", payloadField["name"]?.jsonPrimitive?.content)
    }

    /**
     * Test that verifies the callback handles exceptions during client setup.
     */
    @Test
    fun `Test verify handles exception during ReCaptcha client fetch`() = runTest {
        // Mock Recaptcha.fetchClient to throw an exception
        val testException = IllegalStateException("Failed to initialize ReCaptcha client")
        coEvery { Recaptcha.fetchClient(any(), any()) } throws testException

        val callback = createCallbackWithFullInputFields()

        // Execute verification
        val result = callback.verify {
            recaptchaAction = RecaptchaAction.LOGIN
            timeoutInMills = 10000L
        }

        // Verify the result is failure
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())

        val exception = result.exceptionOrNull()
        assertEquals(testException, exception)
        assertEquals("Failed to initialize ReCaptcha client", exception?.message)

        // Verify that the callback input fields are populated correctly
        val payload = callback.payload()
        val inputArray = payload["input"]?.jsonArray
        assertNotNull(inputArray)

        // Token should be empty
        val tokenField = inputArray[0].jsonObject
        assertEquals("IDToken1token", tokenField["name"]?.jsonPrimitive?.content)
        assertEquals("", tokenField["value"]?.jsonPrimitive?.content)

        // Action should be set
        val actionField = inputArray[1].jsonObject
        assertEquals("IDToken1action", actionField["name"]?.jsonPrimitive?.content)
        assertEquals("login", actionField["value"]?.jsonPrimitive?.content)

        // ClientError should contain the exception message
        val clientErrorField = inputArray[2].jsonObject
        assertEquals("IDToken1clientError", clientErrorField["name"]?.jsonPrimitive?.content)
        assertEquals("Failed to initialize ReCaptcha client", clientErrorField["value"]?.jsonPrimitive?.content)

        // Payload should be empty
        val payloadField = inputArray[3].jsonObject
        assertEquals("IDToken1payload", payloadField["name"]?.jsonPrimitive?.content)
        assertEquals("", payloadField["value"]?.jsonPrimitive?.content)

        // Verify fetchClient was called
        coVerify { Recaptcha.fetchClient(mockApplication, siteKey) }
    }

    /**
     * Test that verifies the callback handles exceptions with null message using UNKNOWN_ERROR.
     */
    @Test
    fun `Test verify uses UNKNOWN_ERROR when exception message is null`() = runTest {
        // Create an exception with null message
        val testException = RuntimeException(null as String?)
        coEvery { Recaptcha.fetchClient(any(), any()) } throws testException

        val callback = createCallbackWithFullInputFields()

        val result = callback.verify {
            recaptchaAction = RecaptchaAction.SIGNUP
        }

        assertTrue(result.isFailure)

        // Verify the clientError field contains UNKNOWN_ERROR
        val payload = callback.payload()
        val inputArray = payload["input"]?.jsonArray
        assertNotNull(inputArray)

        val clientErrorField = inputArray[2].jsonObject
        assertEquals("IDToken1clientError", clientErrorField["name"]?.jsonPrimitive?.content)
        assertEquals("UNKNOWN_ERROR", clientErrorField["value"]?.jsonPrimitive?.content)
    }

    /**
     * Helper function to create a callback with all four input fields:
     * IDToken1token, IDToken1action, IDToken1clientError, and IDToken1payload.
     */
    private fun createCallbackWithFullInputFields(): ReCaptchaEnterpriseCallback {
        val callback = ReCaptchaEnterpriseCallback()

        val mockJson = buildJsonObject {
            put("type", "ReCaptchaEnterpriseCallback")
            put("output", buildJsonArray {
                add(buildJsonObject {
                    put("name", "recaptchaSiteKey")
                    put("value", siteKey)
                })
            })
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("name", "IDToken1token")
                    put("value", "")
                })
                add(buildJsonObject {
                    put("name", "IDToken1action")
                    put("value", "")
                })
                add(buildJsonObject {
                    put("name", "IDToken1clientError")
                    put("value", "")
                })
                add(buildJsonObject {
                    put("name", "IDToken1payload")
                    put("value", "{}")
                })
            })
        }

        callback.init(mockJson)
        return callback
    }
}