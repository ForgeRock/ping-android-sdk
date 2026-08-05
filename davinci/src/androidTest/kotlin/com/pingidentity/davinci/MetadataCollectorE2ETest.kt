/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import androidx.test.filters.SmallTest
import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.davinci.collector.LabelCollector
import com.pingidentity.davinci.collector.MetadataCollector
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.module.name
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E2E tests for [MetadataCollector] — DaVinci SDK Integrator connector
 * (`exchangeCustomMetadata` capability).
 *
 * The test flow starts with a "Select Test Form" that presents a "Metadata" button.
 * Tapping it transitions to an `exchangeCustomMetadata` node that returns a single METADATA
 * field with key "sdkMetadata" and payload {"testkey":"testValue"}.
 * After the SDK submits its response the flow transitions to an "Automation - Message" form
 * which echoes the submitted value back to the client, confirming what DaVinci received.
 *
 */
@SmallTest
class MetadataCollectorE2ETest {

    companion object {
        // --- Select Test Form ---
        const val METADATA_BUTTON_INDEX = 0

        // --- exchangeCustomMetadata node ---
        const val METADATA_COLLECTOR_INDEX = 0

        // --- Automation - Message form ---
        // Index 0 = LABEL (echoed value), index 1 = SUBMIT_BUTTON ("Continue")
        const val LABEL_INDEX = 0
        const val SUBMIT_INDEX = 1
    }

    private var daVinci = DaVinci {
        logger = Logger.STANDARD

        module(Oidc) {
            clientId = DaVinciTestConfig.davinciClientId
            discoveryEndpoint = DaVinciTestConfig.davinciDiscoveryEndpoint
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = DaVinciTestConfig.davinciRedirectUri
            acrValues = DaVinciTestConfig.davinciMetadataAcrValues
        }
    }

    @BeforeTest
    fun setUp() = runTest {
        daVinci.user()?.logout()
    }

    // -------------------------------------------------------------------------
    // Helper: navigate from start() to the exchangeCustomMetadata node
    // -------------------------------------------------------------------------

    private suspend fun navigateToMetadataNode(): ContinueNode {
        var node = daVinci.start() as ContinueNode
        assertEquals("Select Test Form", node.name)
        assertTrue(node.collectors[METADATA_BUTTON_INDEX] is SubmitCollector)
        assertEquals("Metadata", (node.collectors[METADATA_BUTTON_INDEX] as SubmitCollector).label)
        (node.collectors[METADATA_BUTTON_INDEX] as SubmitCollector).value = "click"
        return node.next() as ContinueNode
    }

    // =========================================================================
    // Collector shape and payload
    // =========================================================================

    @Test
    fun metadataCollectorIsPresentWithCorrectShape() = runTest {
        val node = navigateToMetadataNode()

        assertEquals(1, node.collectors.size)
        assertTrue(node.collectors[METADATA_COLLECTOR_INDEX] is MetadataCollector)

        val collector = node.collectors[METADATA_COLLECTOR_INDEX] as MetadataCollector
        assertEquals("sdkMetadata", collector.key)
        assertEquals("METADATA", collector.type)

        // Payload must contain exactly the key/value the test flow sends
        assertEquals("testValue", collector.metadata["testkey"]?.jsonPrimitive?.content)
    }

    @Test
    fun metadataCollectorPayloadIsNullBeforeSetResultOrSetError() = runTest {
        val node = navigateToMetadataNode()
        val collector = node.collectors[METADATA_COLLECTOR_INDEX] as MetadataCollector
        assertNull(collector.payload())
    }

    // =========================================================================
    // Validation
    // =========================================================================

    @Test
    fun validateRequiresResponseBeforeSubmit() = runTest {
        val node = navigateToMetadataNode()
        val collector = node.collectors[METADATA_COLLECTOR_INDEX] as MetadataCollector

        val errors = collector.validate()
        assertTrue(errors.isNotEmpty())
        assertEquals("Required", errors[0].toString())
    }

    @Test
    fun validatePassesAfterSetResult() = runTest {
        val node = navigateToMetadataNode()
        val collector = node.collectors[METADATA_COLLECTOR_INDEX] as MetadataCollector

        collector.setResult(buildJsonObject { put("status", "success") })
        assertTrue(collector.validate().isEmpty())
    }

    @Test
    fun validatePassesAfterSetError() = runTest {
        val node = navigateToMetadataNode()
        val collector = node.collectors[METADATA_COLLECTOR_INDEX] as MetadataCollector

        collector.setError(errorCode = "100", message = "An error occurred")
        assertTrue(collector.validate().isEmpty())
    }

    // =========================================================================
    // Success path — setResult
    // =========================================================================

