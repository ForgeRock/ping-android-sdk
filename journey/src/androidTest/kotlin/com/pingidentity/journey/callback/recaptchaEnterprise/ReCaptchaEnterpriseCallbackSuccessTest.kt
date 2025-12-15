/*
 * Copyright (c) 2025 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback.recaptchaEnterprise

import androidx.test.filters.LargeTest
import com.google.android.recaptcha.RecaptchaAction
import com.pingidentity.journey.BaseJourneyTest
import com.pingidentity.journey.IntegrationTestConfig
import com.pingidentity.journey.callback.ChoiceCallback
import com.pingidentity.journey.callback.TextOutputCallback
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.journey.user
import com.pingidentity.journey.utils.DeviceSkipRule
import com.pingidentity.journey.utils.RequiresDevice
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.recaptcha.enterprise.ReCaptchaEnterpriseCallback
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Integration tests for ReCaptcha Enterprise callback verification flows.
 *
 * Tests cover success scenarios (default/custom actions, custom payloads)
 */
@LargeTest
class ReCaptchaEnterpriseCallbackSuccessTest : BaseJourneyTest() {
    /**
     * Rule to skip tests on emulator.
     */
    @get:Rule
    val deviceSkipRule = DeviceSkipRule()

    /**
     * Sets up the journey tree configuration before each test.
     */
    @Before
    fun setupTree() {
        tree = "TEST-e2e-recaptcha-enterprise"
    }

    @Test
    @RequiresDevice
    fun testReCaptchaTestSuccessScenarios() = runTest {
        defaultJourney.user()?.logout()
        testRecaptchaEnterpriseSuccess()

        defaultJourney.user()?.logout()
        testRecaptchaEnterpriseCustomAction()

        defaultJourney.user()?.logout()
        testRecaptchaEnterpriseCustomPayload()
    }

