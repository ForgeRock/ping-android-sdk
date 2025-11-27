/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback.recaptchaEnterprise

import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaException
import com.pingidentity.journey.BaseJourneyTest
import com.pingidentity.journey.IntegrationTestConfig
import com.pingidentity.journey.Journey
import com.pingidentity.journey.callback.ChoiceCallback
import com.pingidentity.journey.callback.TextOutputCallback
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.recaptcha.enterprise.ReCaptchaEnterpriseCallback
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for ReCaptcha Enterprise callback verification flows.
 *
 * Tests cover success scenarios (default/custom actions, custom payloads) and
 * failure scenarios (invalid config, score failures, client errors).
 */
//@Ignore("Skipped for now - see SDKS-4583.")
class RecaptchaEnterpriseCallbackTest : BaseJourneyTest() {
    private lateinit var recaptchaJourney: Journey

    /**
     * Sets up the journey tree configuration before each test.
     */
    @Before
    fun setupTree() {
        setup()
        tree = "TEST-e2e-recaptcha-enterprise"
        recaptchaJourney = Journey {
            logger = Logger.STANDARD
            serverUrl = "https://openam-sdks2.forgeblocks.com/am"
            realm = REALM
            cookie = COOKIE
        }
    }

    @After
    fun tearDown() {
        cleanup()
    }

    /**
     * Tests successful token verification with default LOGIN action.
     *
     * Verifies site key, token generation, payload structure, and server response
     * including risk score and token properties.
     */
    @Test
    fun testRecaptchaEnterpriseSuccess() = runTest {
        var node = recaptchaJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("success")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseSuccess = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseSuccess.reCaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseSuccess.reCaptchaSiteKey
        )
        var tokenFromCallbackResult = ""

        recaptchaEnterpriseSuccess.verify{}.onSuccess { token ->
            assertTrue(token.isNotEmpty())
            tokenFromCallbackResult = token
        }.onFailure {
            assertTrue("Failure in verifying ${it.message}. ReCaptcha site key: ${recaptchaEnterpriseSuccess.reCaptchaSiteKey}",false) // Should not reach here
        }