    @Test
    fun setResultAdvancesFlowToSuccessBranch() = runTest {
        var node = navigateToMetadataNode()
        val collector = node.collectors[METADATA_COLLECTOR_INDEX] as MetadataCollector

        collector.setResult(buildJsonObject { put("status", "success") })
        node = node.next() as ContinueNode

        // DaVinci echoes the submitted value in the "Automation - Message" node
        assertEquals("Automation - Message", node.name)
        val label = node.collectors[LABEL_INDEX] as LabelCollector
        assertTrue(
            label.content.contains("success"),
            "Expected the echoed label to contain 'success', was: ${label.content}"
        )

        // Continue back to the Select Test Form — confirms the success branch completed
        (node.collectors[SUBMIT_INDEX] as SubmitCollector).value = "click"
        node = node.next() as ContinueNode
        assertEquals("Select Test Form", node.name)
    }

    @Test
    fun setResultPayloadContainsProvidedJson() = runTest {
        val node = navigateToMetadataNode()
        val collector = node.collectors[METADATA_COLLECTOR_INDEX] as MetadataCollector

        val result = buildJsonObject { put("status", "success") }
        collector.setResult(result)

        val payload = collector.payload()
        assertNotNull(payload)
        assertEquals("success", payload["status"]?.jsonPrimitive?.content)
    }

    // =========================================================================
    // Error path — setError
    // =========================================================================

    @Test
    fun setErrorAdvancesFlowToErrorBranch() = runTest {
        var node = navigateToMetadataNode()
        val collector = node.collectors[METADATA_COLLECTOR_INDEX] as MetadataCollector

        collector.setError(errorCode = "100", message = "An error occurred")
        node = node.next() as ContinueNode

        // DaVinci echoes the error code and message in "Automation - Message"
        assertEquals("Automation - Message", node.name)
        val label = node.collectors[LABEL_INDEX] as LabelCollector
        assertTrue(
            label.content.contains("100"),
            "Expected echoed label to contain error code '100', was: ${label.content}"
        )
        assertTrue(
            label.content.contains("An error occurred"),
            "Expected echoed label to contain error message, was: ${label.content}"
        )

        // Continue back to the Select Test Form — confirms the error branch completed
        (node.collectors[SUBMIT_INDEX] as SubmitCollector).value = "click"
        node = node.next() as ContinueNode
        assertEquals("Select Test Form", node.name)
    }

    @Test
    fun setErrorPayloadContainsStructuredErrorEnvelope() = runTest {
        val node = navigateToMetadataNode()
        val collector = node.collectors[METADATA_COLLECTOR_INDEX] as MetadataCollector

        collector.setError(errorCode = "100", message = "An error occurred")

        val payload = collector.payload()
        assertNotNull(payload)
        val error = payload["error"]?.jsonObject
        assertNotNull(error)
        assertEquals("100", error["code"]?.jsonPrimitive?.content)
        assertEquals("An error occurred", error["message"]?.jsonPrimitive?.content)
    }

    // =========================================================================
    // Repeated iterations (loop)
    // =========================================================================

    @Test
    fun metadataFlowCanBeTraversedTwiceInTheSameSession() = runTest {
        // First iteration — success
        var node = navigateToMetadataNode()
        var collector = node.collectors[METADATA_COLLECTOR_INDEX] as MetadataCollector
        collector.setResult(buildJsonObject { put("status", "success") })
        node = node.next() as ContinueNode
        assertEquals("Automation - Message", node.name)
        (node.collectors[SUBMIT_INDEX] as SubmitCollector).value = "click"
        node = node.next() as ContinueNode
        assertEquals("Select Test Form", node.name)

        // Second iteration — error; the new collector must be independent of the first
        (node.collectors[METADATA_BUTTON_INDEX] as SubmitCollector).value = "click"
        node = node.next() as ContinueNode
        assertEquals(1, node.collectors.size)
        assertTrue(node.collectors[METADATA_COLLECTOR_INDEX] is MetadataCollector)

        collector = node.collectors[METADATA_COLLECTOR_INDEX] as MetadataCollector
        // Payload from previous iteration must not carry over
        assertNull(collector.payload())
        assertEquals("testValue", collector.metadata["testkey"]?.jsonPrimitive?.content)

        collector.setError(errorCode = "100", message = "An error occurred")
        node = node.next() as ContinueNode
        assertEquals("Automation - Message", node.name)
        assertTrue((node.collectors[LABEL_INDEX] as LabelCollector).content.contains("100"))

        (node.collectors[SUBMIT_INDEX] as SubmitCollector).value = "click"
        node = node.next() as ContinueNode
        assertEquals("Select Test Form", node.name)
    }
}
