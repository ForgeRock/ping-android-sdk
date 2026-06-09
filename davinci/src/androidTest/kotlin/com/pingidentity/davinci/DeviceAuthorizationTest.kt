/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import androidx.test.filters.LargeTest
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.oidc.DeviceAuthorizationResponse
import com.pingidentity.oidc.DeviceFlowStatus
import com.pingidentity.oidc.OidcDeviceClient
import android.net.Uri
import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.davinci.collector.PasswordCollector
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.oidc.module.VERIFICATION_URI_COMPLETE
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.utils.Result
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E2E tests for the OAuth 2.0 Device Authorization Grant (RFC 8628) flow against PingOne/DaVinci.
 *
 * These tests exercise the requesting-device side only: they verify that the SDK correctly
 * initiates the flow and polls the token endpoint. No approving-device interaction is performed,
 * so the polling loop is expected to receive `authorization_pending` responses until the device
 * code expires or the test cancels collection.
 *
 * Requires a live network connection to PingOne.
 */
@LargeTest
class DeviceAuthorizationTest {

    private val client = OidcDeviceClient {
        logger = Logger.STANDARD
        clientId = "d6505e10-d019-4f9d-ae76-d0648ca9c9c3"
        discoveryEndpoint =
            "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration"
        scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
        storage { fileName = "device_flow_test" }
    }

    // Approving-device DaVinci instance — same tenant, separate OIDC client/storage.
    private val approvingDaVinci = DaVinci {
        logger = Logger.STANDARD
        module(Oidc) {
            clientId = "a6859a12-5e6e-4f64-96bb-cc8577706bee"
            discoveryEndpoint =
                "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = "org.forgerock.demo://oauth2redirect"
            acrValues = "25abee29d6271102825e4b076c8de7c3"
            storage { fileName = "device_flow_approver_test" }
        }
    }

    @BeforeTest
    fun setUp(): Unit = runBlocking {
        // Clear any leftover token from a previous test run so each test starts fresh.
        client.user()?.logout()
    }

    /**
     * TC-01: Start device authorization flow — verify user code, verification URL, and polling.
     *
     * Collects [DeviceFlowStatus.Started] and the first [DeviceFlowStatus.Polling] emission, then
     * cancels the flow. This keeps the test under ~10 s (one 5 s poll interval) instead of waiting
     * for the full device-code expiry (~30 s on this tenant).
     *
     * Verifies:
     * - The first emission is [DeviceFlowStatus.Started] with a well-formed user code,
     *   verification URI, and server-specified poll interval.
     * - At least one [DeviceFlowStatus.Polling] emission follows, confirming the SDK polled
     *   the token endpoint.
     * - The first Polling emission carries the server-specified interval and a positive nextPollAt.
     */
    @Test(timeout = 20_000)
    fun startFlowEmitsStartedThenPolling(): Unit = runBlocking {
        val statuses = mutableListOf<DeviceFlowStatus>()
        val pollingChannel = Channel<DeviceFlowStatus.Polling>(capacity = 1)

        // Collect in the background; signal as soon as the first Polling arrives.
        val job = async {
            client.deviceAuthorization()
                .onEach { status ->
                    statuses.add(status)
                    if (status is DeviceFlowStatus.Polling) pollingChannel.trySend(status)
                }
                .toList()
        }

        // Wait for the first Polling emission, then cancel — no need to wait for expiry.
        pollingChannel.receive()
        job.cancel()

        // --- Started state ---
        val started = statuses.first()
        assertIs<DeviceFlowStatus.Started>(started)

        val response: DeviceAuthorizationResponse = started.response

        assertTrue(response.userCode.isNotBlank(), "userCode should not be blank")
        assertTrue(
            response.userCode.matches(Regex("[A-Z0-9]{4}-[A-Z0-9]{4}")),
            "userCode '${response.userCode}' should match pattern XXXX-XXXX"
        )

        assertTrue(response.verificationUri.isNotBlank(), "verificationUri should not be blank")
        assertTrue(
            response.verificationUri.startsWith("https://auth.pingone.ca/"),
            "verificationUri should start with 'https://auth.pingone.ca/'"
        )

        assertNotNull(response.verificationUriComplete, "verificationUriComplete should be present")
        assertTrue(
            response.verificationUriComplete!!.contains("user_code="),
            "verificationUriComplete should contain 'user_code='"
        )

        assertTrue(response.deviceCode.isNotBlank(), "deviceCode should not be blank")
        assertTrue(response.expiresIn > 0, "expiresIn should be positive")
        assertEquals(5, response.interval, "interval should be 5 seconds (PingOne default)")

        // --- Polling state ---
        val firstPolling = statuses.filterIsInstance<DeviceFlowStatus.Polling>().first()
        assertEquals(1, firstPolling.pollCount, "first pollCount should be 1")
        assertEquals(response.interval, firstPolling.pollInterval,
            "pollInterval should equal the server-specified interval")
        assertTrue(firstPolling.nextPollAt > 0, "nextPollAt should be a positive epoch millis")
    }

