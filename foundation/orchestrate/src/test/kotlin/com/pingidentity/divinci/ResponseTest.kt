/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.divinci

import com.pingidentity.orchestrate.Response
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResponseTest {

    @Test
    fun `body should return response body as string`() = runTest {
        val mockHttpResponse = mockk<HttpResponse> {
            coEvery { body<String>() } returns "response body"
        }
        val response = Response(mockk(), mockHttpResponse)
        assertEquals("response body", response.body())
    }

    @Test
    fun `status should return response status code`() {
        val mockHttpResponse = mockk<HttpResponse> {
            every { status.value } returns 200
        }
        val response = Response(mockk(), mockHttpResponse)
        assertEquals(200, response.status())
    }

    @Test
    fun `cookies should return cookies from response`() {
        val mockHttpResponse = mockk<HttpResponse> {
            every { headers.getAll("Set-Cookie") } returns listOf("cookie1=value1", "cookie2=value2")
        }
        val response = Response(mockk(), mockHttpResponse)
        assertEquals(listOf("cookie1=value1", "cookie2=value2"), response.cookies())
    }

    @Test
    fun `header should return specific header value`() {
        val mockHttpResponse = mockk<HttpResponse> {
            every { headers["Content-Type"] } returns "application/json"
        }
        val response = Response(mockk(), mockHttpResponse)
        assertEquals("application/json", response.header("Content-Type"))
    }

    @Test
    fun `header should return null if header is not present`() {
        val mockHttpResponse = mockk<HttpResponse> {
            every { headers["Non-Existent-Header"] } returns null
        }
        val response = Response(mockk(), mockHttpResponse)
        assertNull(response.header("Non-Existent-Header"))
    }
}