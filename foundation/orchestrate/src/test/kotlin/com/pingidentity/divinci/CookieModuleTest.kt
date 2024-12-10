/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.divinci

import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.Request
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.Workflow
import com.pingidentity.orchestrate.module.Cookie
import com.pingidentity.orchestrate.module.Cookies
import com.pingidentity.storage.MemoryStorage
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CookieModuleTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private lateinit var mockEngine: MockEngine

    @TestRailCase(22141)
    @Test
    fun `test cookie from response`() = runTest {
        mockEngine =
            MockEngine { _ ->
                return@MockEngine respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                    headers {
                        append(
                            "Set-Cookie",
                            "interactionId=178ce234-afd2-4207-984e-bda28bd7042c; Max-Age=3600; Path=/; Expires=Thu, 09 May 9999 21:38:44 GMT; HttpOnly;"
                        )
                        append(
                            "Set-Cookie",
                            "interactionToken=abc; Max-Age=3600; Path=/; Expires=Thu, 09 May 9999 21:38:44 GMT; HttpOnly;"
                        )
                    }
                )
            }

        val dummy = Module.of {
            transform {
                SuccessNode(session = EmptySession)
            }
        }

        val memory = MemoryStorage<Cookies>()

        val workflow = Workflow {
            httpClient = HttpClient(mockEngine)
            module(dummy)
            module(Cookie) {
                storage = memory
                persist = mutableListOf("interactionId", "interactionToken")
            }
        }

        workflow.start()
        assertEquals(2, memory.get()!!.size)

    }

    @TestRailCase(22142)
    @Test
    fun `test cookie storage from response`() = runTest {
        mockEngine =
            MockEngine { _ ->
                return@MockEngine respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                    headers {
                        append(
                            "Set-Cookie",
                            "interactionId=178ce234-afd2-4207-984e-bda28bd7042c; Max-Age=3600; Path=/; Expires=Thu, 09 May 9999 21:38:44 GMT; HttpOnly;"
                        )
                        append(
                            "Set-Cookie",
                            "interactionToken=abc; Max-Age=3600; Path=/; Expires=Thu, 09 May 9999 21:38:44 GMT; HttpOnly;"
                        )
                    }
                )
            }

        val dummy = Module.of {
            transform {
                SuccessNode(session = EmptySession)
            }
        }

        val memory = MemoryStorage<Cookies>()

        val workflow = Workflow {
            httpClient = HttpClient(mockEngine)
            module(dummy)
            module(Cookie) {
                storage = memory
                //We only want to persist interactionId
                persist = mutableListOf("interactionId")
            }
        }
        workflow.start()
        assertEquals(1, memory.get()!!.size)
    }

    @TestRailCase(22143)
    @Test
    fun `test cookie inject to request and signoff`() = runTest {
        var success = false
        val json = buildJsonObject {
            put("booleanKey", true)
        }
        mockEngine =
            MockEngine { _ ->
                return@MockEngine respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                    headers {
                        append(
                            "Set-Cookie",
                            "interactionId=178ce234-afd2-4207-984e-bda28bd7042c; Max-Age=3600; Path=/; Expires=Thu, 09 May 9999 21:38:44 GMT; HttpOnly;"
                        )
                        append(
                            "Set-Cookie",
                            "interactionToken=abc; Max-Age=3600; Path=/; Expires=Thu, 09 May 9999 21:38:44 GMT; HttpOnly;"
                        )
                    }
                )
            }

        val dummy = Module.of {
            transform {
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
        }

        val memory = MemoryStorage<Cookies>()

        val workflow = Workflow {
            httpClient = HttpClient(mockEngine)
            module(dummy)
            module(Cookie) {
                storage = memory
                //We only want to persist interactionId
                persist = mutableListOf("interactionId")
            }
        }
        val node = workflow.start()
        (node as ContinueNode).next()

        assertTrue(
            mockEngine.requestHistory[1].headers["Cookie"]!!.contains("interactionId=178ce234-afd2-4207-984e-bda28bd7042c")
        )
        assertTrue(
            mockEngine.requestHistory[1].headers["Cookie"]!!.contains("interactionToken=abc")
        )

        //Still only one cookie in storage
        assertEquals(1, memory.get()!!.size)

        assertTrue( workflow.signOff().isSuccess)

        assertTrue(
            mockEngine.requestHistory[2].headers["Cookie"]!!.contains("interactionId=178ce234-afd2-4207-984e-bda28bd7042c")
        )
        //We only want to persist interactionId, for signoff we should not have interactionToken
        assertFalse (
            mockEngine.requestHistory[2].headers["Cookie"]!!.contains("interactionToken=abc")
        )
        assertNull(memory.get())

    }

    @TestRailCase(22144)
    @Test
    fun `test expired cookie from response`() = runTest {
        mockEngine =
            MockEngine { _ ->
                return@MockEngine respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                    headers {
                        append(
                            "Set-Cookie",
                            "interactionId=178ce234-afd2-4207-984e-bda28bd7042c; Max-Age=3600; Path=/; Expires=Thu, 09 May 2024 21:38:44 GMT; HttpOnly;"
                        )
                        append(
                            "Set-Cookie",
                            "interactionToken=abc; Max-Age=3600; Path=/; Expires=Thu, 09 May 9999 21:38:44 GMT; HttpOnly;"
                        )
                    }
                )
            }

        val dummy = Module.of {
            transform {
                SuccessNode(session = EmptySession)
            }
        }

        val memory = MemoryStorage<Cookies>()

        val workflow = Workflow {
            httpClient = HttpClient(mockEngine)
            module(dummy)
            module(Cookie) {
                storage = memory
                persist = mutableListOf("interactionId", "interactionToken")
            }
        }

        workflow.start()
        assertEquals(1, memory.get()!!.size)

    }
}