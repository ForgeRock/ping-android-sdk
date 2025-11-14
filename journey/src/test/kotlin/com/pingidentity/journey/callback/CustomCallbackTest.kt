/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import android.content.Context
import com.pingidentity.journey.CallbackInitializer
import com.pingidentity.journey.Journey
import com.pingidentity.journey.SSOToken
import com.pingidentity.journey.authenticate
import com.pingidentity.journey.authenticateHeader
import com.pingidentity.journey.module.Session
import com.pingidentity.journey.plugin.Callback
import com.pingidentity.journey.plugin.JourneyAware
import com.pingidentity.journey.plugin.RequestInterceptor
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.sessionResponse
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Request
import com.pingidentity.storage.MemoryStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.content.TextContent
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CustomCallbackTest {
    private lateinit var mockEngine: MockEngine
    private val mockContext: Context = mockk()


    @BeforeTest
    fun setUp() {
        CallbackInitializer().create(mockContext)
        com.pingidentity.journey.plugin.CallbackRegistry.register("NameCallback", ::CustomCallback)

        mockEngine =
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/am/json/realms/root/authenticate" -> {
                        if (request.body is TextContent) {
                            val result =
                                request.body as TextContent
                            val json =
                                Json.parseToJsonElement(
                                    result.text
                                ).jsonObject
                            val callbacks = json["callbacks"]?.jsonArray
                            if ((callbacks?.size ?: 0) == 2) {
                                return@MockEngine respond(
                                    sessionResponse(),
                                    HttpStatusCode.OK,
                                    authenticateHeader
                                )
                            }
                        }
                        return@MockEngine respond(
                            authenticate(),
                            HttpStatusCode.OK,
                            authenticateHeader
                        )
                    }

                    else -> {
                        return@MockEngine respond(
                            content =
                               ByteReadChannel(""),
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


    @Test
    fun customCallbackWithRequestInterceptor() = runTest {
        val sessionStorage = MemoryStorage<SSOToken>()
        val journey =
            Journey {
                serverUrl = "http://localhost/am"
                logger = Logger.CONSOLE
                httpClient = HttpClient(mockEngine) {
                    followRedirects = false
                }
                module(Session) {
                    tokenStorage = sessionStorage
                }
            }

        val node = journey.start() // Return first Node
        assertTrue(node is ContinueNode)
        //Successful override NameCallback
        assertTrue(node.callbacks[0] is CustomCallback)
        val callback = node.callbacks[0] as CustomCallback
        //Initialize the callback
        assertNotNull(callback.jsonObject)
        //Journey is injected
        assertNotNull(callback.journey)
        node.next()

        //Request is intercepted
        val request = mockEngine.requestHistory[1]
        assertEquals("value", request.headers["custom"])

    }
}

class CustomCallback : Callback, RequestInterceptor, JourneyAware {

    lateinit var jsonObject: JsonObject

    override fun init(jsonObject: JsonObject): Callback {
        this.jsonObject = jsonObject
        return this
    }

    override fun payload(): JsonObject {
        return jsonObject
    }

    override lateinit var journey: Journey

    override var intercept: FlowContext.(Request) -> Request = { request ->
        request.header("custom", "value")
        request
    }
}