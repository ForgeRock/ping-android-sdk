/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import android.net.Uri
import androidx.test.filters.SmallTest
import com.pingidentity.browser.BrowserCanceledException
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.network.ktor.HttpClient
import com.pingidentity.network.ktor.KtorHttpRequest
import com.pingidentity.oidc.OidcWebClient
import com.pingidentity.oidc.exception.AuthorizeException
import com.pingidentity.oidc.module.Oidc
import io.ktor.client.request.forms.FormDataContent
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.URL

/**
 * E2E tests for PAR (Pushed Authorization Request) via centralized (browser-based) login
 * against PingOne.
 *
 * Mirrors [PARCentralizedLoginE2ETest] from the journey module but targets the PingOne
 * environment used by the DaVinci tests.
 *
 * [BrowserLauncher] is mocked to cancel immediately after capturing the authorize URL —
 * this lets us observe the wire shape (PAR POST body, authorize URL params) without ever
 * opening a browser. The PAR POST completes against the real server before
 * [BrowserLauncher.launch] is called, so all wire-level assertions are valid.
 *
 * Two scenarios are covered:
 *   1. par = false — standard flow: all authorize params appear inline in the GET /authorize URL.
 *   2. par = true  — PAR flow: params are POSTed to /par first; GET /authorize carries only
 *      client_id + request_uri.
 */
@SmallTest
class PARCentralizedLoginDaVinciE2ETest {

    private val recordedRequests = mutableListOf<CentralizedRecordedRequest>()

    @Before
    fun setupMocks() {
        recordedRequests.clear()
        mockkObject(BrowserLauncher)
    }

    @After
    fun tearDownMocks() {
        unmockkObject(BrowserLauncher)
    }

    // -------------------------------------------------------------------------
    // par = false — standard centralized login
    // -------------------------------------------------------------------------

    @Test
    fun centralizedLoginWithoutPAR_noParPostMade() = runTest {
        coEvery { BrowserLauncher.launch(any<URL>(), any<Uri>()) } returns
            Result.failure(BrowserCanceledException())
        buildWebClient(par = false).authorize()

        val parCalls = recordedRequests.filter { it.url.contains("/par") && it.method == "POST" }
        assertTrue("Expected zero /par POST calls when par=false", parCalls.isEmpty())
    }

