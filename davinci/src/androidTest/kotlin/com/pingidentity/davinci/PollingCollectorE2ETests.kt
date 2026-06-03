/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import androidx.test.filters.LargeTest
import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.davinci.collector.LabelCollector
import com.pingidentity.davinci.collector.PollingCollector
import com.pingidentity.davinci.collector.PollingStatus
import com.pingidentity.davinci.collector.QRCodeCollector
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.module.name
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E2E tests for PollingCollector covering two DaVinci policies:
 *   - Simple polling (pollChallengeStatus=false, 3 retries, 2s interval)
 *   - Challenge-status polling (pollChallengeStatus=true, 2s interval)
 */
@LargeTest
class PollingCollectorE2ETests {
    private var daVinci = DaVinci {
        logger = Logger.STANDARD

        module(Oidc) {
            clientId = "a6859a12-5e6e-4f64-96bb-cc8577706bee"
            discoveryEndpoint = "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = "org.forgerock.demo://oauth2redirect"
            acrValues = "fae6bc3d08a5c4f5b8ff95175b117278"
        }
    }

    @BeforeTest
    fun setUp() = runTest {
        daVinci.user()?.logout()
    }

    // =========================================================================
    // Simple (Continue) Polling
    // =========================================================================

    /**
     * All 3 retries exhaust without completion. Each cycle posts "continue" and the server
     * rewinds to the same form; on the third cycle TimedOut is emitted and the final submit
     * returns a 400 ErrorNode with message "timedOut".
     */
    @Test
    fun continuePollingTimeout() = runTest {
        var node = daVinci.start() as ContinueNode
        assertEquals("Select Test Form", node.name)
        assertEquals("Continue Polling", (node.collectors[0] as SubmitCollector).label)
        assertEquals("Challenge Polling", (node.collectors[1] as FlowCollector).label)

        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        assertEquals("Automation - Polling", node.name)
        val pollingCollector = node.collectors.filterIsInstance<PollingCollector>().first()
        assertFalse(pollingCollector.pollChallengeStatus)
        assertEquals(2000, pollingCollector.pollInterval.toInt())
        assertEquals(3, pollingCollector.pollRetries.toInt())
        assertEquals(3, pollingCollector.retriesAllowed)

        // Cycle 1 — retriesAllowed 3→2, emits Complete("continue"); server rewinds to same form
        var statuses = pollingCollector.pollStatus().toList()
        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Complete)
        assertEquals("continue", (statuses[0] as PollingStatus.Complete).status)
        assertEquals("continue", pollingCollector.value)
        assertEquals(2, pollingCollector.retriesAllowed)
        node = node.next() as ContinueNode
        assertEquals("Automation - Polling", node.name)

