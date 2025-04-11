/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import com.pingidentity.exception.ApiException
import com.pingidentity.idp.IdpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdpRequestHandlerTest {

    @Test
    fun `fetch with valid response returns IdpClient`() = runTest {
        val handler = mockk<IdpRequestHandler>()
        val httpClient = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond(
                        content =
                            buildJsonObject {
                                put("idp", buildJsonObject {
                                    put("clientId", "testClientId")
                                    put("nonce", "testNonce")
                                    put("scopes", buildJsonArray {
                                        add("scope1")
                                        add("scope2")
                                    })
                                })
                                put("_links", buildJsonObject {
                                    put("next", buildJsonObject {
                                        put("href", "http://next-url.com")
                                    })
                                })
                            }.toString(),
                        headers = headersOf("Content-Type" to listOf("application/json"))
                    )
                }
            }
        }
        val url = "http://valid-url.com"
        val expectedClient = IdpClient(
            "testClientId",
            "http://next-url.com",
            listOf("scope1", "scope2"),
            "testNonce",
            "http://next-url.com"
        )

        coEvery { handler.fetch(httpClient, url) } returns expectedClient

        val result = handler.fetch(httpClient, url)

        assertEquals(expectedClient, result)
    }

    @Test
    fun `fetch with error response throws ApiException`() = runTest {
        val handler = mockk<IdpRequestHandler>()
        val httpClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond(
                        content = "Error",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf("Content-Type" to listOf("application/json"))
                    )
                }
            }
        }
        val url = "http://invalid-url.com"

        coEvery { handler.fetch(httpClient, url) } throws ApiException(400, "Error")

        assertFailsWith<ApiException> {
            handler.fetch(httpClient, url)
        }
    }
}