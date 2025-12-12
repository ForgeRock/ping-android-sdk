/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback.recaptchaEnterprise

import androidx.test.filters.LargeTest
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaException
import com.pingidentity.journey.BaseJourneyTest
import com.pingidentity.journey.IntegrationTestConfig
import com.pingidentity.journey.callback.ChoiceCallback
import com.pingidentity.journey.callback.TextOutputCallback
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.journey.utils.DeviceSkipRule
import com.pingidentity.journey.utils.RequiresDevice
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.recaptcha.enterprise.ReCaptchaEnterpriseCallback
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runners.MethodSorters
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests for the ReCaptchaEnterpriseCallback. The tests running in order prevents failure on the
 * BrowserStack.
 */
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReCaptchaEnterpriseCallbackFailureTest : BaseJourneyTest() {
    /**
     * Rule to skip tests on emulator.
     */
    @get:Rule
    val deviceSkipRule = DeviceSkipRule()

    @BeforeTest
    fun setupTree() {
        tree = "TEST-e2e-recaptcha-enterprise"
    }

    /**
     * Tests score-based failure handling.
     *
     * Verifies token generation succeeds but server may return validation error
     * or low risk score.
     */
    @Test
    @RequiresDevice
    fun test01RecaptchaEnterpriseScoreFailure() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("score_failure")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseFailure = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseFailure.reCaptchaSiteKey.isNotEmpty())
        assertTrue(IntegrationTestConfig.recaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseFailure.reCaptchaSiteKey
        )

        recaptchaEnterpriseFailure.verify{
            recaptchaAction = RecaptchaAction.custom("score_failure")
        }.onSuccess { token ->
            assertTrue(token.isNotEmpty())
        }.onFailure {
            fail("Failure in verifying ${it.message}.")
        }

        val inputPayload = recaptchaEnterpriseFailure.payload()["input"]
        assertNotNull(inputPayload)

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        val messageResponse = textOutputCallback.message
        if (!messageResponse.contains("VALIDATION_ERROR")) {
            val outputMessageJson = Json.parseToJsonElement(textOutputCallback.message)
            assertNotNull(outputMessageJson)
            val score = outputMessageJson.jsonObject["riskAnalysis"]?.jsonObject?.get("score")?.jsonPrimitive.toString()
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
    @RequiresDevice
    fun test02RecaptchaEnterpriseCallbackInvalidProjectId() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("invalid_project_id")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseFailure = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseFailure.reCaptchaSiteKey.isNotEmpty())
        assertTrue(IntegrationTestConfig.recaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseFailure.reCaptchaSiteKey
        )

        recaptchaEnterpriseFailure.verify{
            recaptchaAction = RecaptchaAction.custom("invalid_project_id")
        }.onSuccess { token ->
            assertTrue(token.isNotEmpty())
        }.onFailure {
            fail("Failure in verifying ${it.message}.")
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
    @RequiresDevice
    fun test03RecaptchaEnterpriseInvalidVerificationUrl() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("invalid_verification_url")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseFailure = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseFailure.reCaptchaSiteKey.isNotEmpty())
        assertTrue(IntegrationTestConfig.recaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseFailure.reCaptchaSiteKey
        )

        recaptchaEnterpriseFailure.verify{
            recaptchaAction = RecaptchaAction.custom("invalid_verification_url")
        }.onSuccess { token ->
            assertTrue(token.isNotEmpty())
        }.onFailure {
            fail("Failure in verifying ${it.message}.")
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
    @RequiresDevice
    fun test04RecaptchaEnterpriseInvalidSecretKey() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("invalid_secret_key")
        node = node.next() as ContinueNode

        val recaptchaEnterpriseFailure = node.callbacks.first() as ReCaptchaEnterpriseCallback
        assertTrue(recaptchaEnterpriseFailure.reCaptchaSiteKey.isNotEmpty())
        assertTrue(IntegrationTestConfig.recaptchaSiteKey.isNotEmpty())
        assertEquals(
            IntegrationTestConfig.recaptchaSiteKey,
            recaptchaEnterpriseFailure.reCaptchaSiteKey
        )

        recaptchaEnterpriseFailure.verify{
            recaptchaAction = RecaptchaAction.custom("invalid_secret_key")
        }.onSuccess { token ->
            assertTrue(token.isNotEmpty())
        }.onFailure {
            fail("Failure in verifying ${it.message}.")
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
    @RequiresDevice
    fun test05RecaptchaEnterpriseCustomClientError() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

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
    @RequiresDevice
    fun test06RecaptchaEnterpriseInvalidSiteKey() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

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