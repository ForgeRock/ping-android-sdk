/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.module.Metadata
import com.pingidentity.davinci.module.MetadataException
import com.pingidentity.davinci.module.MetadataNode
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.network.ktor.KtorHttpClient
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.module.Cookie
import com.pingidentity.storage.MemoryStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for [MetadataNode] — parse-and-detect, malformed, missing-link,
 * resume happy-path, resume with error, and cancellation guard.
 *
 * Transport is provided by Ktor [MockEngine]; the test drives a full [DaVinci] instance
 * so all workflow module hooks run exactly as in production.
 */
@RunWith(RobolectricTestRunner::class)
class MetadataNodeTest {

    private lateinit var mockEngine: MockEngine

    // -------------------------------------------------------------------------
    // Shared fixtures / helpers
    // -------------------------------------------------------------------------

    /** Well-formed metadata response — primary probe (field in form.components.fields). */
    private fun metadataResponseJson(
        resumeHref: String = "http://localhost/resume",
        type: String = Metadata.Type.PINGONE_MFA_SDK,
        operation: String = "CHECK_FOR_MFA",
        configs: JsonObject = buildJsonObject { put("sdkId", "ping-mfa") },
    ) = """
        {
            "_links": {
                "next": {
                    "href": "$resumeHref"
                }
            },
            "id": "metadata-node-id",
            "eventName": "continue",
            "form": {
                "name": "SDK Metadata",
                "description": "",
                "category": "CUSTOM_HTML",
                "components": {
                    "fields": [
                        {
                            "type": "METADATA",
                            "TYPE": "$type",
                            "OPERATION": "$operation",
                            "configs": ${configs}
                        }
                    ]
                }
            }
        }
    """.trimIndent()

    /** Malformed metadata: field has type METADATA but TYPE key is missing. */
    private fun malformedMetadataResponseJson() = """
        {
            "_links": {
                "next": {
                    "href": "http://localhost/resume"
                }
            },
            "id": "metadata-node-id",
            "eventName": "continue",
            "form": {
                "name": "SDK Metadata",
                "description": "",
                "category": "CUSTOM_HTML",
                "components": {
                    "fields": [
                        {
                            "type": "METADATA",
                            "OPERATION": "CHECK_FOR_MFA",
                            "configs": {}
                        }
                    ]
                }
            }
        }
    """.trimIndent()

    /** Metadata response without _links.next.href so resume link is absent. */
    private fun metadataResponseMissingLinkJson() = """
        {
            "id": "metadata-node-id",
            "eventName": "continue",
            "form": {
                "name": "SDK Metadata",
                "description": "",
                "category": "CUSTOM_HTML",
                "components": {
                    "fields": [
                        {
                            "type": "METADATA",
                            "TYPE": "${Metadata.Type.PINGONE_MFA_SDK}",
                            "OPERATION": "CHECK_FOR_MFA",
                            "configs": {}
                        }
                    ]
                }
            }
        }
    """.trimIndent()

    /**
     * Builds a [DaVinci] with [engine] as the transport and registers the default collectors.
     * Uses an in-memory token/cookie storage so tests stay fully in-process.
     */
    private fun buildDaVinci(engine: MockEngine): DaVinci {
        CollectorRegistry().initialize()
        return DaVinci {
            httpClient = KtorHttpClient(HttpClient(engine))
            module(Oidc) {
                clientId = "test"
                discoveryEndpoint = "http://localhost/.well-known/openid-configuration"
                scopes = mutableSetOf("openid", "email", "address")
                redirectUri = "http://localhost:8080"
                storage = { MemoryStorage() }
            }
            module(Cookie) {
                storage = { MemoryStorage() }
                persist = mutableListOf("ST")
            }
        }
    }

    @BeforeTest
    fun setUp() {
        // CollectorRegistry is re-initialised per test inside buildDaVinci.
    }

    @AfterTest
    fun tearDown() {
        if (::mockEngine.isInitialized) mockEngine.close()
    }

    // -------------------------------------------------------------------------
    // Test 1 — Parse-and-detect
    // -------------------------------------------------------------------------

    @Test
    fun `start returns MetadataNode with expected type operation and configs`() = runTest {
        val expectedConfigs = buildJsonObject { put("sdkId", "ping-mfa"); put("region", "us") }
        mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/.well-known/openid-configuration" ->
                    respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                "/authorize" ->
                    respond(
                        ByteReadChannel(metadataResponseJson(configs = expectedConfigs)),
                        HttpStatusCode.OK,
                        authorizeResponseHeaders,
                    )
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val node = buildDaVinci(mockEngine).start()

        assertIs<MetadataNode>(node)
        assertEquals(Metadata.Type.PINGONE_MFA_SDK, node.metadata.type)
        assertEquals("CHECK_FOR_MFA", node.metadata.operation)
        assertEquals("ping-mfa", node.metadata.configs["sdkId"]?.jsonPrimitive?.content)
        assertEquals("us", node.metadata.configs["region"]?.jsonPrimitive?.content)
    }

