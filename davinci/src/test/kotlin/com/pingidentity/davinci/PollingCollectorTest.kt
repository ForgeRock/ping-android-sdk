/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.PollingCollector
import com.pingidentity.davinci.collector.PollingStatus
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.logger.Logger
import com.pingidentity.network.ktor.KtorHttpClient
import com.pingidentity.orchestrate.Action
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.SharedContext
import com.pingidentity.orchestrate.Workflow
import com.pingidentity.orchestrate.WorkflowConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.BeforeTest
import com.pingidentity.network.HttpRequest as Request

class PollingCollectorTest {

    private lateinit var daVinci: DaVinci
    private lateinit var config: WorkflowConfig
    private lateinit var logger: Logger

    @BeforeTest
    fun setUp() {
        logger = mockk(relaxed = true)
        config = mockk(relaxed = true)
        daVinci = mockk(relaxed = true)
        every { daVinci.config } returns config
        every { config.logger } returns logger
    }

    @Test
    fun testInitialization() {
        val pollingCollector = PollingCollector()
        assertNotNull(pollingCollector)
    }

    @Test
    fun `should initialize with default values from JsonObject`() {
        val pollingCollector = PollingCollector()
        val jsonObject = buildJsonObject {
            put("pollInterval", "3000")
            put("pollRetries", "30")
            put("pollChallengeStatus", false)
            put("challenge", "testChallenge")
        }

        pollingCollector.init(jsonObject)

        assertEquals("3000", pollingCollector.pollInterval)
        assertEquals("30", pollingCollector.pollRetries)
        assertFalse(pollingCollector.pollChallengeStatus)
        assertEquals("testChallenge", pollingCollector.challenge)
    }

    @Test
    fun `should initialize with default values when fields are missing`() {
        val pollingCollector = PollingCollector()
        val jsonObject = buildJsonObject {}

        pollingCollector.init(jsonObject)

        assertEquals("2000", pollingCollector.pollInterval)
        assertEquals("60", pollingCollector.pollRetries)
        assertFalse(pollingCollector.pollChallengeStatus)
        assertEquals("", pollingCollector.challenge)
    }

    @Test
    fun `should initialize with pollChallengeStatus true`() {
        val pollingCollector = PollingCollector()
        val jsonObject = buildJsonObject {
            put("pollChallengeStatus", true)
            put("challenge", "testChallengeValue")
        }

        pollingCollector.init(jsonObject)

        assertTrue(pollingCollector.pollChallengeStatus)
        assertEquals("testChallengeValue", pollingCollector.challenge)
    }

    @Test
    fun `should return eventType as polling`() {
        val pollingCollector = PollingCollector()
        assertEquals("polling", pollingCollector.eventType())
    }

