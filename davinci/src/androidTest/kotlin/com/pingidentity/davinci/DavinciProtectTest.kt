/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import androidx.test.filters.SmallTest
import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.davinci.collector.LabelCollector
import com.pingidentity.davinci.collector.PasswordCollector
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.module.description
import com.pingidentity.davinci.module.name
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.protect.davinci.ProtectCollector
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.Test

@SmallTest
class DavinciProtectTest {
    val logger: Logger = Logger.STANDARD
    private var daVinci = DaVinci {
        logger = Logger.STANDARD

        module(Oidc) {
            clientId = "021b83ce-a9b1-4ad4-8c1d-79e576eeab76"
            discoveryEndpoint = "https://auth.pingone.ca/02fb4743-189a-4bc7-9d6c-a919edfe6447/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = "org.forgerock.demo://oauth2redirect"
            acrValues = "f47c36d84a95e14dd93d05884792edd5"
        }
    }

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(32255)
    @Test
    fun protectDataCollectionWithCustomHtmlTest() = runTest {
        var node = daVinci.start() as ContinueNode
        // Click the "Protect with HTML Form" button
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // We are at the Login Page which has "skrisk" component
        assertTrue(node.collectors[0] is TextCollector)
        assertTrue(node.collectors[1] is PasswordCollector)
        assertTrue(node.collectors[2] is SubmitCollector)
        assertTrue(node.collectors[3] is ProtectCollector)

        val protectCollector = node.collectors[3] as ProtectCollector

        // "Collect behavioral data" is set to true in the form...
        assertTrue(protectCollector.behavioralDataCollection)

        // "Enable Universal Device Identification" is set to true in the form...
        assertTrue(protectCollector.universalDeviceIdentification)

        // Collect the data and ensure it succeeds
        val result =  protectCollector.collect()
        if (result.isFailure) {
            fail("ProtectCollector.collect() failed: ${result.exceptionOrNull()?.message}")
        }
        assertTrue(result.isSuccess)

        // Fill the login form with username and password and click "Login"
        (node.collectors[0] as? TextCollector)?.value = "jsmith"
        (node.collectors[1] as? PasswordCollector)?.value = "whatever!"
        (node.collectors[2] as? SubmitCollector)?.value = "Login"
        node = node.next() as ContinueNode

        // We are at the "SDK Automation - Risk Evaluation Results" page
        assertEquals(5, node.collectors.size)
        assertTrue(node.collectors[0] is LabelCollector)
        assertTrue(node.collectors[1] is TextCollector)
        assertTrue(node.collectors[2] is TextCollector)
        assertTrue(node.collectors[3] is TextCollector)
        assertTrue(node.collectors[4] is SubmitCollector)

        // Verify the results
        val protectResult = (node.collectors[2] as? TextCollector)?.value
        assertTrue(!protectResult.isNullOrEmpty())
        logger.i("Protect data: $protectResult")

        // Assert a few things about the Protect data
        val jsonResponse = JSONObject(protectResult)
        val rawResponse = jsonResponse.getJSONObject("rawResponse")

        // Assertions for the 'result' object
        val resultObject = rawResponse.getJSONObject("result")
        val level = resultObject.getString("level")
        val score = resultObject.getInt("score")

        assertTrue(level in listOf("LOW", "MEDIUM", "HIGH"))
        assertTrue(score in 1..100)

        // Assertions for the 'event' object
        val eventObject = rawResponse.getJSONObject("event")
        val userObject = eventObject.getJSONObject("user")
        assertEquals("jsmith", userObject.getString("name"))

        // Continue to the next node and finish the flow
        (node.collectors[4] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        assertEquals("Success", node.name)
        assertEquals("Successfully submitted form", node.description)
    }

    @TestRailCase(32256)
    @Test
    fun protectDataCollectionWithPingOneFormTest() = runTest {
        var node = daVinci.start() as ContinueNode
        // Click the "Protect with PingOne Form" button
        (node.collectors[1] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // We are at the "SDK Automation - SignOn Form" page which has "Enable Device Profiling" toggle set to true
        assertTrue(node.collectors[0] is LabelCollector)
        assertTrue(node.collectors[1] is TextCollector)
        assertTrue(node.collectors[2] is PasswordCollector)
        assertTrue(node.collectors[3] is SubmitCollector)
        assertTrue(node.collectors[4] is ProtectCollector)

        val protectCollector = node.collectors[4] as ProtectCollector

        // "Collect behavioral data" is set to true in the form...
        assertTrue(protectCollector.behavioralDataCollection)

        // "Enable Universal Device Identification" is set to true in the form...
        assertTrue(protectCollector.universalDeviceIdentification)

        // Collect the data and ensure it succeeds
        val result =  protectCollector.collect()
        if (result.isFailure) {
            fail("ProtectCollector.collect() failed: ${result.exceptionOrNull()?.message}")
        }
        assertTrue(result.isSuccess)

        // Fill the login form with username and password and click "Login"
        (node.collectors[1] as? TextCollector)?.value = "jsmith"
        (node.collectors[2] as? PasswordCollector)?.value = "whatever!"
        (node.collectors[3] as? SubmitCollector)?.value = "Login"
        node = node.next() as ContinueNode

        // We are at the "SDK Automation - Risk Evaluation Results" page
        assertEquals(5, node.collectors.size)
        assertTrue(node.collectors[0] is LabelCollector)
        assertTrue(node.collectors[1] is TextCollector)
        assertTrue(node.collectors[2] is TextCollector)
        assertTrue(node.collectors[3] is TextCollector)
        assertTrue(node.collectors[4] is SubmitCollector)

        // Verify the results
        val protectResult = (node.collectors[2] as? TextCollector)?.value
        assertTrue(!protectResult.isNullOrEmpty())
        logger.i("Protect data: $protectResult")

        // Assert a few things about the Protect data
        val jsonResponse = JSONObject(protectResult)
        val rawResponse = jsonResponse.getJSONObject("rawResponse")

        // Assertions for the 'result' object
        val resultObject = rawResponse.getJSONObject("result")
        val level = resultObject.getString("level")
        val score = resultObject.getInt("score")

        assertTrue(level in listOf("LOW", "MEDIUM", "HIGH"))
        assertTrue(score in 1..100)

        // Assertions for the 'event' object
        val eventObject = rawResponse.getJSONObject("event")
        val userObject = eventObject.getJSONObject("user")
        assertEquals("jsmith", userObject.getString("name"))

        // Continue to the next node and finish the flow
        (node.collectors[4] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        assertEquals("Success", node.name)
        assertEquals("Successfully submitted form", node.description)
    }
}