        // Cycle 2 — retriesAllowed 2→1, emits Complete("continue"); server rewinds to same form
        statuses = pollingCollector.pollStatus().toList()
        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Complete)
        assertEquals("continue", (statuses[0] as PollingStatus.Complete).status)
        assertEquals("continue", pollingCollector.value)
        assertEquals(1, pollingCollector.retriesAllowed)
        node = node.next() as ContinueNode
        assertEquals("Automation - Polling", node.name)

        // Cycle 3 — retriesAllowed 1→0, emits TimedOut; submit "timedOut" → 400 ErrorNode
        statuses = pollingCollector.pollStatus().toList()
        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.TimedOut)
        assertEquals("timedOut", pollingCollector.value)
        assertEquals(0, pollingCollector.retriesAllowed)

        val result = node.next()
        assertTrue(result is ErrorNode)
        assertEquals("timedOut", (result as ErrorNode).message.trim())
    }

    /**
     * User clicks "Finish" after one poll cycle, bypassing remaining retries.
     * The server skips the rewind and returns the "Done" message form directly.
     */
    @Test
    fun continuePollingFinish() = runTest {
        var node = daVinci.start() as ContinueNode
        assertEquals("Select Test Form", node.name)

        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        assertEquals("Automation - Polling", node.name)
        val pollingCollector = node.collectors.filterIsInstance<PollingCollector>().first()
        assertFalse(pollingCollector.pollChallengeStatus)

        // One poll cycle → Complete("continue"); server rewinds to the same polling form
        val statuses = pollingCollector.pollStatus().toList()
        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Complete)
        assertEquals("continue", (statuses[0] as PollingStatus.Complete).status)
        assertEquals("continue", pollingCollector.value)
        node = node.next() as ContinueNode
        assertEquals("Automation - Polling", node.name)

        // Click "Finish" → server returns the "Done" message form directly
        val finishButton = node.collectors.filterIsInstance<FlowCollector>()
            .first { it.label == "Finish" }
        finishButton.value = finishButton.label
        node = node.next() as ContinueNode

        assertEquals("Automation - Polling Message", node.name)
        val doneLabel = node.collectors.filterIsInstance<LabelCollector>()
            .first { it.content == "Message: Done" }
        assertEquals("Message: Done", doneLabel.content)
    }

    // =========================================================================
    // Challenge-Status Polling
    // =========================================================================

    /**
     * No approval is given; all 3 poll cycles return status="started" (isChallengeComplete=false),
     * so pollStatus() emits Continue(1,3) → Continue(2,3) → Continue(3,3) → TimedOut.
     * The final submit returns a 400 ErrorNode with message "timedOut".
     */
    @Test
    fun challengePollingTimeout() = runTest {
        var node = daVinci.start() as ContinueNode
        assertEquals("Select Test Form", node.name)
        assertTrue(node.collectors[1] is FlowCollector)
        assertEquals("Challenge Polling", (node.collectors[1] as FlowCollector).label)

        (node.collectors[1] as FlowCollector).value = "click"
        node = node.next() as ContinueNode

        assertEquals("Automation - Polling", node.name)
        val pollingCollector = node.collectors.filterIsInstance<PollingCollector>().first()
        assertTrue(pollingCollector.pollChallengeStatus)
        assertEquals(2000, pollingCollector.pollInterval.toInt())
        assertEquals(3, pollingCollector.pollRetries.toInt())
        assertTrue(pollingCollector.challenge.isNotEmpty())

        val statuses = pollingCollector.pollStatus().toList()

        assertEquals(4, statuses.size)

        assertTrue(statuses[0] is PollingStatus.Continue)
        assertEquals(1, (statuses[0] as PollingStatus.Continue).retryCount)
        assertEquals(3, (statuses[0] as PollingStatus.Continue).maxRetries)

        assertTrue(statuses[1] is PollingStatus.Continue)
        assertEquals(2, (statuses[1] as PollingStatus.Continue).retryCount)
        assertEquals(3, (statuses[1] as PollingStatus.Continue).maxRetries)

        assertTrue(statuses[2] is PollingStatus.Continue)
        assertEquals(3, (statuses[2] as PollingStatus.Continue).retryCount)
        assertEquals(3, (statuses[2] as PollingStatus.Continue).maxRetries)

        assertTrue(statuses[3] is PollingStatus.TimedOut)
        assertEquals("timedOut", pollingCollector.value)

        val result = node.next()
        assertTrue(result is ErrorNode)
        assertEquals("timedOut", (result as ErrorNode).message.trim())
    }

    /**
     * OOB approval is simulated by GETting the magic link (from the LabelCollector) on a
     * background thread while pollStatus() polls concurrently. The approval is sent once the
     * first Continue status is received, guaranteeing the challenge is registered before the
     * approval request is sent. The last emitted status must be Complete("approved") and the
     * flow must advance to the "Automation - Polling Message" form.
     *
     * runBlocking is intentional: the test launches two concurrent real-IO coroutines (poll
     * loop + OOB approval) that must interleave against a live server. runTest's virtual-time
     * scheduler would auto-advance delays inside pollStatus(), causing the approval request to
     * race ahead of the challenge registration and producing non-deterministic failures.
     */
    @Test
    fun challengePollingApproval() = runBlocking {
        var node = withContext(Dispatchers.IO) { daVinci.start() } as ContinueNode
        assertEquals("Select Test Form", node.name)
        assertTrue(node.collectors[1] is FlowCollector)
        assertEquals("Challenge Polling", (node.collectors[1] as FlowCollector).label)

        (node.collectors[1] as FlowCollector).value = "click"
        node = withContext(Dispatchers.IO) { node.next() } as ContinueNode

        assertEquals("Automation - Polling", node.name)
        val pollingCollector = node.collectors.filterIsInstance<PollingCollector>().first()
        assertTrue(pollingCollector.pollChallengeStatus)
        assertTrue(pollingCollector.pollInterval.toInt() > 0)
        assertTrue(pollingCollector.pollRetries.toInt() > 0)
        assertTrue(pollingCollector.challenge.isNotEmpty())

        // The OOB URL is embedded in the label as "Number Challenge <url>"
        val magicLink = node.collectors.filterIsInstance<LabelCollector>()
            .first { it.content.startsWith("Number Challenge ") }
            .content.substringAfter("Number Challenge ").trim()
        assertTrue(magicLink.startsWith("https://"), "Expected a magic link URL, got: $magicLink")

        // Fire approval only after the first Continue status is received, guaranteeing the
        // challenge is registered server-side before the approval request is sent.
        val firstContinueSeen = CompletableDeferred<Unit>()
        val pollJob = async(Dispatchers.IO) {
            pollingCollector.pollStatus()
                .onEach { if (it is PollingStatus.Continue && !firstContinueSeen.isCompleted) firstContinueSeen.complete(Unit) }
                .toList()
        }
        val approvalJob = async(Dispatchers.IO) {
            withTimeoutOrNull(30_000) { firstContinueSeen.await() } ?: return@async
            (java.net.URL(magicLink).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                runCatching { responseCode }
                disconnect()
            }
        }

        approvalJob.await()
        val statuses = pollJob.await()

        assertTrue(statuses.isNotEmpty())
        val lastStatus = statuses.last()
        assertTrue(lastStatus is PollingStatus.Complete)
        assertEquals("approved", (lastStatus as PollingStatus.Complete).status)
        assertEquals("approved", pollingCollector.value)

        node = withContext(Dispatchers.IO) { node.next() } as ContinueNode
        assertEquals("Automation - Polling Message", node.name)
        val approvedLabel = node.collectors.filterIsInstance<LabelCollector>()
            .first { it.content == "Message: approved" }
        assertEquals("Message: approved", approvedLabel.content)
    }

    // =========================================================================
    // QR Code + Challenge-Status Polling
    // =========================================================================

    /**
     * Decodes the URL encoded in a QR code Bitmap using ZXing.
     */
    private fun decodeQrBitmap(bitmap: android.graphics.Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = com.google.zxing.RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
        return com.google.zxing.MultiFormatReader().decode(binaryBitmap).text
    }

    /**
     * The "Challenge Polling QRCode" flow shows a QR_CODE field alongside a POLLING field.
     * No approval is given; all retries exhaust → TimedOut → 400 ErrorNode.
     * The test verifies QRCodeCollector properties before polling begins.
     */
    @Test
    fun qrCodeChallengePollingTimeout() = runTest {
        var node = daVinci.start() as ContinueNode
        assertEquals("Select Test Form", node.name)
        assertTrue(node.collectors[2] is FlowCollector)
        assertEquals("Challenge Polling QRCode", (node.collectors[2] as FlowCollector).label)

        (node.collectors[2] as FlowCollector).value = "click"
        node = node.next() as ContinueNode

        assertEquals("Automation - Polling with QR Code", node.name)

        // Verify QRCodeCollector is present and well-formed
        val qrCodeCollector = node.collectors.filterIsInstance<QRCodeCollector>().first()
        assertTrue(qrCodeCollector.content.startsWith("data:image/"), "Expected a data URI, got: ${qrCodeCollector.content}")
        assertTrue(qrCodeCollector.content.contains("base64,"), "Expected base64 encoding in data URI")
        assertNotNull(qrCodeCollector.bitmap(), "bitmap() must decode successfully from a valid base64 data URI")

        // Verify PollingCollector is present and configured for challenge-status polling
        val pollingCollector = node.collectors.filterIsInstance<PollingCollector>().first()
        assertTrue(pollingCollector.pollChallengeStatus)
        assertEquals(2000, pollingCollector.pollInterval.toInt())
        assertTrue(pollingCollector.challenge.isNotEmpty())

        // Poll until all retries are exhausted — no OOB approval, so TimedOut is emitted last
        val statuses = pollingCollector.pollStatus().toList()

        assertTrue(statuses.isNotEmpty())
        assertTrue(statuses.last() is PollingStatus.TimedOut)
        assertEquals("timedOut", pollingCollector.value)

        val result = node.next()
        assertTrue(result is ErrorNode)
        assertEquals("timedOut", (result as ErrorNode).message.trim())
    }

    /**
     * Simulates scanning the QR code by decoding the URL from the QR bitmap (ZXing) and
     * GETting it on a background thread while pollStatus() polls concurrently. The approval
     * is sent once the first Continue status is received; the last status must be
     * Complete("approved") and the flow must advance to "Automation - Polling Message".
     *
     * runBlocking is intentional: the test launches two concurrent real-IO coroutines (poll
     * loop + OOB approval) that must interleave against a live server. runTest's virtual-time
     * scheduler would auto-advance delays inside pollStatus(), causing the approval request to
     * race ahead of the challenge registration and producing non-deterministic failures.
     */
    @Test
    fun qrCodeChallengePollingApproval() = runBlocking {
        var node = withContext(Dispatchers.IO) { daVinci.start() } as ContinueNode
        assertEquals("Select Test Form", node.name)
        assertTrue(node.collectors[2] is FlowCollector)
        assertEquals("Challenge Polling QRCode", (node.collectors[2] as FlowCollector).label)

        (node.collectors[2] as FlowCollector).value = "click"
        node = withContext(Dispatchers.IO) { node.next() } as ContinueNode

        assertEquals("Automation - Polling with QR Code", node.name)

        // Decode the approval URL from the QR code bitmap
        val qrCodeCollector = node.collectors.filterIsInstance<QRCodeCollector>().first()
        val bitmap = qrCodeCollector.bitmap()
        assertNotNull(bitmap, "bitmap() must decode successfully")
        val approvalUrl = decodeQrBitmap(bitmap!!)
        assertTrue(approvalUrl.startsWith("https://"), "Expected an HTTPS URL in QR code, got: $approvalUrl")

        val pollingCollector = node.collectors.filterIsInstance<PollingCollector>().first()
        assertTrue(pollingCollector.pollChallengeStatus)
        assertTrue(pollingCollector.challenge.isNotEmpty())

        // Fire approval only after the first Continue status is received, guaranteeing the
        // challenge is registered server-side before the approval request is sent.
        val firstContinueSeen = CompletableDeferred<Unit>()
        val pollJob = async(Dispatchers.IO) {
            pollingCollector.pollStatus()
                .onEach { if (it is PollingStatus.Continue && !firstContinueSeen.isCompleted) firstContinueSeen.complete(Unit) }
                .toList()
        }
        val approvalJob = async(Dispatchers.IO) {
            withTimeoutOrNull(30_000) { firstContinueSeen.await() } ?: return@async
            (java.net.URL(approvalUrl).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                runCatching { responseCode }
                disconnect()
            }
        }

        approvalJob.await()
        val statuses = pollJob.await()

        assertTrue(statuses.isNotEmpty())
        val lastStatus = statuses.last()
        assertTrue(lastStatus is PollingStatus.Complete)
        assertEquals("approved", (lastStatus as PollingStatus.Complete).status)
        assertEquals("approved", pollingCollector.value)

        node = withContext(Dispatchers.IO) { node.next() } as ContinueNode
        assertEquals("Automation - Polling Message", node.name)
        val approvedLabel = node.collectors.filterIsInstance<LabelCollector>()
            .first { it.content == "Message: approved" }
        assertEquals("Message: approved", approvedLabel.content)
    }
}