    @Test
    fun `should execute simple polling without challenge status`() = runTest {
        val pollingCollector = PollingCollector()
        val jsonObject = buildJsonObject {
            put("pollInterval", "10")
            put("pollRetries", "5")
            put("pollChallengeStatus", false)
        }

        pollingCollector.init(jsonObject)
        pollingCollector.davinci = daVinci

        val statuses = pollingCollector.pollStatus().toList()

        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Complete)
        assertEquals("continue", (statuses[0] as PollingStatus.Complete).status)
        assertEquals("continue", pollingCollector.value) // Value set via interception
        assertEquals(4, pollingCollector.retriesAllowed)
    }

    @Test
    fun `should poll challenge status and complete when isChallengeComplete is true`() = runTest {
        val pollingCollector = PollingCollector()

        // Create mock response
        val mockEngine = MockEngine { request ->
            // Verify the request
            assertTrue(request.url.encodedPath.contains("/davinci/user/credentials/challenge"))
            assertTrue(request.url.encodedPath.endsWith("/status"))
            assertEquals("test-interaction-id", request.headers["interactionId"])

            // Return successful challenge completion
            respond(
                content = """{"isChallengeComplete": true, "status": "approved"}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val httpClient = KtorHttpClient(HttpClient(mockEngine))
        every { daVinci.config.httpClient } returns httpClient

        // Create test input
        val inputJson = buildJsonObject {
            put("pollInterval", "10")
            put("pollRetries", "5")
            put("pollChallengeStatus", true)
            put("challenge", "test-challenge-123")
            put("interactionId", "test-interaction-id")
            putJsonObject("_links") {
                putJsonObject("self") {
                    put("href", "https://auth.pingone.ca/test-env-id/davinci/connections/test-connection/capabilities/test")
                }
            }
        }

        // Create a minimal ContinueNode
        val continueNode = object : ContinueNode(
            FlowContext(SharedContext(mutableMapOf())),
            Workflow {},
            inputJson,
            emptyList<Action>()
        ) {
            override fun asRequest(): Request = daVinci.config.httpClient.request()
        }

        pollingCollector.init(inputJson)
        pollingCollector.davinci = daVinci
        pollingCollector.continueNode = continueNode

        val statuses = pollingCollector.pollStatus().toList()

        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Complete)
        assertEquals("approved", (statuses[0] as PollingStatus.Complete).status)
        assertEquals("approved", pollingCollector.value) // Value set via interception

        mockEngine.close()
        httpClient.close()
    }

    @Test
    fun `should continue polling when isChallengeComplete is false`() = runTest {
        val pollingCollector = PollingCollector()

        var requestCount = 0
        val maxRetries = 3

        // Create mock response that returns false for first 2 calls, then true
        val mockEngine = MockEngine { request ->
            requestCount++

            val isComplete = requestCount >= maxRetries
            respond(
                content = """{"isChallengeComplete": $isComplete, "status": "approved"}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val httpClient = KtorHttpClient(HttpClient(mockEngine))

        every { daVinci.config.httpClient } returns httpClient

        // Create test input
        val inputJson = buildJsonObject {
            put("pollInterval", "10")
            put("pollRetries", maxRetries.toString())
            put("pollChallengeStatus", true)
            put("challenge", "test-challenge-456")
            put("interactionId", "test-interaction-id-2")
            putJsonObject("_links") {
                putJsonObject("self") {
                    put("href", "https://auth.pingone.ca/env-123/davinci/connections/conn-456/capabilities/test")
                }
            }
        }

        val continueNode = object : ContinueNode(
            FlowContext(SharedContext(mutableMapOf())),
            Workflow {},
            inputJson,
            emptyList<Action>()
        ) {
            override fun asRequest(): Request = daVinci.config.httpClient.request()
        }

        pollingCollector.init(inputJson)
        pollingCollector.davinci = daVinci
        pollingCollector.continueNode = continueNode

        val statuses = pollingCollector.pollStatus().toList()

        // Should emit Continue status for first 2 attempts, then Complete
        assertEquals(maxRetries, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Continue)
        assertTrue(statuses[1] is PollingStatus.Continue)
        assertTrue(statuses[2] is PollingStatus.Complete)

        // Verify retry counts
        assertEquals(1, (statuses[0] as PollingStatus.Continue).retryCount)
        assertEquals(2, (statuses[1] as PollingStatus.Continue).retryCount)

        assertEquals("approved", pollingCollector.value) // Value set from last emission
        assertEquals(maxRetries, requestCount)

        mockEngine.close()
        httpClient.close()
    }

    @Test
    fun `should reach max retries when challenge never completes`() = runTest {
        val pollingCollector = PollingCollector()

        var requestCount = 0
        val maxRetries = 3

        // Create mock response that always returns false
        val mockEngine = MockEngine { request ->
            requestCount++
            respond(
                content = """{"isChallengeComplete": false}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val httpClient = KtorHttpClient(HttpClient(mockEngine))

        every { daVinci.config.httpClient } returns httpClient

        val inputJson = buildJsonObject {
            put("pollInterval", "10")
            put("pollRetries", maxRetries.toString())
            put("pollChallengeStatus", true)
            put("challenge", "test-challenge-789")
            put("interactionId", "test-interaction-id-3")
            putJsonObject("_links") {
                putJsonObject("self") {
                    put("href", "https://auth.pingone.ca/env-999/davinci/connections/conn-888/capabilities/test")
                }
            }
        }

        val continueNode = object : ContinueNode(
            FlowContext(SharedContext(mutableMapOf())),
            Workflow {},
            inputJson,
            emptyList<Action>()
        ) {
            override fun asRequest(): Request = daVinci.config.httpClient.request()
        }

        pollingCollector.init(inputJson)
        pollingCollector.davinci = daVinci
        pollingCollector.continueNode = continueNode

        val statuses = pollingCollector.pollStatus().toList()

        // Should emit Continue status for all retries, then TimeOut
        assertEquals(maxRetries + 1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Continue)
        assertTrue(statuses[1] is PollingStatus.Continue)
        assertTrue(statuses[2] is PollingStatus.Continue)
        assertTrue(statuses[3] is PollingStatus.TimedOut)

        assertEquals("timedOut", pollingCollector.value) // Value set via interception
        assertEquals(maxRetries, requestCount)

        mockEngine.close()
        httpClient.close()
    }

    @Test
    fun `should emit Expired status when server returns non-200 response with challengeExpired serviceName`() = runTest {
        val pollingCollector = PollingCollector()

        var requestCount = 0
        val maxRetries = 3

        // Create mock response that returns a non-200 status with challengeExpired serviceName
        val mockEngine = MockEngine { request ->
            requestCount++
            respond(
                content = """{"cause":{"message":"Challenge Expired"},"logLevel":"error","serviceName":"challengeExpired","correlationId":"c4d740ca-138f-4047-8391-da312b4e1027"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val httpClient = KtorHttpClient(HttpClient(mockEngine))

        every { daVinci.config.httpClient } returns httpClient

        val inputJson = buildJsonObject {
            put("pollInterval", "10")
            put("pollRetries", maxRetries.toString())
            put("pollChallengeStatus", true)
            put("challenge", "test-challenge-error")
            put("interactionId", "test-interaction-id-error")
            putJsonObject("_links") {
                putJsonObject("self") {
                    put("href", "https://auth.pingone.ca/env-error/davinci/connections/conn-error/capabilities/test")
                }
            }
        }

        val continueNode = object : ContinueNode(
            FlowContext(SharedContext(mutableMapOf())),
            Workflow {},
            inputJson,
            emptyList<Action>()
        ) {
            override fun asRequest(): Request = daVinci.config.httpClient.request()
        }

        pollingCollector.init(inputJson)
        pollingCollector.davinci = daVinci
        pollingCollector.continueNode = continueNode

        val statuses = pollingCollector.pollStatus().toList()

        // Non-200 response emits Expired immediately and stops polling (only 1 request made)
        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Expired)
        assertEquals("expired", pollingCollector.value) // Value set via interception
        assertEquals(1, requestCount) // Polling stops after first non-200 response

        mockEngine.close()
        httpClient.close()
    }

    @Test
    fun `should emit Error status when server returns non-200 response without challengeExpired serviceName`() = runTest {
        val pollingCollector = PollingCollector()

        var requestCount = 0

        // Create mock response that returns a non-200 status with an unrecognized serviceName
        val mockEngine = MockEngine { request ->
            requestCount++
            respond(
                content = """{"cause":{"message":"Internal Server Error"},"logLevel":"error","serviceName":"internalError","correlationId":"abc-123"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val httpClient = KtorHttpClient(HttpClient(mockEngine))

        every { daVinci.config.httpClient } returns httpClient

        val inputJson = buildJsonObject {
            put("pollInterval", "10")
            put("pollRetries", "3")
            put("pollChallengeStatus", true)
            put("challenge", "test-challenge-error")
            put("interactionId", "test-interaction-id-error")
            putJsonObject("_links") {
                putJsonObject("self") {
                    put("href", "https://auth.pingone.ca/env-error/davinci/connections/conn-error/capabilities/test")
                }
            }
        }

        val continueNode = object : ContinueNode(
            FlowContext(SharedContext(mutableMapOf())),
            Workflow {},
            inputJson,
            emptyList<Action>()
        ) {
            override fun asRequest(): Request = daVinci.config.httpClient.request()
        }

        pollingCollector.init(inputJson)
        pollingCollector.davinci = daVinci
        pollingCollector.continueNode = continueNode

        val statuses = pollingCollector.pollStatus().toList()

        // Non-200 without challengeExpired serviceName emits Error and stops polling
        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Error)
        assertEquals("error", pollingCollector.value)
        assertEquals(1, requestCount)

        mockEngine.close()
        httpClient.close()
    }

    @Test
    fun `should emit Expired status when server returns 401 Unauthorized`() = runTest {
        val pollingCollector = PollingCollector()

        var requestCount = 0

        val mockEngine = MockEngine { request ->
            requestCount++
            respond(
                content = """{"cause":{"message":"Challenge Expired"},"logLevel":"error","serviceName":"challengeExpired","correlationId":"c4d740ca-138f-4047-8391-da312b4e1027"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val httpClient = KtorHttpClient(HttpClient(mockEngine))

        every { daVinci.config.httpClient } returns httpClient

        val inputJson = buildJsonObject {
            put("pollInterval", "10")
            put("pollRetries", "5")
            put("pollChallengeStatus", true)
            put("challenge", "test-challenge-401")
            put("interactionId", "test-interaction-id-401")
            putJsonObject("_links") {
                putJsonObject("self") {
                    put("href", "https://auth.pingone.ca/env-401/davinci/connections/conn-401/capabilities/test")
                }
            }
        }

        val continueNode = object : ContinueNode(
            FlowContext(SharedContext(mutableMapOf())),
            Workflow {},
            inputJson,
            emptyList<Action>()
        ) {
            override fun asRequest(): Request = daVinci.config.httpClient.request()
        }

        pollingCollector.init(inputJson)
        pollingCollector.davinci = daVinci
        pollingCollector.continueNode = continueNode

        val statuses = pollingCollector.pollStatus().toList()

        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Expired)
        assertEquals("expired", pollingCollector.value)
        assertEquals(1, requestCount)

        mockEngine.close()
        httpClient.close()
    }

    @Test
    fun `should emit Error status when JSON parsing fails`() = runTest {
        val pollingCollector = PollingCollector()

        var requestCount = 0

        // Create mock response that returns invalid JSON
        val mockEngine = MockEngine { request ->
            requestCount++
            respond(
                content = """This is not valid JSON!""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "text/plain")
            )
        }

        val httpClient = KtorHttpClient(HttpClient(mockEngine))

        every { daVinci.config.httpClient } returns httpClient

        val inputJson = buildJsonObject {
            put("pollInterval", "10")
            put("pollRetries", "3")
            put("pollChallengeStatus", true)
            put("challenge", "test-challenge-parse-error")
            put("interactionId", "test-interaction-id-parse-error")
            putJsonObject("_links") {
                putJsonObject("self") {
                    put("href", "https://auth.pingone.ca/env-parse/davinci/connections/conn-parse/capabilities/test")
                }
            }
        }

        val continueNode = object : ContinueNode(
            FlowContext(SharedContext(mutableMapOf())),
            Workflow {},
            inputJson,
            emptyList<Action>()
        ) {
            override fun asRequest(): Request = daVinci.config.httpClient.request()
        }

        pollingCollector.init(inputJson)
        pollingCollector.davinci = daVinci
        pollingCollector.continueNode = continueNode

        val statuses = pollingCollector.pollStatus().toList()

        // Should emit Error status on JSON parsing failure
        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Error)
        assertEquals("error", pollingCollector.value) // Value set via interception
        assertEquals(1, requestCount)

        mockEngine.close()
        httpClient.close()
    }

    @Test
    fun `should not poll when pollChallengeStatus is false`() = runTest {
        val pollingCollector = PollingCollector()
        val inputJson = buildJsonObject {
            put("pollInterval", "10")
            put("pollChallengeStatus", false)
            put("challenge", "test-challenge")
        }

        pollingCollector.init(inputJson)
        pollingCollector.davinci = daVinci

        val statuses = pollingCollector.pollStatus().toList()

        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Complete)
        assertEquals("continue", (statuses[0] as PollingStatus.Complete).status)
        assertEquals("continue", pollingCollector.value) // Value set via interception
    }

    @Test
    fun `should not poll when challenge is empty`() = runTest {
        val pollingCollector = PollingCollector()

        val inputJson = buildJsonObject {
            put("pollInterval", "10")
            put("pollChallengeStatus", true)
            put("challenge", "")
        }

        pollingCollector.init(inputJson)
        pollingCollector.davinci = daVinci

        val statuses = pollingCollector.pollStatus().toList()

        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Complete)
        assertEquals("continue", (statuses[0] as PollingStatus.Complete).status)
        assertEquals("continue", pollingCollector.value) // Value set via interception
    }

    @Test
    fun `should emit TimeOut when simple polling retries reach 0`() = runTest {
        val pollingCollector = PollingCollector()
        val inputJson = buildJsonObject {
            put("pollInterval", "10")
            put("pollRetries", "1") // Only 1 retry
            put("pollChallengeStatus", false)
        }

        pollingCollector.init(inputJson)
        pollingCollector.davinci = daVinci

        val statuses = pollingCollector.pollStatus().toList()

        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.TimedOut)
        assertEquals("timedOut", pollingCollector.value) // Value set via interception
        assertEquals(0, pollingCollector.retriesAllowed)
    }

    @Test
    fun `should emit Complete with continue on each poll call and TimedOut when retries exhausted`() = runTest {
        val pollingCollector = PollingCollector()
        val inputJson = buildJsonObject {
            put("pollInterval", "10")
            put("pollRetries", "3")
            put("pollChallengeStatus", false)
        }

        pollingCollector.init(inputJson)
        pollingCollector.davinci = daVinci

        // First poll: retriesAllowed 3 → 2
        var statuses = pollingCollector.pollStatus().toList()
        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Complete)
        assertEquals("continue", (statuses[0] as PollingStatus.Complete).status)
        assertEquals("continue", pollingCollector.value)
        assertEquals(2, pollingCollector.retriesAllowed)

        // Second poll: retriesAllowed 2 → 1
        statuses = pollingCollector.pollStatus().toList()
        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.Complete)
        assertEquals("continue", (statuses[0] as PollingStatus.Complete).status)
        assertEquals("continue", pollingCollector.value)
        assertEquals(1, pollingCollector.retriesAllowed)

        // Third poll: retriesAllowed 1 → 0, emits TimedOut
        statuses = pollingCollector.pollStatus().toList()
        assertEquals(1, statuses.size)
        assertTrue(statuses[0] is PollingStatus.TimedOut)
        assertEquals("timedOut", pollingCollector.value)
        assertEquals(0, pollingCollector.retriesAllowed)
    }
}