    @Test
    fun centralizedLoginWithoutPAR_authorizeUrlContainsAllParams() = runTest {
        val capturedUrl = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(capturedUrl), any<Uri>()) } returns
            Result.failure(BrowserCanceledException())
        buildWebClient(par = false).authorize()

        assertTrue("Expected browser to be launched", capturedUrl.isCaptured)
        val query = capturedUrl.captured.query ?: ""
        for (param in listOf(
            "client_id", "response_type", "scope",
            "redirect_uri", "code_challenge", "code_challenge_method",
        )) {
            assertTrue("Expected '$param' in /authorize URL when par=false", query.contains(param))
        }
        assertFalse(
            "Did not expect 'request_uri' in /authorize URL when par=false",
            query.contains("request_uri"),
        )
    }

    @Test
    fun centralizedLoginWithoutPAR_browserLaunchedWithAuthorizeUrl() = runTest {
        val capturedUrl = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(capturedUrl), any<Uri>()) } returns
            Result.failure(BrowserCanceledException())
        val result = buildWebClient(par = false).authorize()

        assertTrue("Expected browser to be launched when par=false", capturedUrl.isCaptured)
        assertTrue("Expected authorize() to fail when browser is cancelled", result.isFailure)
        assertTrue(
            "Expected BrowserCanceledException, got ${result.exceptionOrNull()?.javaClass?.simpleName}",
            result.exceptionOrNull() is BrowserCanceledException,
        )
    }

    // -------------------------------------------------------------------------
    // par = true — PAR-backed centralized login
    // -------------------------------------------------------------------------

    @Test
    fun centralizedLoginWithPAR_exactlyOneParPostMade() = runTest {
        coEvery { BrowserLauncher.launch(any<URL>(), any<Uri>()) } returns
            Result.failure(BrowserCanceledException())
        buildWebClient(par = true).authorize()

        val parCalls = recordedRequests.filter { it.url.contains("/par") && it.method == "POST" }
        assertEquals("Expected exactly one POST to /par when par=true", 1, parCalls.size)
    }

    @Test
    fun centralizedLoginWithPAR_parPostBodyContainsRequiredFields() = runTest {
        coEvery { BrowserLauncher.launch(any<URL>(), any<Uri>()) } returns
            Result.failure(BrowserCanceledException())
        buildWebClient(par = true).authorize()

        val parRequest = recordedRequests.first { it.url.contains("/par") && it.method == "POST" }
        val form = parRequest.formFields

        for (field in listOf(
            "client_id", "response_type", "scope",
            "redirect_uri", "code_challenge", "code_challenge_method",
        )) {
            assertNotNull("PAR POST body missing required field '$field'", form[field])
        }
        assertEquals("Expected correct client_id in PAR body", DaVinciTestConfig.parClientId, form["client_id"])
        assertEquals("Expected response_type=code in PAR body", "code", form["response_type"])
    }

    @Test
    fun centralizedLoginWithPAR_authorizeUrlContainsOnlyClientIdAndRequestUri() = runTest {
        val capturedUrl = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(capturedUrl), any<Uri>()) } returns
            Result.failure(BrowserCanceledException())
        buildWebClient(par = true).authorize()

        assertTrue("Expected browser to be launched after PAR POST", capturedUrl.isCaptured)
        val query = capturedUrl.captured.query ?: ""
        assertTrue("Expected 'client_id' in /authorize URL after PAR", query.contains("client_id"))
        assertTrue("Expected 'request_uri' in /authorize URL after PAR", query.contains("request_uri"))
        for (forbidden in listOf("scope=", "redirect_uri=", "code_challenge=", "response_type=")) {
            assertFalse(
                "Did not expect '$forbidden' in /authorize URL when par=true",
                query.contains(forbidden),
            )
        }
    }

    @Test
    fun centralizedLoginWithPAR_requestUriIsNonEmpty() = runTest {
        val capturedUrl = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(capturedUrl), any<Uri>()) } returns
            Result.failure(BrowserCanceledException())
        buildWebClient(par = true).authorize()

        assertTrue("Expected browser to be launched", capturedUrl.isCaptured)
        val requestUri = Uri.parse(capturedUrl.captured.toString()).getQueryParameter("request_uri")
        assertNotNull("Expected non-null request_uri in /authorize URL", requestUri)
        assertTrue("Expected non-empty request_uri", requireNotNull(requestUri).isNotEmpty())
    }

    @Test
    fun centralizedLoginWithPAR_browserLaunchedAfterSuccessfulParPost() = runTest {
        val capturedUrl = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(capturedUrl), any<Uri>()) } returns
            Result.failure(BrowserCanceledException())
        val result = buildWebClient(par = true).authorize()

        assertTrue("Expected browser to be launched after successful PAR POST", capturedUrl.isCaptured)
        assertTrue("Expected failure due to browser cancellation (not a PAR error)", result.isFailure)
        assertTrue(
            "Expected BrowserCanceledException, got ${result.exceptionOrNull()?.javaClass?.simpleName}",
            result.exceptionOrNull() is BrowserCanceledException,
        )
    }

    // -------------------------------------------------------------------------
    // par = true — PAR failure scenarios
    // -------------------------------------------------------------------------

    @Test
    fun centralizedLoginWithPAR_wrongClientId_returnsFailureWithAuthorizeException() = runTest {
        val result = buildWebClient(par = true, clientId = "invalid-client-id-xyz").authorize()

        assertTrue("Expected authorize() to fail when client_id is invalid", result.isFailure)
        assertTrue(
            "Expected AuthorizeException for rejected PAR, got ${result.exceptionOrNull()?.javaClass?.simpleName}",
            result.exceptionOrNull() is AuthorizeException,
        )
    }

    @Test
    fun centralizedLoginWithPAR_wrongClientId_browserNeverLaunched() = runTest {
        val capturedUrl = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(capturedUrl), any<Uri>()) } returns
            Result.failure(BrowserCanceledException())
        buildWebClient(par = true, clientId = "invalid-client-id-xyz").authorize()

        assertFalse(
            "Expected browser NOT to be launched when PAR is rejected due to invalid client_id",
            capturedUrl.isCaptured,
        )
    }

    @Test
    fun centralizedLoginWithPAR_wrongRedirectUri_returnsFailureWithAuthorizeException() = runTest {
        val result = buildWebClient(par = true, redirectUri = "https://invalid.example.com/callback").authorize()

        assertTrue("Expected authorize() to fail when redirect_uri is not registered", result.isFailure)
        assertTrue(
            "Expected AuthorizeException for rejected PAR, got ${result.exceptionOrNull()?.javaClass?.simpleName}",
            result.exceptionOrNull() is AuthorizeException,
        )
    }

    @Test
    fun centralizedLoginWithPAR_wrongRedirectUri_browserNeverLaunched() = runTest {
        val capturedUrl = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(capturedUrl), any<Uri>()) } returns
            Result.failure(BrowserCanceledException())
        buildWebClient(par = true, redirectUri = "https://invalid.example.com/callback").authorize()

        assertFalse(
            "Expected browser NOT to be launched when PAR is rejected due to unregistered redirect_uri",
            capturedUrl.isCaptured,
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds an [OidcWebClient] pointed at the PingOne test server. All outgoing HTTP requests
     * are recorded into [recordedRequests] via an onRequest interceptor, including the PAR POST
     * body fields for form-encoded requests.
     */
    private fun buildWebClient(
        par: Boolean,
        clientId: String = DaVinciTestConfig.parClientId,
        redirectUri: String = DaVinciTestConfig.parRedirectUri,
    ): OidcWebClient {
        val sharedHttpClient = HttpClient {
            logger = Logger.STANDARD
            onRequest {
                val formFields: Map<String, String> = try {
                    // KtorHttpRequest.builder is internal to foundation:network, but ktor.client.core
                    // is on the androidTest classpath so the cast is safe here. The outer try/catch
                    // handles any non-form-encoded requests (GET, JSON body, etc.) gracefully.
                    val formData = (this as? KtorHttpRequest)?.builder?.body as? FormDataContent
                    formData?.formData?.entries()
                        ?.associate { (k, values) -> k to (values.firstOrNull() ?: "") }
                        ?: emptyMap()
                } catch (_: Exception) {
                    emptyMap()
                }
                recordedRequests.add(
                    CentralizedRecordedRequest(url = url, method = method(), formFields = formFields),
                )
            }
        }

        return OidcWebClient {
            httpClient = sharedHttpClient
            logger = Logger.STANDARD
            module(Oidc) {
                this.clientId = clientId
                this.redirectUri = redirectUri
                scopes = mutableSetOf("openid", "profile", "email")
                discoveryEndpoint = DaVinciTestConfig.parDiscoveryEndpoint
                acrValues = DaVinciTestConfig.parAcrValues
                this.par = par
            }
        }
    }
}

private data class CentralizedRecordedRequest(
    val url: String,
    val method: String,
    val formFields: Map<String, String>,
)
