/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.BaseJourneyTest
import com.pingidentity.journey.module.session
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.protect.journey.PingOneProtectEvaluationCallback
import com.pingidentity.protect.journey.PingOneProtectInitializeCallback
import com.pingidentity.testrail.TestRailCase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING) // These tests need to run in order
class PingOneProtectEvaluationCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        tree = "TEST_PING_ONE_PROTECT_EVALUATE"
    }

    @Test
    fun test01EvaluateNoInit() = runTest {
        var node = defaultJourney.start(tree)  as ContinueNode

        // Select test from the choice callback
        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("evaluate-no-init")
        node = node.next() as ContinueNode

        // Handle the NameCallback
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        // Handle the PingOneProtectEvaluationCallback
        val pingOneProtectEvaluationCallback = node.callbacks.first() as PingOneProtectEvaluationCallback

        // Try to collect the data and ensure it fails since Protect is not initialized
        val result = pingOneProtectEvaluationCallback.collect()
        if (result.isSuccess) {
            fail("ProtectCollector.collect() should have failed since Protect is not initialized.")
        }
        assertTrue(result.isFailure)

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        assertEquals("Client Error", textOutputCallback.message)

        val success = node.next()
        assertTrue(success is SuccessNode)   // This should be the final node
        success as SuccessNode
        logger.i("Session: ${success.session.value}")

        assertNotNull(success.session)
        assertNotNull(defaultJourney.session())
    }

    @Test
    @TestRailCase(32273)
    fun test02EvaluateSuccess() = runTest {
        var node = defaultJourney.start(tree)  as ContinueNode

        // Select test from the choice callback
        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("evaluate-default")
        node = node.next() as ContinueNode

        // Handle the PingOneProtectInitializeCallback
        val pingOneProtectInitializeCallback = node.callbacks.first() as PingOneProtectInitializeCallback
        val initResult = pingOneProtectInitializeCallback.start()
        assertTrue(initResult.isSuccess)

        // Handle the NameCallback
        node = node.next() as ContinueNode
        node.handleLoginCallbacks()

        // Move on and handle the PingOneProtectEvaluationCallback
        node = node.next() as ContinueNode
        val pingOneProtectEvaluationCallback = node.callbacks.first() as PingOneProtectEvaluationCallback
        assertTrue(pingOneProtectEvaluationCallback.pauseBehavioralData)

        // Try to collect the data and ensure it succeeds
        val result = pingOneProtectEvaluationCallback.collect()
        if (result.isFailure) {
            fail("Unexpected failure during evaluate!")
        }
        assertTrue(result.isSuccess)

        val json: JsonObject = pingOneProtectEvaluationCallback.json
        val inputArray = json["input"]?.jsonArray ?: error("Missing 'input' array")

        // Find IDToken1signals and assert non-empty value
        val idToken1signalsValue = inputArray
            .map { it.jsonObject }
            .firstOrNull { it["name"]?.jsonPrimitive?.content == "IDToken1signals" }
            ?.get("value")
            ?.jsonPrimitive
            ?.content
            ?: error("IDToken1signals not found")

        assertTrue(idToken1signalsValue.isNotBlank())

        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        assertEquals("Success", textOutputCallback.message)

        val success = node.next()
        assertTrue(success is SuccessNode)   // This should be the final node
        success as SuccessNode
        logger.i("Session: ${success.session.value}")

        assertNotNull(success.session)
        assertNotNull(defaultJourney.session())
    }
}