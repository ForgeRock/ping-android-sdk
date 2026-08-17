/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.pingidentity.journey.Constants.TRANSACTION
import com.pingidentity.journey.module.Session
import com.pingidentity.network.ktor.KtorHttpClient
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.Node
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.Workflow
import com.pingidentity.storage.MemoryStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class JourneyBackchannelTest {

    private val mockContext: Context = mockk()
    private lateinit var mockEngine: MockEngine

    // A valid backchannel URI whose host matches the serverUrl used in test journeys ("localhost").
    private val validUri =
        "https://localhost/am/UI/Login?realm=%2Falpha&authIndexType=transaction&authIndexValue=abc-123".toUri()

    @BeforeTest
    fun setUp() {
        CallbackInitializer().create(mockContext)

        mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/am/json/realms/root/authenticate" -> {
                    respond(
                        authenticate(),
                        HttpStatusCode.OK,
                        authenticateHeader
                    )
                }
                else -> {
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        mockEngine.close()
    }

    /**
     * Asserts the node is a [FailureNode] carrying [expectedCauseMessage] and that no network call
     * was made. Checking the cause message keeps each test pinned to the validation branch it is
     * named for, rather than passing on whichever guard happens to reject the URI first.
     */
    private fun assertRejected(node: Node, expectedCauseMessage: String) {
        val failure = assertIs<FailureNode>(node, "Expected FailureNode but got $node")
        assertEquals(expectedCauseMessage, failure.cause.message, "Rejected on the wrong branch")
        assertEquals(
            0,
            mockEngine.requestHistory.size,
            "Expected no network call but got ${mockEngine.requestHistory.size}"
        )
    }

    // ---------------------------------------------------------------------------
    // Task 2.1 — Cases 1–5
    // ---------------------------------------------------------------------------

    // Case 1: Happy path — ContinueNode
    @Test
    fun `backchannel start happy path returns ContinueNode`() = runTest {
        val journey = Journey {
            serverUrl = "http://localhost/am"
            realm = "root"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = validUri)

        assertTrue(node is ContinueNode, "Expected ContinueNode but got $node")
        assertEquals(1, mockEngine.requestHistory.size, "Expected exactly 1 network request")

        val request = mockEngine.requestHistory[0]
        assertTrue(
            request.url.encodedPath.endsWith("/json/realms/root/authenticate"),
            "Expected path to end with /json/realms/root/authenticate but was ${request.url.encodedPath}"
        )
        assertContains(request.url.encodedQuery, "authIndexType=$TRANSACTION")
        assertContains(request.url.encodedQuery, "authIndexValue=abc-123")
    }

    // Case 2: Happy path — SuccessNode
    @Test
    fun `backchannel start returns SuccessNode when server responds with session`() = runTest {
        val successEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/am/json/realms/root/authenticate" -> {
                    respond(
                        sessionResponse(),
                        HttpStatusCode.OK,
                        authenticateHeader
                    )
                }
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val journey = Journey {
            serverUrl = "http://localhost/am"
            realm = "root"
            httpClient = KtorHttpClient(HttpClient(successEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = validUri)

        assertTrue(node is SuccessNode, "Expected SuccessNode but got $node")
        assertEquals("Dummy Session Token", node.session.value)

        successEngine.close()
    }

    // Case 3: Invalid URI — missing authIndexType
    @Test
    fun `backchannel start returns FailureNode when authIndexType is missing`() = runTest {
        val uri = "https://localhost/am/UI/Login?authIndexValue=abc-123".toUri()

        val journey = Journey {
            serverUrl = "http://localhost/am"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = uri)

        assertRejected(node, "Missing authIndexType or authIndexValue")
    }

    // Case 4: Invalid URI — missing authIndexValue
    @Test
    fun `backchannel start returns FailureNode when authIndexValue is missing`() = runTest {
        val uri = "https://localhost/am/UI/Login?authIndexType=transaction".toUri()

        val journey = Journey {
            serverUrl = "http://localhost/am"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = uri)

        assertRejected(node, "Missing authIndexType or authIndexValue")
    }

    // Case 5: Opaque URI — getQueryParameter throws UnsupportedOperationException
    @Test
    fun `backchannel start returns FailureNode for opaque non-hierarchical URI`() = runTest {
        // "mailto:" is an opaque URI, so it is rejected by the isHierarchical guard in
        // start(backchannelUri) before getQueryParameter is ever reached.
        val opaqueUri = Uri.parse("mailto:user@example.com")

        val journey = Journey {
            serverUrl = "http://localhost/am"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = opaqueUri)

        assertRejected(node, "Backchannel URI is not hierarchical")
    }

    // ---------------------------------------------------------------------------
    // Task 2.2 — Cases 6–9 and Android-specific robustness cases 10–11
    // ---------------------------------------------------------------------------

    // Case 6: JourneyConfig absent — Workflow without JourneyConfig
    @Test
    fun `backchannel start returns FailureNode when workflow has no JourneyConfig`() = runTest {
        // Build a raw Workflow using WorkflowConfig instead of JourneyConfig.
        // The start(backchannelUri) extension checks config !is JourneyConfig and returns FailureNode.
        val rawWorkflow = Workflow {
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
        }

        val node = rawWorkflow.start(backchannelUri = validUri)

        assertRejected(node, "JourneyConfig missing")
    }

    // Case 7: noSession = true
    @Test
    fun `backchannel start with noSession=true includes noSession in request URL`() = runTest {
        val journey = Journey {
            serverUrl = "http://localhost/am"
            realm = "root"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = validUri) {
            noSession = true
        }

        assertTrue(node is ContinueNode, "Expected ContinueNode but got $node")
        val request = mockEngine.requestHistory[0]
        assertContains(request.url.encodedQuery, "noSession=true")
    }

    // Case 8: forceAuth = true
    @Test
    fun `backchannel start with forceAuth=true includes ForceAuth in request URL`() = runTest {
        val journey = Journey {
            serverUrl = "http://localhost/am"
            realm = "root"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = validUri) {
            forceAuth = true
        }

        assertTrue(node is ContinueNode, "Expected ContinueNode but got $node")
        val request = mockEngine.requestHistory[0]
        assertContains(request.url.encodedQuery, "ForceAuth=true")
    }

    // Case 9: AM 4xx (expired transaction) → ErrorNode
    @Test
    fun `backchannel start returns ErrorNode when server responds with 401 Unauthorized`() = runTest {
        val expiredEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(
                    """{"code":401,"reason":"Unauthorized","message":"Transaction expired"}"""
                ),
                status = HttpStatusCode.Unauthorized,
            )
        }

        val journey = Journey {
            serverUrl = "http://localhost/am"
            realm = "root"
            httpClient = KtorHttpClient(HttpClient(expiredEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = validUri)

        assertTrue(node is ErrorNode, "Expected ErrorNode but got $node")
        assertEquals("Transaction expired", node.message)

        expiredEngine.close()
    }

    // Case 10: Empty authIndexType string
    @Test
    fun `backchannel start returns FailureNode when authIndexType is empty string`() = runTest {
        val uri = "https://localhost/am/UI/Login?authIndexType=&authIndexValue=abc-123".toUri()

        val journey = Journey {
            serverUrl = "http://localhost/am"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = uri)

        assertRejected(node, "Missing authIndexType or authIndexValue")
    }

    // Case 10b: Whitespace-only authIndexType — isNullOrBlank must reject, not just isNullOrEmpty
    @Test
    fun `backchannel start returns FailureNode when authIndexType is whitespace only`() = runTest {
        // "%20" decodes to a single space, so getQueryParameter returns " ".
        val uri = "https://localhost/am/UI/Login?authIndexType=%20&authIndexValue=abc-123".toUri()

        val journey = Journey {
            serverUrl = "http://localhost/am"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = uri)

        assertRejected(node, "Missing authIndexType or authIndexValue")
    }

    // Case 10c: Whitespace-only authIndexValue — an all-blank transaction id is not a valid id
    @Test
    fun `backchannel start returns FailureNode when authIndexValue is whitespace only`() = runTest {
        val uri =
            "https://localhost/am/UI/Login?authIndexType=transaction&authIndexValue=%20%20".toUri()

        val journey = Journey {
            serverUrl = "http://localhost/am"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = uri)

        assertRejected(node, "Missing authIndexType or authIndexValue")
    }

    // Case 10d: serverUrl assigned but has no host — must not compare against a null host
    @Test
    fun `backchannel start returns FailureNode when serverUrl has no host`() = runTest {
        val journey = Journey {
            // No scheme or authority, so Uri.parse(serverUrl).host is null.
            serverUrl = "localhost/am"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = validUri)

        assertRejected(node, "JourneyConfig.serverUrl has no host")
    }

    // Case 11a: Host mismatch — URI host differs from serverUrl host
    @Test
    fun `backchannel start returns FailureNode when URI host does not match serverUrl`() = runTest {
        val maliciousUri =
            "https://attacker.example.com/am/UI/Login?authIndexType=transaction&authIndexValue=abc-123".toUri()

        val journey = Journey {
            serverUrl = "http://localhost/am"
            realm = "root"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = maliciousUri)

        assertRejected(node, "Backchannel URI host does not match configured serverUrl")
    }

    // Case 11b: serverUrl never assigned — lateinit must not leak UninitializedPropertyAccessException
    @Test
    fun `backchannel start returns FailureNode when serverUrl is not configured`() = runTest {
        val journey = Journey {
            // serverUrl deliberately left unset
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = validUri)

        assertRejected(node, "JourneyConfig.serverUrl is not configured")
    }

    // Case 11c: Hostnames are case-insensitive (RFC 3986 section 3.2.2)
    @Test
    fun `backchannel start accepts URI host that differs from serverUrl only by case`() = runTest {
        val uppercaseHostUri =
            "https://LOCALHOST/am/UI/Login?authIndexType=transaction&authIndexValue=abc-123".toUri()

        val journey = Journey {
            serverUrl = "http://localhost/am"
            realm = "root"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = uppercaseHostUri)

        assertTrue(node is ContinueNode, "Expected ContinueNode but got $node")
        assertContains(mockEngine.requestHistory[0].url.encodedQuery, "authIndexValue=abc-123")
    }

    // Case 11d: Non-transactional authIndexType is rejected — this overload is transaction-only
    @Test
    fun `backchannel start returns FailureNode when authIndexType is not transaction`() = runTest {
        val serviceUri =
            "https://localhost/am/UI/Login?authIndexType=service&authIndexValue=Login".toUri()

        val journey = Journey {
            serverUrl = "http://localhost/am"
            realm = "root"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = serviceUri)

        assertRejected(node, "Unsupported authIndexType 'service', expected 'transaction'")
    }

    // Case 11: Realm-from-config safety — URI realm is ignored, config realm is used
    @Test
    fun `backchannel start uses config realm and ignores realm in URI`() = runTest {
        val bravoUri =
            "https://localhost/am/UI/Login?realm=%2Fbravo&authIndexType=transaction&authIndexValue=abc-123".toUri()

        val journey = Journey {
            serverUrl = "http://localhost/am"
            realm = "root"
            httpClient = KtorHttpClient(HttpClient(mockEngine) { followRedirects = false })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start(backchannelUri = bravoUri)

        assertTrue(node is ContinueNode, "Expected ContinueNode but got $node")
        val request = mockEngine.requestHistory[0]

        // Must use config realm "root"
        assertContains(
            request.url.encodedPath,
            "/json/realms/root/authenticate",
            message = "Expected config realm 'root' in path but was ${request.url.encodedPath}"
        )
        // Must NOT contain "bravo"
        assertFalse(
            request.url.encodedPath.contains("bravo"),
            "Request path must not contain URI realm 'bravo' but was ${request.url.encodedPath}"
        )
    }
}
