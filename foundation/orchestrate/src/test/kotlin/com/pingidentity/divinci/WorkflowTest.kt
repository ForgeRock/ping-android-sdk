/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.divinci

import com.pingidentity.testrail.TestRailCase
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.OverrideMode.APPEND
import com.pingidentity.orchestrate.OverrideMode.IGNORE
import com.pingidentity.orchestrate.Request
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.Workflow
import com.pingidentity.testrail.TestRailWatcher
import com.pingidentity.utils.PingDsl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.IllegalStateException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private lateinit var mockEngine: MockEngine

    @PingDsl
    class CustomHeaderConfig {
        var enable = true
        var value = "Android-SDK"
        var name = "header-name"
    }

    private val customHeader =
        Module.of(::CustomHeaderConfig) {
            // Intercept all send request and inject custom header
            next { _, request ->
                if (config.enable) {
                    request.header(config.name, config.value)
                }
                request
            }

            start {
                if (config.enable) {
                    it.header(config.name, config.value)
                }
                it
            }
        }

    private val forceAuth =
        Module.of {
            // Intercept all send request and inject custom header
            start { request ->
                request.parameter("forceAuth", "true")
                request
            }
        }

    private val noSession =
        Module.of {
            // Intercept all send request and inject custom header
            next { _, request ->
                sharedContext
                request.parameter("forceAuth", "true")
                request
            }
        }

    @TestRailCase(21290)
    @Test
    fun `Test same module instance should override existing module`() =
        runTest {
            val workflow =
                Workflow {
                    module(forceAuth)
                    module(customHeader) {
                        enable = true
                        name = "header-name2"
                        value = "iOS-SDK"
                    }
                    module(noSession)
                    module(customHeader) {
                        enable = true
                        name = "header-name1"
                        value = "Android-SDK"
                    }
                }
            assertTrue(workflow.config.modules.size == 3)
            val moduleRegistry = workflow.config.modules.find { it.module == customHeader }
            assertEquals("Android-SDK", (moduleRegistry!!.config as CustomHeaderConfig).value)
            //The original position is retained
            assertEquals(forceAuth, workflow.config.modules[0].module)
            assertEquals(customHeader, workflow.config.modules[1].module)
            assertEquals(noSession, workflow.config.modules[2].module)
        }

    @TestRailCase(21291)
    @Test
    fun `Test same module instance should override existing module with priority`() =
        runTest {
            val workflow =
                Workflow {
                    module(forceAuth, 1) //Last in the list
                    module(customHeader, 2) {
                        enable = true
                        name = "header-name2"
                        value = "iOS-SDK"
                    }
                    module(noSession, 3)
                    module(customHeader, 10) {
                        enable = true
                        name = "header-name1"
                        value = "Android-SDK"
                    }
                }
            assertTrue(workflow.config.modules.size == 3)
            val moduleRegistry = workflow.config.modules.find { it.module == customHeader }
            assertEquals("Android-SDK", (moduleRegistry!!.config as CustomHeaderConfig).value)
            val index = workflow.config.modules.indexOfFirst { it.module == customHeader }
            //The original position is retained even with the new priority
            assertEquals(1, index)
        }

    @TestRailCase(21292)
    @Test
    fun `Test not override existing module`() =
        runTest {
            val workflow =
                Workflow {
                    timeout = 10

                    module(forceAuth)
                    module(noSession)
                    module(customHeader) {
                        enable = true
                        value = "iOS-SDK"
                        name = "header-name2"
                    }
                    // You cannot register 2 modules with the same instance,
                    // the later one will replace the previous one
                    // To register the same module twice, you need to add overridable = false
                    // APPEND means the module cannot be replaced and does not replace previous module
                    module(customHeader, mode = APPEND) {
                        enable = true
                        value = "Android-SDK"
                        name = "header-name1"
                    }
                }
            assertTrue(workflow.config.modules.size == 4)
        }

    @TestRailCase(21294)
    @Test
    fun `Test not override existing module with Ignore`() =
        runTest {
            val workflow =
                Workflow {
                    timeout = 10

                    module(forceAuth)
                    module(noSession)
                    module(customHeader) {
                        enable = true
                        value = "iOS-SDK"
                        name = "header-name2"
                    }
                    module(customHeader, mode = IGNORE) {
                        enable = true
                        value = "Android-SDK"
                        name = "header-name1"
                    }
                }
            assertTrue(workflow.config.modules.size == 3)
            val moduleRegistry = workflow.config.modules.find { it.module == customHeader }
            assertEquals("iOS-SDK", (moduleRegistry!!.config as CustomHeaderConfig).value)

        }

    @Test
    fun `Test add module with Ignore`() =
        runTest {
            val workflow =
                Workflow {
                    timeout = 10

                    module(forceAuth)
                    module(noSession)
                    module(customHeader, mode = IGNORE) {
                        enable = true
                        value = "Android-SDK"
                        name = "header-name1"
                    }
                }
            assertTrue(workflow.config.modules.size == 3)
            val moduleRegistry = workflow.config.modules.find { it.module == customHeader }
            assertEquals("Android-SDK", (moduleRegistry!!.config as CustomHeaderConfig).value)

        }

    @TestRailCase(21293)
    @Test
    fun `Test module default priority`() =
        runTest {
            val workflow =
                Workflow {
                    module(forceAuth)
                    module(noSession)
                    module(customHeader) {
                        enable = true
                        value = "iOS-SDK"
                        name = "header-name2"
                    }
                    module(noSession)
                }
            assertTrue(workflow.config.modules.size == 3)
            val list = workflow.config.modules
            assertEquals(list[0].module, forceAuth)
            assertEquals(list[1].module, noSession)
            assertEquals(list[2].module, customHeader)
        }

    @TestRailCase(21295)
    @Test
    fun `Test module custom priority`() =
        runTest {
            val workflow =
                Workflow {
                    module(forceAuth, priority = 2)
                    module(noSession, priority = 3)
                    module(customHeader, priority = 1) {
                        enable = true
                        value = "iOS-SDK"
                        name = "header-name2"
                    }
                }
            assertTrue(workflow.config.modules.size == 3)
            val list = workflow.config.modules
            assertEquals(list[0].module, customHeader)
            assertEquals(list[1].module, forceAuth)
            assertEquals(list[2].module, noSession)
        }

    @TestRailCase(21296)
    @Test
    fun `Test module with same priority`() =
        runTest {
            val workflow =
                Workflow {
                    module(forceAuth, priority = 2)
                    module(noSession, priority = 2)
                    module(customHeader, priority = 1) {
                        enable = true
                        value = "iOS-SDK"
                        name = "header-name2"
                    }
                }
            assertTrue(workflow.config.modules.size == 3)
            val list = workflow.config.modules
            assertEquals(list[0].module, customHeader)
            assertEquals(list[1].module, forceAuth)
            assertEquals(list[2].module, noSession)
        }

    @TestRailCase(21297)
    @Test
    fun `Test module registration in each stage`() {
        val workflow = Workflow { }
        assertTrue(workflow.config.modules.isEmpty())
        assertTrue(workflow.init.isEmpty())
        assertTrue(workflow.start.isEmpty())
        assertTrue(workflow.next.isEmpty())
        assertTrue(workflow.response.isEmpty())
        assertTrue(workflow.node.isEmpty())
        assertTrue(workflow.success.isEmpty())
        assertTrue(workflow.signOff.isEmpty())

        val dummy = Module.of {
            // Intercept all send request and inject custom header
            init {
            }
            start {
                it
            }
            next { _, request ->
                request
            }
            response { }
            node {
                it
            }
            success {
                it
            }
            transform {
                SuccessNode(session = EmptySession)
            }
            signOff {
                it
            }
        }
        val workflow2 = Workflow {
            module(dummy)
        }

        assertEquals(1, workflow2.config.modules.size)
        assertEquals(1, workflow2.init.size)
        assertEquals(1, workflow2.start.size)
        assertEquals(1, workflow2.next.size)
        assertEquals(1, workflow2.response.size)
        assertEquals(1, workflow2.node.size)
        assertEquals(1, workflow2.success.size)
        assertEquals(1, workflow2.signOff.size)
    }

    @TestRailCase(21298)
    @Test
    fun `Test module execution`() = runTest {
        var initializeCnt = 0
        var startCnt = 0
        var nextCnt = 0
        var responseCnt = 0
        var transformCnt = 0
        var nodeReceivedCnt = 0
        var successCnt = 0
        var success = false

        val json = buildJsonObject {
            put("booleanKey", true)
        }

        mockEngine =
            MockEngine {
                return@MockEngine respond(
                    content =
                    ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                )
            }

        val dummy = Module.of {
            // Intercept all send request and inject custom header
            init {
                initializeCnt++
            }
            start {
                startCnt++
                it
            }
            next { _, request ->
                nextCnt++
                request
            }
            response {
                responseCnt++
            }
            transform {
                transformCnt++
                if (success) {
                    SuccessNode(session = EmptySession)
                } else {
                    success = true
                    object : ContinueNode(this, workflow, json, emptyList()) {
                        override fun asRequest(): Request {
                            return Request()
                        }
                    }
                }
            }
            node {
                nodeReceivedCnt++
                it
            }
            success {
                successCnt++
                it
            }
        }
        val workflow = Workflow {
            httpClient = HttpClient(mockEngine)
            module(dummy)
        }

        val node = workflow.start()
        val connector = node as ContinueNode
        assertEquals(workflow, connector.workflow)
        assertEquals(json, connector.input)
        assertTrue(connector.actions.isEmpty())
        assertTrue(connector.context.flowContext.isEmpty())
        connector.next()
        assertEquals(1, initializeCnt)
        assertEquals(1, startCnt)
        assertEquals(1, nextCnt)
        assertEquals(2, responseCnt)
        assertEquals(2, transformCnt)
        assertEquals(2, nodeReceivedCnt)
        assertEquals(1, successCnt)
    }

    @TestRailCase(21299)
    @Test
    fun `Test access to workflow context`() = runTest {
        val json = buildJsonObject {
            put("booleanKey", true)
        }

        mockEngine =
            MockEngine {
                return@MockEngine respond(
                    content =
                    ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                )
            }
        var success = false

        val dummy = Module.of {
            init {
                sharedContext["count"] = sharedContext["count"] as Int + 1
            }
            start {
                sharedContext["count"] = sharedContext["count"] as Int + 1
                it
            }
            next { _, request ->
                sharedContext["count"] = sharedContext["count"] as Int + 1
                request
            }
            response {
                sharedContext["count"] = sharedContext["count"] as Int + 1
            }
            transform {
                sharedContext["count"] = sharedContext["count"] as Int + 1
                if (success) {
                    SuccessNode(session = EmptySession)
                } else {
                    success = true
                    object : ContinueNode(this, workflow, json, emptyList()) {
                        override fun asRequest(): Request {
                            return Request()
                        }
                    }
                }
            }
            node {
                sharedContext["count"] = sharedContext["count"] as Int + 1
                it
            }
            success {
                sharedContext["count"] = sharedContext["count"] as Int + 1
                it
            }
        }
        val workflow = Workflow {
            httpClient = HttpClient(mockEngine)
            logger = Logger.CONSOLE
            module(dummy)
        }
        workflow.sharedContext["count"] = 0

        val node = workflow.start()
        (node as ContinueNode).next()

        assertEquals(10, workflow.sharedContext.getValue<Int>("count"))
    }

    @TestRailCase(21300)
    @Test
    fun `test init state function throw exception`() = runTest {
        var count = 0
        val initFailed = Module.of {
            init {
                count++
                throw IllegalStateException("Failed to initialize")
            }
        }

        val workflow = Workflow {
            module(initFailed)
        }

        //Call the start twice, since the init() failed, it will call init again.
        val node = workflow.start()
        workflow.start()
        assertTrue { count == 2 }
        assertTrue(node is FailureNode)
        assertTrue(node.cause is IllegalStateException)
    }

    @TestRailCase(22119)
    @Test
    fun `test start state function throw exception`() = runTest {
        val module = Module.of {
            start {
                throw IllegalStateException("Failed to start")
            }
        }

        val workflow = Workflow {
            module(module)
        }

        val node = workflow.start()
        assertTrue(node is FailureNode)
        assertTrue(node.cause is IllegalStateException)
    }

    @TestRailCase(21301)
    @Test
    fun `test response state function throw exception`() = runTest {
        mockEngine =
            MockEngine {
                return@MockEngine respond(
                    content =
                    ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                )
            }

        val module = Module.of {
            response {
                throw IllegalStateException("Failed to start")
            }
        }

        val workflow = Workflow {
            httpClient = HttpClient(mockEngine)
            module(module)
        }

        val node = workflow.start()
        assertTrue(node is FailureNode)
        assertTrue(node.cause is IllegalStateException)
    }

    @TestRailCase(21302)
    @Test
    fun `test transform state function throw exception`() = runTest {
        mockEngine =
            MockEngine {
                return@MockEngine respond(
                    content =
                    ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                )
            }

        val module = Module.of {
            transform {
                throw IllegalStateException("Failed to transform")
            }
        }

        val workflow = Workflow {
            httpClient = HttpClient(mockEngine)
            module(module)
        }

        val node = workflow.start()
        assertTrue(node is FailureNode)
        assertTrue(node.cause is IllegalStateException)
    }

    @TestRailCase(21303)
    @Test
    fun `test next state function throw exception`() = runTest {
        val json = buildJsonObject {
            put("booleanKey", true)
        }
        mockEngine =
            MockEngine {
                return@MockEngine respond(
                    content =
                    ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                )
            }

        val module = Module.of {
            next { _, _ ->
                throw IllegalStateException("Failed to start")
            }

            transform {
                object : ContinueNode(this, workflow, json, emptyList()) {
                    override fun asRequest(): Request {
                        return Request()
                    }
                }
            }
        }

        val workflow = Workflow {
            httpClient = HttpClient(mockEngine)
            module(module)
        }

        val node = workflow.start()
        val next = (node as ContinueNode).next()
        assertTrue(next is FailureNode)
        assertTrue(next.cause is IllegalStateException)
    }

    @TestRailCase(21304)
    @Test
    fun `Test node state function throw exception`() = runTest {
        val json = buildJsonObject {
            put("booleanKey", true)
        }
        mockEngine =
            MockEngine {
                return@MockEngine respond(
                    content =
                    ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                )
            }

        val module = Module.of {
            node {
                throw IllegalStateException("Failed to Node")
            }

            transform {
                object : ContinueNode(this, workflow, json, emptyList()) {
                    override fun asRequest(): Request {
                        return Request()
                    }
                }
            }
        }

        val workflow = Workflow {
            httpClient = HttpClient(mockEngine)
            module(module)
        }

        val node = workflow.start()
        assertTrue(node is FailureNode)
        assertTrue(node.cause is IllegalStateException)
    }

    @TestRailCase(21305)
    @Test
    fun `test success state function throw exception`() = runTest {
        mockEngine =
            MockEngine {
                return@MockEngine respond(
                    content =
                    ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                )
            }

        val module = Module.of {

            transform {
                SuccessNode(session = EmptySession)
            }

            success {
                throw IllegalStateException("Failed in Success state")

            }
        }

        val workflow = Workflow {
            httpClient = HttpClient(mockEngine)
            module(module)
        }

        val node = workflow.start()

        assertTrue(node is FailureNode)
        assertTrue(node.cause is IllegalStateException)
    }

    @TestRailCase(22120)
    @Test
    fun `Test execution failure`() = runTest {

        mockEngine =
            MockEngine {
                return@MockEngine respond(
                    content =
                    ByteReadChannel("Invalid request"),
                    status = HttpStatusCode.BadRequest,
                )
            }

        val dummy = Module.of {
            transform {
                ErrorNode(Json.parseToJsonElement("{}").jsonObject, "Invalid request")
            }
        }
        val workflow = Workflow {
            httpClient = HttpClient(mockEngine)
            module(dummy)
        }

        val node = workflow.start()
        val failure = node as ErrorNode
        assertEquals("Invalid request", failure.message)
        assertTrue(failure.input.isEmpty())
    }

    @TestRailCase(22121)
    @Test
    fun `signOff should return success result when no exceptions occur`() = runTest {
        val mockHttpClient = HttpClient(MockEngine { respond("") })
        val workflow = Workflow {
            httpClient = mockHttpClient
        }

        val result = workflow.signOff()

        assertTrue(result.isSuccess)
    }

    @TestRailCase(22122)
    @Test
    fun `signOff should return failure result when exception occurs`() = runTest {
        val mockHttpClient = HttpClient(MockEngine { throw IllegalStateException("Sign off failed") })
        val workflow = Workflow {
            httpClient = mockHttpClient
        }

        val result = workflow.signOff()

        assertTrue(result.isFailure)
        assertTrue((result).exceptionOrNull() is IllegalStateException)
        assertEquals("Sign off failed", result.exceptionOrNull()!!.message)
    }
}