/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import com.pingidentity.idp.IdpClient
import com.pingidentity.idp.IdpResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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

class GoogleRequestHandlerTest {

    @Test
    fun `authorize with valid URL returns Request`() = runTest {
        val httpClient = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond(
                        content = buildJsonObject {
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
        val handler = GoogleRequestHandler(httpClient, mockk())
        val url = "http://valid-url.com"
        coEvery { handler.authorize(any<IdpClient>()) } returns IdpResult("testIdToken")

        val result = handler.authorize(url)

        assertEquals(result.builder.url.build().toString(), "http://next-url.com")
        assertEquals(result.builder.headers["Accept"], "application/json")
        assertEquals(result.builder.body.toString(), "{\"idToken\":\"testIdToken\"}")
    }

    @Test
    fun `authorize with valid IdpClient returns IdpResult`() = runTest {
        val handler = GoogleRequestHandler(mockk(), mockk())
        val idpClient = IdpClient(
            "testClientId",
            "http://next-url.com",
            listOf("scope1", "scope2"),
            "testNonce",
            "http://next-url.com"
        )
        val expectedResult = IdpResult("testIdToken")

        coEvery { handler.authorize(idpClient) } returns expectedResult

        val result = handler.authorize(idpClient)

        assertEquals(expectedResult, result)
    }

    @Test
    fun `authorize with invalid IdpClient throws Exception`() = runTest {
        val handler = GoogleRequestHandler(mockk(), mockk())
        val idpClient = IdpClient(
            "invalidClientId",
            "http://next-url.com",
            listOf("scope1", "scope2"),
            "testNonce",
            "http://next-url.com"
        )

        coEvery { handler.authorize(idpClient) } throws Exception("Invalid IdpClient")

        assertFailsWith<Exception> {
            handler.authorize(idpClient)
        }
    }
}