    /**
     * TC-02: Successful device authorization via DaVinci approval.
     *
     * Starts the device flow on the requesting side, then concurrently drives a DaVinci login
     * flow on the approving side using [VERIFICATION_URI_COMPLETE]. The polling loop on the
     * requesting side picks up the approval and emits [DeviceFlowStatus.Success].
     *
     * Verifies:
     * - The requesting device receives [DeviceFlowStatus.Success] as the terminal state.
     * - A valid access token is accessible via the returned [User].
     * - [OidcDeviceClient.user] returns a non-null user after the flow completes.
     */
    @Test(timeout = 60_000)
    fun deviceAuthorizationApprovalViaDaVinci(): Unit = runBlocking {
        // Channel used to hand the verificationUriComplete from the collecting coroutine to the
        // approving coroutine without starting a second device flow.
        val startedChannel = Channel<DeviceFlowStatus.Started>(capacity = 1)

        // Launch the device flow collection in the background so it keeps polling while we
        // drive the approval flow below. onEach forwards the Started emission immediately
        // so the approving side can begin without waiting for the full flow to complete.
        val deviceFlowJob = async {
            client.deviceAuthorization()
                .onEach { status ->
                    if (status is DeviceFlowStatus.Started) startedChannel.trySend(status)
                }
                .toList()
        }

        // Wait until the requesting device has a verification URI before approving.
        val started = startedChannel.receive()
        val verificationUriComplete = checkNotNull(started.response.verificationUriComplete) {
            "verificationUriComplete must be present for DaVinci approval"
        }

        // Approving side: drive the DaVinci login flow with the verification URI.
        // The Oidc module extracts user_code and submits it to the device endpoint on SuccessNode.
        var node = approvingDaVinci.start {
            VERIFICATION_URI_COMPLETE to Uri.parse(verificationUriComplete)
        }
        node = node as ContinueNode

        // Step 1 — Sign On form: username + password.
        (node.collectors[0] as? TextCollector)?.value = "e2euser"
        (node.collectors[1] as? PasswordCollector)?.value = "2222"
        (node.collectors[2] as? SubmitCollector)?.value = "Sign On"
        node = node.next()
        node = node as ContinueNode

        // Step 2 — Device Code approval form: tap "Approve Device" (FLOW_BUTTON at index 1).
        // ERROR_DISPLAY is not a registered collector type so it is excluded from the list:
        // index 0 = TEXT (device-code), index 1 = FLOW_BUTTON (button-approve), index 2 = FLOW_BUTTON (button-reject).
        (node.collectors[1] as? FlowCollector)?.value = "click"
        // The approval call returns a DaVinci Connector (ContinueNode subclass) wrapping the
        // "returnSuccessResponseRedirect" response, not a SuccessNode — that is the DaVinci SDK's
        // expected behaviour for this flow. What matters is the requesting device receives Success.
        val approvalResult = node.next()
        assertIs<ContinueNode>(approvalResult)

        // Requesting side: wait for the polling loop to pick up the approval.
        val statuses = deviceFlowJob.await()
        assertIs<DeviceFlowStatus.Success>(statuses.last())

        val user = (statuses.last() as DeviceFlowStatus.Success).user
        val token = user.token()
        assertIs<Result.Success<*>>(token)
        assertNotNull((token as Result.Success).value)

        // Confirm the token is accessible via client.user() after the flow completes.
        assertNotNull(client.user())

        // Clean up: log out both sides.
        client.user()?.logout()
        approvingDaVinci.user()?.logout()
    }

