/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import androidx.core.net.toUri
import androidx.test.filters.SmallTest
import com.pingidentity.journey.module.Oidc
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.network.ktor.HttpClient
import com.pingidentity.network.ktor.KtorHttpRequest
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.utils.Result
import io.ktor.client.request.forms.FormDataContent
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * E2E tests for PAR (Pushed Authorization Request, RFC 9126) in the Journey / AIC flow.
 *
 * Two scenarios are covered:
 *  1. par = false (default) — standard OIDC flow; all authorize params appear inline in the
 *     GET /authorize URL, no POST to /par is made.
 *  2. par = true             — PAR flow; params are POSTed to /par first, then only
 *     client_id + request_uri appear in the GET /authorize URL.
 *
 * Wire-shape is verified by recording every outgoing HTTP request through a Ktor
 * onRequest interceptor that captures the URL, method, and form fields of each request.
 *
 * Test server: openam-sdks.forgeblocks.com (AIC)
 */
@SmallTest
class PARJourneyE2ETest : BaseJourneyTest() {

    private val recordedRequests = mutableListOf<RecordedRequest>()

    @Before
    fun setupTree() = runTest {
        tree = "Login"
        recordedRequests.clear()
    }

    // ---------------------------------------------------------------------------
    // par = false — standard flow
    // ---------------------------------------------------------------------------

    @Test
    fun journeyLoginWithoutPAR_noParCallMade() = runTest {
        val journey = buildJourney(par = false)
        loginSuccessfully(journey)

        val parCalls = recordedRequests.filter { it.url.contains("/par") && it.method == "POST" }
        assertTrue("Expected zero /par POST calls when par=false", parCalls.isEmpty())
    }

    @Test
    fun journeyLoginWithoutPAR_authorizeUrlContainsAllParams() = runTest {
        val journey = buildJourney(par = false)
        loginSuccessfully(journey)

        val authorizeRequest = recordedRequests.firstOrNull { it.url.contains("/authorize") }
        assertNotNull("Expected an /authorize request when par=false", authorizeRequest)
        val queryParams = requireNotNull(authorizeRequest).url.toUri().queryParameterNames

        for (expected in listOf(
            "client_id", "response_type", "scope",
            "redirect_uri", "code_challenge", "code_challenge_method"
        )) {
            assertTrue(
                "Expected '$expected' in /authorize URL when par=false",
                queryParams.contains(expected)
            )
        }

        assertTrue(
            "Did not expect 'request_uri' in /authorize URL when par=false",
            !queryParams.contains("request_uri")
        )
    }

    @Test
    fun journeyLoginWithoutPAR_tokenObtainedSuccessfully() = runTest {
        val journey = buildJourney(par = false)
        loginSuccessfully(journey)

        val tokenResult = journey.user()?.token()
        assertTrue("Expected successful token after standard flow", tokenResult is Result.Success)
        assertTrue(
            "Expected non-empty access token",
            (tokenResult as Result.Success).value.accessToken.isNotEmpty()
        )
    }

    // ---------------------------------------------------------------------------
    // par = true — PAR flow
    // ---------------------------------------------------------------------------

    @Test
    fun journeyLoginWithPAR_exactlyOneParPostMade() = runTest {
        val journey = buildJourney(par = true)
        loginSuccessfully(journey)

        val parCalls = recordedRequests.filter { it.url.contains("/par") && it.method == "POST" }
        assertEquals("Expected exactly one POST to /par when par=true", 1, parCalls.size)
    }

    @Test
    fun journeyLoginWithPAR_parPostBodyContainsRequiredFields() = runTest {
        val journey = buildJourney(par = true)
        loginSuccessfully(journey)

        val parRequest = recordedRequests.first { it.url.contains("/par") && it.method == "POST" }
        val formFields = parRequest.formFields

        for (field in listOf(
            "client_id", "response_type", "scope",
            "redirect_uri", "code_challenge", "code_challenge_method"
        )) {
            assertNotNull("PAR POST body missing required field '$field'", formFields[field])
        }
        assertEquals("Expected correct client_id in PAR body", CLIENT_ID, formFields["client_id"])
        assertEquals("Expected response_type=code in PAR body", "code", formFields["response_type"])
    }