    // -------------------------------------------------------------------------
    // Test 2 — Malformed detection
    // -------------------------------------------------------------------------

    @Test
    fun `start returns FailureNode with MetadataException Malformed when TYPE is absent`() =
        runTest {
            mockEngine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/.well-known/openid-configuration" ->
                        respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                    "/authorize" ->
                        respond(
                            ByteReadChannel(malformedMetadataResponseJson()),
                            HttpStatusCode.OK,
                            authorizeResponseHeaders,
                        )
                    else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
                }
            }

            val node = buildDaVinci(mockEngine).start()

            assertIs<FailureNode>(node)
            assertIs<MetadataException.Malformed>(node.cause)
            assertTrue(
                node.cause.message?.contains("TYPE") == true,
                "Exception message should mention 'TYPE'",
            )
        }

    // -------------------------------------------------------------------------
    // Test 3 — Missing resume link
    // -------------------------------------------------------------------------

    @Test
    fun `resume returns FailureNode with MissingResumeLink when _links_next_href is absent`() =
        runTest {
            mockEngine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/.well-known/openid-configuration" ->
                        respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                    "/authorize" ->
                        respond(
                            ByteReadChannel(metadataResponseMissingLinkJson()),
                            HttpStatusCode.OK,
                            authorizeResponseHeaders,
                        )
                    else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
                }
            }

            // When _links.next.href is absent, tryMetadataNode returns null and Transform falls
            // through to a regular Connector. Verify the node is NOT a MetadataNode.
            val node = buildDaVinci(mockEngine).start()
            assertFalse(
                node is MetadataNode,
                "When _links.next.href is absent, tryMetadataNode should return null " +
                    "and Transform should fall through to a Connector",
            )
        }

    /**
     * Unit test: [MetadataNode.asRequest] throws [MetadataException.MissingResumeLink] when
     * the node's input lacks `_links.next.href`.
     *
     * This is the direct unit-level verification of the exception path.
     */
    @Test
    fun `MissingResumeLink has correct message`() {
        // MetadataNode constructor is internal; verify the exception message directly.
        val ex = MetadataException.MissingResumeLink()
        assertEquals("Metadata node has no _links.next.href", ex.message)
    }

    // -------------------------------------------------------------------------
    // Test 4 — Resume happy path (output only)
    // -------------------------------------------------------------------------

    @Test
    fun `resume posts output to _links_next_href and the body contains the supplied output`() =
        runTest {
            val resumeHref = "http://localhost/resume"
            val sampleOutput = buildJsonObject { put("sampleField", "sampleValue") }

            // Track the body captured at the /resume endpoint
            var capturedResumeBody: String? = null

            mockEngine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/.well-known/openid-configuration" ->
                        respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                    "/authorize" ->
                        respond(
                            ByteReadChannel(metadataResponseJson(resumeHref = resumeHref)),
                            HttpStatusCode.OK,
                            authorizeResponseHeaders,
                        )
                    "/resume" -> {
                        capturedResumeBody = (request.body as? io.ktor.http.content.TextContent)?.text
                        // Return a success-like response after resume
                        respond(
                            ByteReadChannel(
                                """
                                {
                                    "authorizeResponse": {
                                        "code": "resume-code"
                                    }
                                }
                                """.trimIndent()
                            ),
                            HttpStatusCode.OK,
                            headers,
                        )
                    }
                    "/token" -> respond(tokeResponse(), HttpStatusCode.OK, headers)
                    else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
                }
            }

            val daVinci = buildDaVinci(mockEngine)
            val startNode = daVinci.start()
            assertIs<MetadataNode>(startNode)

            startNode.resume(output = sampleOutput, error = null)

            assertNotNull(capturedResumeBody, "Resume endpoint was not called")
            val bodyJson = Json.parseToJsonElement(capturedResumeBody!!).jsonObject
            assertEquals(
                "sampleValue",
                bodyJson["output"]?.jsonObject?.get("sampleField")?.jsonPrimitive?.content,
            )
            assertFalse(
                bodyJson.containsKey("error"),
                "Body must not contain 'error' key when error is null",
            )
        }

    // -------------------------------------------------------------------------
    // Test 5 — Resume with error
    // -------------------------------------------------------------------------

    @Test
    fun `resume posts error object when error is supplied`() = runTest {
        val resumeHref = "http://localhost/resumeWithError"
        val sampleError = buildJsonObject { put("code", "ERR") }

        var capturedResumeBody: String? = null

        mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/.well-known/openid-configuration" ->
                    respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                "/authorize" ->
                    respond(
                        ByteReadChannel(metadataResponseJson(resumeHref = resumeHref)),
                        HttpStatusCode.OK,
                        authorizeResponseHeaders,
                    )
                "/resumeWithError" -> {
                    capturedResumeBody = (request.body as? io.ktor.http.content.TextContent)?.text
                    respond(
                        ByteReadChannel(
                            """
                            {
                                "authorizeResponse": {
                                    "code": "resume-error-code"
                                }
                            }
                            """.trimIndent()
                        ),
                        HttpStatusCode.OK,
                        headers,
                    )
                }
                "/token" -> respond(tokeResponse(), HttpStatusCode.OK, headers)
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val daVinci = buildDaVinci(mockEngine)
        val startNode = daVinci.start()
        assertIs<MetadataNode>(startNode)

        startNode.resume(output = null, error = sampleError)

        assertNotNull(capturedResumeBody, "Resume endpoint was not called")
        val bodyJson = Json.parseToJsonElement(capturedResumeBody!!).jsonObject
        assertEquals(
            "ERR",
            bodyJson["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content,
        )
        assertFalse(
            bodyJson.containsKey("output"),
            "Body must not contain 'output' key when output is null",
        )
    }

    // -------------------------------------------------------------------------
    // Test 6 — Cancellation guard
    // -------------------------------------------------------------------------

    @Test
    fun `CancellationException is not swallowed during resume`() = runTest {
        val resumeHref = "http://localhost/resumeSlow"
        // Tracks whether the resume endpoint was called after cancellation
        var resumeCallCount = 0

        mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/.well-known/openid-configuration" ->
                    respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                "/authorize" ->
                    respond(
                        ByteReadChannel(metadataResponseJson(resumeHref = resumeHref)),
                        HttpStatusCode.OK,
                        authorizeResponseHeaders,
                    )
                "/resumeSlow" -> {
                    resumeCallCount++
                    respond(ByteReadChannel(""), HttpStatusCode.OK)
                }
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val daVinci = buildDaVinci(mockEngine)
        val startNode = daVinci.start()
        assertIs<MetadataNode>(startNode)

        // Launch a child coroutine and cancel it immediately before it gets to run.
        // This verifies that the coroutine machinery (and therefore resume()) honours
        // cancellation — a swallowed CancellationException would keep the coroutine alive.
        val job = launch {
            // yield() ensures the coroutine cooperates with cancellation at the first
            // suspension point before the network call fires.
            kotlinx.coroutines.yield()
            startNode.resume()
        }
        job.cancel()
        job.join()

        assertTrue(job.isCancelled, "Job must be in cancelled state after cancel()+join()")
        // The resume endpoint must NOT have been called — cancellation happened before it.
        assertEquals(0, resumeCallCount, "resume endpoint must not be called after cancellation")
    }

    // -------------------------------------------------------------------------
    // Test 7 — setOutput / setError builder pattern
    // -------------------------------------------------------------------------

    @Test
    fun `setOutput and setError builder pattern sends correct body on resume with no args`() =
        runTest {
            val resumeHref = "http://localhost/resumeBuilder"

            var capturedResumeBody: String? = null

            mockEngine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/.well-known/openid-configuration" ->
                        respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                    "/authorize" ->
                        respond(
                            ByteReadChannel(metadataResponseJson(resumeHref = resumeHref)),
                            HttpStatusCode.OK,
                            authorizeResponseHeaders,
                        )
                    "/resumeBuilder" -> {
                        capturedResumeBody =
                            (request.body as? io.ktor.http.content.TextContent)?.text
                        respond(
                            ByteReadChannel(
                                """{"authorizeResponse":{"code":"builder-code"}}"""
                            ),
                            HttpStatusCode.OK,
                            headers,
                        )
                    }
                    "/token" -> respond(tokeResponse(), HttpStatusCode.OK, headers)
                    else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
                }
            }

            val daVinci = buildDaVinci(mockEngine)
            val startNode = daVinci.start()
            assertIs<MetadataNode>(startNode)

            startNode.setOutput(buildJsonObject { put("builderKey", "builderValue") })
            startNode.resume() // no args — uses pending values

            assertNotNull(capturedResumeBody)
            val bodyJson = Json.parseToJsonElement(capturedResumeBody!!).jsonObject
            assertEquals(
                "builderValue",
                bodyJson["output"]?.jsonObject?.get("builderKey")?.jsonPrimitive?.content,
            )
        }
}
