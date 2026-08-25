/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import androidx.test.filters.SmallTest
import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.davinci.collector.ImageCollector
import com.pingidentity.davinci.collector.LabelCollector
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.module.name
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E2E tests for [ImageCollector] — DaVinci IMAGE-type form field.
 *
 * The test flow starts with a "Select Test Form" that presents a FLOW_BUTTON "Image" button.
 * Tapping Image transitions to the "Automation - Image" showForm node which contains:
 *
 *   index 0 — LabelCollector  (key "title-text", richContent "Image Collector Test")
 *   index 1 — ImageCollector  (key "image", description "Image Collector Test", raster imageUrl, no hyperlinkUrl)
 *   index 2 — SubmitCollector (key "submit", label "Continue")
 *
 * Tapping Continue loops back to the Select Test Form.
 */
@SmallTest
class ImageCollectorE2ETest {

    companion object {
        // --- Select Test Form ---
        const val IMAGE_BUTTON_INDEX = 1   // FLOW_BUTTON

        // --- Automation - Image node ---
        const val LABEL_INDEX = 0          // LABEL  (title-text)
        const val IMAGE_INDEX = 1          // IMAGE  (image)
        const val SUBMIT_INDEX = 2         // SUBMIT_BUTTON (Continue)

        const val EXPECTED_IMAGE_KEY = "image"
        const val EXPECTED_IMAGE_DESCRIPTION = "Image Collector Test"
        const val EXPECTED_IMAGE_URL = "https://img.magnific.com/free-photo/closeup-shot-beautiful-butterfly-with-interesting-textures-orange-petaled-flower_181624-7640.jpg"
        const val EXPECTED_HYPERLINK_URL = "https://www.pingidentity.com/en.html"
        const val EXPECTED_NODE_NAME = "Automation - Image"
    }

    private var daVinci = DaVinci {
        logger = Logger.STANDARD

        module(Oidc) {
            clientId = DaVinciTestConfig.davinciClientId
            discoveryEndpoint = DaVinciTestConfig.davinciDiscoveryEndpoint
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = DaVinciTestConfig.davinciRedirectUri
            acrValues = DaVinciTestConfig.davinciImageAcrValues
        }
    }

    @BeforeTest
    fun setUp() = runTest {
        daVinci.user()?.logout()
    }

    // -------------------------------------------------------------------------
    // Helper: navigate from start() to the Automation - Image form
    // -------------------------------------------------------------------------

    private suspend fun navigateToImageForm(): ContinueNode {
        var node = daVinci.start() as ContinueNode
        assertEquals("Select Test Form", node.name)
        assertTrue(node.collectors[IMAGE_BUTTON_INDEX] is FlowCollector)
        assertEquals("Image", (node.collectors[IMAGE_BUTTON_INDEX] as FlowCollector).label)
        (node.collectors[IMAGE_BUTTON_INDEX] as FlowCollector).value = "click"
        return node.next() as ContinueNode
    }

    // =========================================================================
    // Node shape
    // =========================================================================

    @Test
    fun automationImageNodeHasCorrectName() = runTest {
        val node = navigateToImageForm()
        assertEquals(EXPECTED_NODE_NAME, node.name)
    }

    // =========================================================================
    // ImageCollector properties
    // =========================================================================

    @Test
    fun imageCollectorHasCorrectKey() = runTest {
        val node = navigateToImageForm()
        val collector = node.collectors[IMAGE_INDEX] as ImageCollector
        assertEquals(EXPECTED_IMAGE_KEY, collector.key)
    }

    @Test
    fun imageCollectorHasCorrectDescription() = runTest {
        val node = navigateToImageForm()
        val collector = node.collectors[IMAGE_INDEX] as ImageCollector
        assertEquals(EXPECTED_IMAGE_DESCRIPTION, collector.description)
    }

    @Test
    fun imageCollectorHasCorrectImageUrl() = runTest {
        val node = navigateToImageForm()
        val collector = node.collectors[IMAGE_INDEX] as ImageCollector
        assertEquals(EXPECTED_IMAGE_URL, collector.imageUrl)
    }

    @Test
    fun imageCollectorHasCorrectHyperlinkUrl() = runTest {
        val node = navigateToImageForm()
        val collector = node.collectors[IMAGE_INDEX] as ImageCollector
        assertEquals(EXPECTED_HYPERLINK_URL, collector.hyperlinkUrl)
    }

    @Test
    fun imageCollectorIdReturnsKey() = runTest {
        val node = navigateToImageForm()
        val collector = node.collectors[IMAGE_INDEX] as ImageCollector
        assertEquals(collector.key, collector.id())
    }

    @Test
    fun labelCollectorRichContentContainsTitle() = runTest {
        val node = navigateToImageForm()
        val label = node.collectors[LABEL_INDEX] as LabelCollector
        val richContent = label.richContent
        assertNotNull(richContent)
        assertEquals("Image Collector Test", richContent.content)
    }
}
