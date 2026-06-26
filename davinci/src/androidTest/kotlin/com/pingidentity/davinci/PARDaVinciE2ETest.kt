/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import android.net.Uri
import androidx.test.filters.SmallTest
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.plugin.DaVinci as DaVinciFlow
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.network.ktor.HttpClient
import com.pingidentity.oidc.exception.AuthorizeException
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.FailureNode
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * E2E tests for PAR (Pushed Authorization Request, RFC 9126) in the DaVinci / PingOne flow.
 *
 * Two scenarios are covered:
 *  1. par = false (default) — standard OIDC flow; all authorize params appear inline in the
 *     GET /authorize URL (sent as the DaVinci start request), no POST to /par is made.
 *  2. par = true             — PAR flow; params are POSTed to /par first, then only
 *     client_id + request_uri appear in the GET /authorize URL.
 *
 * Unlike the centralized-login tests, DaVinci does NOT use BrowserLauncher. The authorize URL is
 * sent as a regular HTTP GET that returns JSON (DaVinci form). Wire-shape is verified by recording
 * every outgoing HTTP request through an onRequest interceptor on the shared HttpClient.
 *
 * Test server: auth.pingone.ca (PingOne)
 */
@SmallTest
class PARDaVinciE2ETest {

    private val recordedRequests = mutableListOf<RecordedRequest>()

    companion object {
        private const val CLIENT_ID = "a6859a12-5e6e-4f64-96bb-cc8577706bee"
        private const val DISCOVERY_ENDPOINT =
            "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration"
        private const val REDIRECT_URI = "org.forgerock.demo://oauth2redirect"
        private const val ACR_VALUES = "4ada23c8f9ae6201ec8116ffbf004595"
    }

    @Before
    fun setup() {
        recordedRequests.clear()
    }

    // ---------------------------------------------------------------------------
    // par = false — standard flow
    // ---------------------------------------------------------------------------

    @Test
    fun daVinciWithoutPAR_noParPostMade() = runTest {
        buildDaVinci(par = false).start()

        val parCalls = recordedRequests.filter { it.url.contains("/par") && it.method == "POST" }
        assertTrue("Expected zero /par POST calls when par=false", parCalls.isEmpty())
    }

    @Test
    fun daVinciWithoutPAR_authorizeUrlContainsAllParams() = runTest {
        buildDaVinci(par = false).start()

        val authorizeRequest = recordedRequests.firstOrNull { it.url.contains("/authorize") }
        assertNotNull("Expected an /authorize request when par=false", authorizeRequest)
        val queryParams = Uri.parse(requireNotNull(authorizeRequest).url).queryParameterNames

        for (expected in listOf(
            "client_id", "response_type", "scope",
            "redirect_uri", "code_challenge", "code_challenge_method",
        )) {
            assertTrue(
                "Expected '$expected' in /authorize URL when par=false",
                queryParams.contains(expected),
            )
        }
        assertFalse(
            "Did not expect 'request_uri' in /authorize URL when par=false",
            queryParams.contains("request_uri"),
        )
    }

    @Test
    fun daVinciWithoutPAR_startReturnsContinueNode() = runTest {
        val node = buildDaVinci(par = false).start()
        assertTrue(
            "Expected ContinueNode (DaVinci first form) when par=false, got ${node::class.simpleName}",
            node is ContinueNode,
        )
    }

    // ---------------------------------------------------------------------------
    // par = true — PAR flow
    // ---------------------------------------------------------------------------

    @Test
    fun daVinciWithPAR_exactlyOneParPostMade() = runTest {
        buildDaVinci(par = true).start()

        val parCalls = recordedRequests.filter { it.url.contains("/par") && it.method == "POST" }
        assertEquals("Expected exactly one POST to /par when par=true", 1, parCalls.size)
    }

    @Test
    fun daVinciWithPAR_authorizeUrlContainsOnlyClientIdAndRequestUri() = runTest {
        buildDaVinci(par = true).start()

        val authorizeRequest = recordedRequests.firstOrNull { it.url.contains("/authorize") }
        assertNotNull("Expected an /authorize request after PAR POST", authorizeRequest)
        val queryParams = Uri.parse(requireNotNull(authorizeRequest).url).queryParameterNames

        assertTrue(
            "Expected 'client_id' in /authorize URL after PAR",
            queryParams.contains("client_id"),
        )
        assertTrue(
            "Expected 'request_uri' in /authorize URL after PAR",
            queryParams.contains("request_uri"),
        )

        // Full OIDC params must be absent — they were pushed to /par, not the URL
        for (forbidden in listOf(
            "scope", "redirect_uri", "code_challenge",
            "code_challenge_method", "response_type",
        )) {
            assertFalse(
                "Did not expect '$forbidden' in /authorize URL when par=true",
                queryParams.contains(forbidden),
            )
        }
    }