    /**
     * Tests successful token verification with default LOGIN action.
     *
     * Verifies site key, token generation, payload structure, and server response
     * including risk score and token properties.
     */
    private fun testRecaptchaEnterpriseSuccess() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("success")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseSuccess = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseSuccess.reCaptchaSiteKey.isNotEmpty())
        assertTrue(IntegrationTestConfig.recaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseSuccess.reCaptchaSiteKey
        )
        var tokenFromCallbackResult = ""

        recaptchaEnterpriseSuccess.verify {}.onSuccess { token ->
            assertTrue(token.isNotEmpty())
            tokenFromCallbackResult = token
        }.onFailure {
            fail("Failure in verifying ${it.message}.")
        }

        val inputPayload = recaptchaEnterpriseSuccess.payload()["input"]
        assertNotNull(inputPayload)
        inputPayload?.jsonArray?.forEach {
            when (it.jsonObject["name"]?.toString()?.replace("\"", "")) {
                "IDToken1token" -> {
                    val tokenFromPayload =
                        it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
                    assertEquals(tokenFromCallbackResult, tokenFromPayload)
                }

                "IDToken1action" -> {
                    val action = it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
                    assertEquals(action, RecaptchaAction.LOGIN.action)
                }

                "IDToken1clientError" -> {
                    val clientError = it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
                    assertTrue(clientError.isEmpty())
                }

                "IDToken1payload" -> {
                    val payload = it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
                    assertEquals("{}", payload)
                }
            }
        }

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        val outputMessageJson = Json.parseToJsonElement(textOutputCallback.message)
        assertNotNull(outputMessageJson)

        val name = outputMessageJson.jsonObject["name"]?.jsonPrimitive
        val token =
            outputMessageJson.jsonObject["event"]?.jsonObject?.get("token")?.jsonPrimitive.toString()
        val siteKey =
            outputMessageJson.jsonObject["event"]?.jsonObject?.get("siteKey")?.jsonPrimitive.toString()
        val userAgent =
            outputMessageJson.jsonObject["event"]?.jsonObject?.get("userAgent")?.jsonPrimitive.toString()
        val userIpAddress =
            outputMessageJson.jsonObject["event"]?.jsonObject?.get("userIpAddress")?.jsonPrimitive.toString()
        val score =
            outputMessageJson.jsonObject["riskAnalysis"]?.jsonObject?.get("score")?.jsonPrimitive.toString()
        val valid = outputMessageJson.jsonObject["tokenProperties"]?.jsonObject?.get("valid")
        val action =
            outputMessageJson.jsonObject["tokenProperties"]?.jsonObject?.get("action")?.jsonPrimitive.toString()
        val androidPackageName =
            outputMessageJson.jsonObject["tokenProperties"]?.jsonObject?.get("androidPackageName")

        assertTrue(name.toString().replace("\"", "").startsWith("projects/"))
        assertTrue(token.isNotEmpty())
        assertEquals(IntegrationTestConfig.recaptchaSiteKey, siteKey.replace("\"", ""))
        assertEquals("ktor-client", userAgent.replace("\"", ""))
        assertTrue(userIpAddress.isNotEmpty())
        assertTrue(score.toFloat() in 0.0f..1.0f)
        assertEquals(RecaptchaAction.LOGIN.action, action.replace("\"", ""))
        assertEquals(
            "com.pingidentity.journey.test",
            androidPackageName.toString().replace("\"", "")
        )
        assertEquals(true, valid?.toString()?.toBoolean())
    }

    /**
     * Tests verification with custom action parameter.
     *
     * Verifies custom action is correctly mapped in payload and server response.
     */
    private fun testRecaptchaEnterpriseCustomAction() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("custom_action")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseSuccess = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseSuccess.reCaptchaSiteKey.isNotEmpty())
        assertTrue(IntegrationTestConfig.recaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseSuccess.reCaptchaSiteKey
        )
        var tokenFromCallbackResult = ""

        recaptchaEnterpriseSuccess.verify {
            recaptchaAction = RecaptchaAction.custom("custom_action")
        }.onSuccess { token ->
            assertTrue(token.isNotEmpty())
            tokenFromCallbackResult = token
        }.onFailure {
            fail("Failure in verifying ${it.message}.")
        }
        val inputPayload = recaptchaEnterpriseSuccess.payload()["input"]
        assertNotNull(inputPayload)
        inputPayload?.jsonArray?.forEach {
            when (it.jsonObject["name"]?.toString()?.replace("\"", "")) {
                "IDToken1token" -> {
                    val tokenFromPayload =
                        it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
                    assertEquals(tokenFromCallbackResult, tokenFromPayload)
                }

                "IDToken1action" -> {
                    val action = it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
                    assertEquals(action, RecaptchaAction.custom("custom_action").action)
                }

                "IDToken1clientError" -> {
                    val clientError = it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
                    assertTrue(clientError.isEmpty())
                }

                "IDToken1payload" -> {
                    val payload = it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
                    assertEquals("{}", payload)
                }
            }
        }

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        val outputMessageJson = Json.parseToJsonElement(textOutputCallback.message)
        assertNotNull(outputMessageJson)

        val action =
            outputMessageJson.jsonObject["tokenProperties"]?.jsonObject?.get("action")?.jsonPrimitive.toString()
        assertEquals("custom_action", action.replace("\"", ""))
    }

    /**
     * Tests verification with custom transaction payload.
     *
     * Verifies custom payload (transaction data, billing info) is correctly
     * serialized and processed by the server.
     */
    private fun testRecaptchaEnterpriseCustomPayload() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("custom_payload")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseSuccess = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseSuccess.reCaptchaSiteKey.isNotEmpty())
        assertTrue(IntegrationTestConfig.recaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseSuccess.reCaptchaSiteKey
        )
        var tokenFromCallbackResult = ""
        val customPayloadJson = buildJsonObject {
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
                    put("address", Json.parseToJsonElement("""["3333 Random Road"]"""))
                    put("locality", "Courtenay")
                    put("administrative_area", "BC")
                    put("region_code", "CA")
                    put("postal_code", "V2V 2V2")
                })
            })
        }

        recaptchaEnterpriseSuccess.verify {
            customPayload = customPayloadJson
        }.onSuccess { token ->
            assertTrue(token.isNotEmpty())
            tokenFromCallbackResult = token
        }.onFailure {
            fail("Failure in verifying ${it.message}.")
        }

        val inputPayload = recaptchaEnterpriseSuccess.payload()["input"]
        assertNotNull(inputPayload)
        inputPayload?.jsonArray?.forEach {
            when (it.jsonObject["name"]?.toString()?.replace("\"", "")) {
                "IDToken1token" -> {
                    val tokenFromPayload =
                        it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
                    assertEquals(tokenFromCallbackResult, tokenFromPayload)
                }

                "IDToken1action" -> {
                    val action = it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
                    assertEquals(action, RecaptchaAction.LOGIN.action)
                }

                "IDToken1clientError" -> {
                    val clientError = it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
                    assertTrue(clientError.isEmpty())
                }

                "IDToken1payload" -> {
                    val payload =
                        it.jsonObject["value"]?.toString()?.replace("\"", "")?.replace("\\", "")
                            ?: ""
                    val customPayloadJsonString =
                        customPayloadJson.toString().replace("\"", "").replace("\\", "")
                    assertEquals(payload, customPayloadJsonString)
                }
            }
        }

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        val outputMessageJson = Json.parseToJsonElement(textOutputCallback.message)
        assertNotNull(outputMessageJson)

        val transactionId =
            outputMessageJson.jsonObject["event"]?.jsonObject?.get("transactionData")?.jsonObject?.get("transactionId")?.jsonPrimitive
        val paymentMethod =
            outputMessageJson.jsonObject["event"]?.jsonObject?.get("transactionData")?.jsonObject?.get("paymentMethod")?.jsonPrimitive
        val cardBin =
            outputMessageJson.jsonObject["event"]?.jsonObject?.get("transactionData")?.jsonObject?.get("cardBin")?.jsonPrimitive
        val cardLastFour =
            outputMessageJson.jsonObject["event"]?.jsonObject?.get("transactionData")?.jsonObject?.get("cardLastFour")?.jsonPrimitive
        val accountId =
            outputMessageJson.jsonObject["event"]?.jsonObject?.get("userInfo")?.jsonObject?.get("accountId")?.jsonPrimitive

        assertEquals("custom-payload-1234567890", transactionId.toString().replace("\"", ""))
        assertEquals("credit-card", paymentMethod.toString().replace("\"", ""))
        assertEquals("1111", cardBin.toString().replace("\"", ""))
        assertEquals("1234", cardLastFour.toString().replace("\"", ""))
        assertEquals("user_account_id_123", accountId.toString().replace("\"", ""))
    }
}