        val inputPayload = recaptchaEnterpriseSuccess.payload()["input"]
        assertNotNull(inputPayload)
        inputPayload?.jsonArray?.forEach {
            when (it.jsonObject["name"]?.toString()?.replace("\"", "")) {
                "IDToken1token" -> {
                    val tokenFromPayload = it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
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
        val token = outputMessageJson.jsonObject["event"]?.jsonObject["token"]?.jsonPrimitive.toString()
        val siteKey = outputMessageJson.jsonObject["event"]?.jsonObject["siteKey"]?.jsonPrimitive.toString()
        val userAgent = outputMessageJson.jsonObject["event"]?.jsonObject["userAgent"]?.jsonPrimitive.toString()
        val userIpAddress = outputMessageJson.jsonObject["event"]?.jsonObject["userIpAddress"]?.jsonPrimitive.toString()
        val score = outputMessageJson.jsonObject["riskAnalysis"]?.jsonObject["score"]?.jsonPrimitive.toString()
        val valid = outputMessageJson.jsonObject["tokenProperties"]?.jsonObject["valid"]
        val action = outputMessageJson.jsonObject["tokenProperties"]?.jsonObject["action"]?.jsonPrimitive.toString()
        val androidPackageName = outputMessageJson.jsonObject["tokenProperties"]?.jsonObject["androidPackageName"]

        assertTrue(name.toString().replace("\"", "").startsWith("projects/"))
        assertTrue(token.isNotEmpty())
        assertEquals(IntegrationTestConfig.recaptchaSiteKey, siteKey.replace("\"", ""))
        assertEquals("ktor-client", userAgent.replace("\"", ""))
        assertTrue(userIpAddress.isNotEmpty())
        assertTrue(score.toFloat() in 0.0f..1.0f)
        assertEquals(RecaptchaAction.LOGIN.action, action.replace("\"", ""))
        assertEquals("com.pingidentity.journey.test", androidPackageName.toString().replace("\"", ""))
        assertEquals(true, valid?.toString()?.toBoolean())
    }

    /**
     * Tests verification with custom action parameter.
     *
     * Verifies custom action is correctly mapped in payload and server response.
     */
    @Test
    fun testRecaptchaEnterpriseCustomAction() = runTest {
        var node = recaptchaJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("custom_action")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseSuccess = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseSuccess.reCaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseSuccess.reCaptchaSiteKey
        )
        var tokenFromCallbackResult = ""

        recaptchaEnterpriseSuccess.verify{
            recaptchaAction = RecaptchaAction.custom("custom_action")
        }.onSuccess { token ->
            assertTrue(token.isNotEmpty())
            tokenFromCallbackResult = token
        }.onFailure {
            assertTrue("Failure in verifying ${it.message}. ReCaptcha site key: ${recaptchaEnterpriseSuccess.reCaptchaSiteKey}", false) // Should not reach here
        }
        val inputPayload = recaptchaEnterpriseSuccess.payload()["input"]
        assertNotNull(inputPayload)
        inputPayload?.jsonArray?.forEach {
            when (it.jsonObject["name"]?.toString()?.replace("\"", "")) {
                "IDToken1token" -> {
                    val tokenFromPayload = it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
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

        val action = outputMessageJson.jsonObject["tokenProperties"]?.jsonObject["action"]?.jsonPrimitive.toString()
        assertEquals("custom_action", action.replace("\"", ""))
    }

    /**
     * Tests verification with custom transaction payload.
     *
     * Verifies custom payload (transaction data, billing info) is correctly
     * serialized and processed by the server.
     */
    @Test
    fun testRecaptchaEnterpriseCustomPayload() = runTest {
        var node = recaptchaJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("custom_payload")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseSuccess = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseSuccess.reCaptchaSiteKey.isNotEmpty())
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

        recaptchaEnterpriseSuccess.verify{
            customPayload = customPayloadJson
        }.onSuccess { token ->
            assertTrue(token.isNotEmpty())
            tokenFromCallbackResult = token
        }.onFailure {
            assertTrue("Failure in verifying ${it.message}. ReCaptcha site key: ${recaptchaEnterpriseSuccess.reCaptchaSiteKey}", false) // Should not reach here
        }

        val inputPayload = recaptchaEnterpriseSuccess.payload()["input"]
        assertNotNull(inputPayload)
        inputPayload?.jsonArray?.forEach {
            when (it.jsonObject["name"]?.toString()?.replace("\"", "")) {
                "IDToken1token" -> {
                    val tokenFromPayload = it.jsonObject["value"]?.toString()?.replace("\"", "") ?: ""
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
                    val payload = it.jsonObject["value"]?.toString()?.replace("\"", "")?.replace("\\", "") ?: ""
                    val customPayloadJsonString = customPayloadJson.toString().replace("\"", "").replace("\\", "")
                    assertEquals(payload, customPayloadJsonString)
                }
            }
        }

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        val outputMessageJson = Json.parseToJsonElement(textOutputCallback.message)
        assertNotNull(outputMessageJson)

        val transactionId = outputMessageJson.jsonObject["event"]?.jsonObject["transactionData"]?.jsonObject["transactionId"]?.jsonPrimitive
        val paymentMethod = outputMessageJson.jsonObject["event"]?.jsonObject["transactionData"]?.jsonObject["paymentMethod"]?.jsonPrimitive
        val cardBin = outputMessageJson.jsonObject["event"]?.jsonObject["transactionData"]?.jsonObject["cardBin"]?.jsonPrimitive
        val cardLastFour = outputMessageJson.jsonObject["event"]?.jsonObject["transactionData"]?.jsonObject["cardLastFour"]?.jsonPrimitive
        val accountId = outputMessageJson.jsonObject["event"]?.jsonObject["userInfo"]?.jsonObject["accountId"]?.jsonPrimitive

        assertEquals("custom-payload-1234567890", transactionId.toString().replace("\"", ""))
        assertEquals("credit-card", paymentMethod.toString().replace("\"", ""))
        assertEquals("1111", cardBin.toString().replace("\"", ""))
        assertEquals("1234", cardLastFour.toString().replace("\"", ""))
        assertEquals("user_account_id_123", accountId.toString().replace("\"", ""))
    }

    /**
     * Tests score-based failure handling.
     *
     * Verifies token generation succeeds but server may return validation error
     * or low risk score.
     */
    @Test
    fun testRecaptchaEnterpriseScoreFailure() = runTest {
        var node = recaptchaJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("score_failure")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseFailure = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseFailure.reCaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseFailure.reCaptchaSiteKey
        )

        recaptchaEnterpriseFailure.verify{
            recaptchaAction = RecaptchaAction.custom("score_failure")
        }.onSuccess { token ->
            assertTrue(token.isNotEmpty())
        }.onFailure {
            assertTrue("Failure in verifying ${it.message}. ReCaptcha site key: ${recaptchaEnterpriseFailure.reCaptchaSiteKey}", false) // Should not reach here
        }

        val inputPayload = recaptchaEnterpriseFailure.payload()["input"]
        assertNotNull(inputPayload)

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        val messageResponse = textOutputCallback.message
        if (!messageResponse.contains("VALIDATION_ERROR")) {
            val outputMessageJson = Json.parseToJsonElement(textOutputCallback.message)
            assertNotNull(outputMessageJson)
            val score = outputMessageJson.jsonObject["riskAnalysis"]?.jsonObject["score"]?.jsonPrimitive.toString()
            assertEquals(score.toFloat(),1.0f)
        } else {
            assertTrue(messageResponse.contains("VALIDATION_ERROR"))
        }
    }

    /**
     * Tests handling of invalid Google Cloud project ID.
     *
     * Verifies API_ERROR is returned when project is disabled or missing.
     */
    @Test
    fun testRecaptchaEnterpriseCallbackInvalidProjectId() = runTest {
        var node = recaptchaJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("invalid_project_id")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseFailure = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseFailure.reCaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseFailure.reCaptchaSiteKey
        )

        recaptchaEnterpriseFailure.verify{
            recaptchaAction = RecaptchaAction.custom("invalid_project_id")
        }.onSuccess { token ->
            assertTrue(token.isNotEmpty())
        }.onFailure {
            assertTrue("Failure in verifying ${it.message}. ReCaptcha site key: ${recaptchaEnterpriseFailure.reCaptchaSiteKey}", false) // Should not reach here
        }

        val inputPayload = recaptchaEnterpriseFailure.payload()["input"]
        assertNotNull(inputPayload)

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        val messageResponse = textOutputCallback.message
        assertTrue(messageResponse.contains("API_ERROR"))
        assertTrue(messageResponse.contains("reCAPTCHA Enterprise API has not been used in project invalid before or it is disabled"))
    }

    /**
     * Tests handling of invalid verification endpoint URL.
     *
     * Verifies API_ERROR or UNKNOWN error is returned for misconfigured endpoints.
     */
    @Test
    fun testRecaptchaEnterpriseInvalidVerificationUrl() = runTest {
        var node = recaptchaJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("invalid_verification_url")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseFailure = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseFailure.reCaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseFailure.reCaptchaSiteKey
        )

        recaptchaEnterpriseFailure.verify{
            recaptchaAction = RecaptchaAction.custom("invalid_verification_url")
        }.onSuccess { token ->
            assertTrue(token.isNotEmpty())
        }.onFailure {
            assertTrue("Failure in verifying ${it.message}. ReCaptcha site key: ${IntegrationTestConfig.recaptchaSiteKey}", false) // Should not reach here
        }

        val inputPayload = recaptchaEnterpriseFailure.payload()["input"]
        assertNotNull(inputPayload)

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        val message = textOutputCallback.message
        assertTrue(message.contains("API_ERROR") || message.contains("UNKNOWN"))
    }

