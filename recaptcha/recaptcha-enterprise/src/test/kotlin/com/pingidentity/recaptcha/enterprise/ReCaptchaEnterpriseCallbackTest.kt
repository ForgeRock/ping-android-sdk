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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
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
        val callback = createInitializedCallback()
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

    @Test
    fun `Test verify calls are mode in order when result is mocked to be success`() = runTest {
        val mockRecaptchaClient = mockk<RecaptchaClient>()
        coEvery { Recaptcha.fetchClient(mockApplication, siteKey) } returns mockRecaptchaClient
        coEvery { mockRecaptchaClient.execute(any(), any()) } returns Result.success("test-token")

        val callback = createInitializedCallback()
        callback.initForTest("recaptchaSiteKey", JsonPrimitive(siteKey))

        val result = callback.verify {
            recaptchaAction = RecaptchaAction.LOGIN
            timeoutInMills = 15000L
        }
        coVerify(ordering = Ordering.ORDERED) {
            Recaptcha.fetchClient(mockApplication, siteKey)
            mockRecaptchaClient.execute(RecaptchaAction.LOGIN, 15000L)
        }

        assertTrue(result.isSuccess)
        assertEquals("test-token", result.getOrNull())
        assertEquals(
            "test-token",
            callback.payload()["input"]?.jsonArray[0]?.jsonObject["value"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `Test verify calls are mode in order when result is mocked to be failure`() = runTest {
        val mockRecaptchaClient = mockk<RecaptchaClient>()
        val testException = Exception(ReCaptchaEnterpriseCallback.UNKNOWN_ERROR)
        coEvery { Recaptcha.fetchClient(mockApplication, siteKey) } returns mockRecaptchaClient
        coEvery { mockRecaptchaClient.execute(any(), any()) } returns Result.failure(testException)

        val callback = createInitializedCallback()

        val result = callback.verify {
            recaptchaAction = RecaptchaAction.LOGIN
            timeoutInMills = 15000L
        }
        coVerify(ordering = Ordering.ORDERED) {
            Recaptcha.fetchClient(mockApplication, siteKey)
            mockRecaptchaClient.execute(RecaptchaAction.LOGIN, 15000L)
        }

        assertTrue(result.isFailure)
        assertEquals(testException.message, result.exceptionOrNull()?.message)
    }

    @Test
    fun `Test verify call with custom payload`() = runTest {
        val mockRecaptchaClient = mockk<RecaptchaClient>()
        coEvery { Recaptcha.fetchClient(mockApplication, siteKey) } returns mockRecaptchaClient
        coEvery { mockRecaptchaClient.execute(any(), any()) } returns Result.success("test-token")

        val callback = createInitializedCallback(includeCustomPayload = true)

        val customPayload = buildJsonObject {
            buildJsonObject {
                put("firewallPolicyEvaluation", false)
                put("express", false)
                put("transaction_data", buildJsonObject {
                    put("transaction_id", "custom-payload-1234567890")
                    put("payment_method", "credit-card")
                    put("card_bin", "1111")
                    put("card_last_four", "1234")
                    put("currency_code", "CAD")
                    put("value", 12.34)
                    put("user", buildJsonObject {
                        put("email", "sdkuser@example.com")
                    })
                    put("billing_address", buildJsonObject {
                        put("recipient", "Sdk User")
                        put("address", buildJsonArray {
                            add("3333 Random Road")
                        })
                        put("locality", "Courtenay")
                        put("administrative_area", "BC")
                        put("region_code", "CA")
                        put("postal_code", "V2V 2V2")
                    })
                })
            }
        }

        val result = callback.verify {
            recaptchaAction = RecaptchaAction.SIGNUP
            timeoutInMills = 15000L
            this.customPayload = customPayload
        }
        coVerify(ordering = Ordering.ORDERED) {
            Recaptcha.fetchClient(mockApplication, siteKey)
            mockRecaptchaClient.execute(RecaptchaAction.SIGNUP, 15000L)
            val input = callback.input(result.getOrNull()!!, customPayload)
            println(input)
            assertNotNull(input)
        }

        assertTrue(result.isSuccess)
        assertEquals("test-token", result.getOrNull())
        assertEquals(
            "[{\"name\":\"recaptchaToken\",\"value\":\"test-token\"},{\"name\":\"customPayload\"}]",
            callback.payload()["input"].toString()
        )
    }

    /**
     * Helper function to create a properly initialized callback with mock JSON structure.
     * @param includeCustomPayload Whether to include the customPayload input field.
     * @return An initialized [ReCaptchaEnterpriseCallback] instance.
     */
    private fun createInitializedCallback(
        includeCustomPayload: Boolean = false,
    ): ReCaptchaEnterpriseCallback {
        val callback = ReCaptchaEnterpriseCallback()

        // Initialize the callback with a mock JSON structure
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
                    put("name", "recaptchaToken")
                    put("value", "")
                })
                if (includeCustomPayload) {
                    add(buildJsonObject {
                        put("name", "customPayload")
                        put("value", "")
                    })
                }
            })
        }

        callback.init(mockJson)
        return callback
    }
}