    @Test
    fun journeyLoginWithPAR_authorizeUrlContainsOnlyClientIdAndRequestUri() = runTest {
        val journey = buildJourney(par = true)
        loginSuccessfully(journey)

        val authorizeRequest = recordedRequests.firstOrNull { it.url.contains("/authorize") }
        assertNotNull("Expected an /authorize request after PAR POST", authorizeRequest)
        val queryParams = requireNotNull(authorizeRequest).url.toUri().queryParameterNames

        assertTrue(
            "Expected 'client_id' in /authorize URL after PAR",
            queryParams.contains("client_id")
        )
        assertTrue(
            "Expected 'request_uri' in /authorize URL after PAR",
            queryParams.contains("request_uri")
        )

        // Full OIDC params must be absent — they were pushed to /par, not the URL
        for (forbidden in listOf(
            "scope", "redirect_uri", "code_challenge",
            "code_challenge_method", "response_type"
        )) {
            assertTrue(
                "Did not expect '$forbidden' in /authorize URL when par=true",
                !queryParams.contains(forbidden)
            )
        }
    }

    @Test
    fun journeyLoginWithPAR_requestUriPropagatedToAuthorize() = runTest {
        val journey = buildJourney(par = true)
        loginSuccessfully(journey)

        val authorizeRequest = requireNotNull(recordedRequests.firstOrNull { it.url.contains("/authorize") })
        val requestUri = authorizeRequest.url.toUri().getQueryParameter("request_uri")
        assertNotNull("Expected non-null request_uri in /authorize URL", requestUri)
        assertTrue("Expected non-empty request_uri", requireNotNull(requestUri).isNotEmpty())
    }

    @Test
    fun journeyLoginWithPAR_tokenObtainedSuccessfully() = runTest {
        val journey = buildJourney(par = true)
        loginSuccessfully(journey)

        val tokenResult = journey.user()?.token()
        assertTrue("Expected successful token after PAR flow", tokenResult is Result.Success)
        assertTrue(
            "Expected non-empty access token after PAR",
            (tokenResult as Result.Success).value.accessToken.isNotEmpty()
        )
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Builds a [Journey] pointed at the AIC test server with an onRequest interceptor that
     * records every outgoing HTTP request into [recordedRequests].
     *
     * [httpClient] is set at the workflow level so it is shared across all modules — including
     * the Oidc module's start hook that performs the PAR POST — ensuring all requests are captured.
     */
    private fun buildJourney(par: Boolean): Journey {
        return Journey {
            logger = Logger.STANDARD
            serverUrl = SERVER_URL
            realm = REALM
            cookie = COOKIE
            httpClient = HttpClient {
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
                        RecordedRequest(
                            url = url,
                            method = method(),
                            formFields = formFields,
                        )
                    )
                }
            }
            module(Oidc) {
                clientId = CLIENT_ID
                redirectUri = REDIRECT_URI
                scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
                discoveryEndpoint = DISCOVERY_ENDPOINT
                this.par = par
            }
        }
    }

    private suspend fun loginSuccessfully(journey: Journey) {
        val node = journey.start(tree) as ContinueNode
        node.handleLoginCallbacks(USERNAME, PASSWORD)
        val result = node.next()
        assertTrue(
            "Expected SuccessNode after login, got ${result::class.simpleName}",
            result is SuccessNode
        )
        // Trigger the OIDC authorize redirect (and PAR POST if par=true).
        // In the Journey SDK, this happens lazily on the first token() call.
        journey.user()?.token()
    }
}

// ---------------------------------------------------------------------------
// RecordedRequest — immutable snapshot of a dispatched HTTP request
// ---------------------------------------------------------------------------

/**
 * Immutable snapshot of an outgoing HTTP request captured by the onRequest interceptor.
 *
 * @property url        Full request URL including query parameters.
 * @property method     HTTP method ("GET", "POST", etc.).
 * @property formFields Decoded form-urlencoded body fields, or empty map if not a form POST.
 */
data class RecordedRequest(
    val url: String,
    val method: String,
    val formFields: Map<String, String>,
)