    /**
     * Tests handling of invalid server-side secret key.
     *
     * Verifies INVALID_SECRET_KEY error is returned when secret key cannot be retrieved.
     */
    @Test
    fun testRecaptchaEnterpriseInvalidSecretKey() = runTest {
        var node = recaptchaJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("invalid_secret_key")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseFailure = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseFailure.reCaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseFailure.reCaptchaSiteKey
        )

        recaptchaEnterpriseFailure.verify{
            recaptchaAction = RecaptchaAction.custom("invalid_secret_key")
        }.onSuccess { token ->
            assertTrue(token.isNotEmpty())
        }.onFailure {
            assertTrue("Failure in verifying ${it.message}. ReCaptcha site key: ${IntegrationTestConfig.recaptchaSiteKey}", false) // Should not reach here
        }

        val inputPayload = recaptchaEnterpriseFailure.payload()["input"]
        assertNotNull(inputPayload)

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        val message = textOutputCallback.message
        assertTrue(message.contains("INVALID_SECRET_KEY:Secret key could not be retrieved"))
    }

    /**
     * Tests custom client error propagation to server.
     *
     * Verifies custom error messages are correctly sent in the response.
     */
    @Test
    fun testRecaptchaEnterpriseCustomClientError() = runTest {
        var node = recaptchaJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        // This returns a failure response from server with CUSTOM_CLIENT_ERROR
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("invalid_site_key")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseFailure = node.callbacks.first() as ReCaptchaEnterpriseCallback

        recaptchaEnterpriseFailure.verify {
            recaptchaAction = RecaptchaAction.custom("invalid_site_key")
            customError = { "CUSTOM_CLIENT_ERROR" }
        }

        val inputPayload = recaptchaEnterpriseFailure.payload()["input"]
        assertNotNull(inputPayload)

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        val message = textOutputCallback.message
        assertTrue(message.contains("CUSTOM_CLIENT_ERROR"))
    }

    /**
     * Tests handling of invalid site key.
     *
     * Verifies RecaptchaException is thrown client-side and error is propagated
     * to server response.
     */
    @Test
    fun testRecaptchaEnterpriseInvalidSiteKey() = runTest {
        var node = recaptchaJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("invalid_site_key")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseFailure = node.callbacks.first() as ReCaptchaEnterpriseCallback

        recaptchaEnterpriseFailure.verify{
            recaptchaAction = RecaptchaAction.custom("invalid_site_key")
        }.onSuccess { token ->
            assertTrue(token.isEmpty())
        }.onFailure { throwable ->
            assertTrue(throwable is RecaptchaException)
        }

        val inputPayload = recaptchaEnterpriseFailure.payload()["input"]
        assertNotNull(inputPayload)

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        val message = textOutputCallback.message
        assertEquals("CLIENT_ERROR:Site key invalid", message)
    }
}