    @Test
    fun daVinciWithPAR_requestUriPropagatedToAuthorize() = runTest {
        buildDaVinci(par = true).start()

        val authorizeRequest = requireNotNull(recordedRequests.firstOrNull { it.url.contains("/authorize") })
        val requestUri = Uri.parse(authorizeRequest.url).getQueryParameter("request_uri")
        assertNotNull("Expected non-null request_uri in /authorize URL", requestUri)
        assertTrue("Expected non-empty request_uri", requireNotNull(requestUri).isNotEmpty())
    }

    @Test
    fun daVinciWithPAR_startReturnsContinueNode() = runTest {
        val node = buildDaVinci(par = true).start()
        assertTrue(
            "Expected ContinueNode (DaVinci first form) after PAR flow, got ${node::class.simpleName}",
            node is ContinueNode,
        )
    }

    // ---------------------------------------------------------------------------
    // par = true — PAR failure scenarios
    // ---------------------------------------------------------------------------

    @Test
    fun daVinciWithPAR_wrongClientId_returnsFailureNode() = runTest {
        val node = buildDaVinci(par = true, clientId = "invalid-client-id-xyz").start()

        assertTrue(
            "Expected FailureNode when client_id is invalid, got ${node::class.simpleName}",
            node is FailureNode,
        )
        assertTrue(
            "Expected AuthorizeException for rejected PAR, got ${(node as FailureNode).cause::class.simpleName}",
            node.cause is AuthorizeException,
        )
    }

    @Test
    fun daVinciWithPAR_wrongClientId_noAuthorizeRequestMade() = runTest {
        buildDaVinci(par = true, clientId = "invalid-client-id-xyz").start()

        val authorizeCalls = recordedRequests.filter { it.url.contains("/authorize") }
        assertTrue(
            "Expected no /authorize request when PAR is rejected due to invalid client_id",
            authorizeCalls.isEmpty(),
        )
    }

    @Test
    fun daVinciWithPAR_wrongRedirectUri_returnsFailureNode() = runTest {
        val node = buildDaVinci(par = true, redirectUri = "https://invalid.example.com/callback").start()

        assertTrue(
            "Expected FailureNode when redirect_uri is not registered, got ${node::class.simpleName}",
            node is FailureNode,
        )
        assertTrue(
            "Expected AuthorizeException for rejected PAR, got ${(node as FailureNode).cause::class.simpleName}",
            node.cause is AuthorizeException,
        )
    }

    @Test
    fun daVinciWithPAR_wrongRedirectUri_noAuthorizeRequestMade() = runTest {
        buildDaVinci(par = true, redirectUri = "https://invalid.example.com/callback").start()

        val authorizeCalls = recordedRequests.filter { it.url.contains("/authorize") }
        assertTrue(
            "Expected no /authorize request when PAR is rejected due to unregistered redirect_uri",
            authorizeCalls.isEmpty(),
        )
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Builds a [DaVinciFlow] pointed at the PingOne test server with an onRequest interceptor
     * that records every outgoing HTTP request into [recordedRequests].
     *
     * [httpClient] is set at the workflow level (not inside [Oidc]) so that it is shared by
     * every module in the pipeline — including the PAR POST made by the Oidc module's start
     * hook — ensuring all requests are captured.
     */
    private fun buildDaVinci(
        par: Boolean,
        clientId: String = CLIENT_ID,
        redirectUri: String = REDIRECT_URI,
    ): DaVinciFlow {
        val sharedHttpClient = HttpClient {
            logger = Logger.STANDARD
            onRequest {
                recordedRequests.add(RecordedRequest(url = url, method = method()))
            }
        }

        return DaVinci {
            logger = Logger.STANDARD
            this.httpClient = sharedHttpClient
            module(Oidc) {
                this.clientId = clientId
                this.redirectUri = redirectUri
                scopes = mutableSetOf("openid", "profile", "email")
                discoveryEndpoint = DISCOVERY_ENDPOINT
                acrValues = ACR_VALUES
                this.par = par
            }
        }
    }
}

// ---------------------------------------------------------------------------
// RecordedRequest — immutable snapshot of a dispatched HTTP request
// ---------------------------------------------------------------------------

private data class RecordedRequest(
    val url: String,
    val method: String,
)