    /**
     * TC-03: Device authorization denied via DaVinci.
     *
     * Starts the device flow, drives a DaVinci login, then taps "Reject Device" on the approval
     * form. The server returns `access_denied` on the next poll and the requesting side emits
     * [DeviceFlowStatus.AccessDenied] as the terminal state.
     *
     * Verifies:
     * - The DaVinci reject step returns an [ErrorNode] (server responds 400 with the rejection).
     * - The requesting device receives [DeviceFlowStatus.AccessDenied] as the terminal state.
     * - No token is stored ([OidcDeviceClient.user] returns null).
     */
    @Test(timeout = 60_000)
    fun deviceAuthorizationRejectedViaDaVinci(): Unit = runBlocking {
        val startedChannel = Channel<DeviceFlowStatus.Started>(capacity = 1)

        val deviceFlowJob = async {
            client.deviceAuthorization()
                .onEach { status ->
                    if (status is DeviceFlowStatus.Started) startedChannel.trySend(status)
                }
                .toList()
        }

        val started = startedChannel.receive()
        val verificationUriComplete = checkNotNull(started.response.verificationUriComplete) {
            "verificationUriComplete must be present for DaVinci rejection"
        }

        var node = approvingDaVinci.start {
            VERIFICATION_URI_COMPLETE to Uri.parse(verificationUriComplete)
        }
        node = node as ContinueNode

        // Step 1 — Sign On form.
        (node.collectors[0] as? TextCollector)?.value = "e2euser"
        (node.collectors[1] as? PasswordCollector)?.value = "2222"
        (node.collectors[2] as? SubmitCollector)?.value = "Sign On"
        node = node.next()
        node = node as ContinueNode

        // Step 2 — Device Code form: tap "Reject Device" (FLOW_BUTTON at index 2).
        (node.collectors[2] as? FlowCollector)?.value = "click"
        // The reject call returns an ErrorNode (server responds 400 "Device Authorization Rejected").
        val rejectionResult = node.next()
        assertIs<ErrorNode>(rejectionResult)

        // Requesting side: the next poll after rejection returns access_denied.
        val statuses = deviceFlowJob.await()
        assertIs<DeviceFlowStatus.AccessDenied>(statuses.last())

        // No token should be stored after a rejection.
        assertNull(client.user())
    }

    /**
     * TC-04: Device code expires without approval — flow emits Expired.
     *
     * Starts the device flow without approving on any second device and waits for the
     * polling loop to exhaust the device-code lifetime. The final emission must be
     * [DeviceFlowStatus.Expired] and no token should be stored.
     *
     * Verifies:
     * - The first emission is [DeviceFlowStatus.Started].
     * - At least one [DeviceFlowStatus.Polling] emission is present.
     * - The terminal emission is [DeviceFlowStatus.Expired].
     * - [OidcDeviceClient.user] returns null — no token was stored.
     *
     * Timeout is set well above the device-code lifetime on this tenant (~30 s) plus the
     * maximum remaining poll interval, to avoid flakiness without waiting indefinitely.
     */
    // Disabled: this test waits for the full device-code lifetime (~30 s on this tenant) to elapse
    // before asserting Expired, which makes it too slow for routine CI runs. Enable it explicitly
    // when verifying expiry behaviour (e.g. after changes to the polling loop or token storage).
    @Ignore("Slow: waits for full device-code expiry (~30 s). Enable manually when testing expiry behaviour.")
    @Test(timeout = 90_000)
    fun deviceCodeExpiresWithoutApproval(): Unit = runBlocking {
        val statuses = client.deviceAuthorization().toList()

        assertIs<DeviceFlowStatus.Started>(statuses.first())
        assertTrue(
            statuses.filterIsInstance<DeviceFlowStatus.Polling>().isNotEmpty(),
            "Expected at least one Polling emission before expiry"
        )
        assertIs<DeviceFlowStatus.Expired>(statuses.last())
        assertNull(client.user())
    }
}
