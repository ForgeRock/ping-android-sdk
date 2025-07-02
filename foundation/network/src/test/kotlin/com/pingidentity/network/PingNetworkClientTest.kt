/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import io.mockk.mockk
import io.mockk.verify
import io.mockk.coVerify
import io.mockk.every
import io.mockk.coEvery
import io.mockk.slot

class PingNetworkClientTest {

    private lateinit var mockHttpClientProvider: HttpClientProvider
    private lateinit var mockRequestInterceptor: RequestInterceptor
    private lateinit var mockResponseInterceptor: ResponseInterceptor
    private lateinit var config: NetworkClientConfig
    private lateinit var client: PingNetworkClient

    @Before
    fun setup() {
        mockHttpClientProvider = mockk()
        mockRequestInterceptor = mockk()
        mockResponseInterceptor = mockk()
        
        // Create a mock HTTP client that returns a predefined response
        val mockEngine = MockEngine { request ->
            respond(
                content = "Response Content",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        
        val mockHttpClient = HttpClient(mockEngine)
        
        // Configure the mocks
        every { mockHttpClientProvider.createClient(any()) } returns mockHttpClient
        
        val requestSlot = slot<HttpRequestBuilder>()
        every { mockRequestInterceptor.intercept(capture(requestSlot)) } answers { requestSlot.captured }
        every { mockRequestInterceptor.priority } returns 0
        
        val responseSlot = slot<HttpResponse>()
        coEvery { mockResponseInterceptor.intercept(capture(responseSlot)) } answers { responseSlot.captured }
        every { mockResponseInterceptor.priority } returns 0
        
        // Create the configuration
        config = NetworkClientConfig().apply {
            httpClientProvider = mockHttpClientProvider
            addRequestInterceptor(mockRequestInterceptor)
            addResponseInterceptor(mockResponseInterceptor)
        }
        
        // Create the client
        client = PingNetworkClient(config)
    }

    @Test
    fun `test request calls interceptors`() = runBlocking {
        // Execute a request
        client.request {
            url {
                protocol = io.ktor.http.URLProtocol.HTTPS
                host = "example.com"
            }
            method = HttpMethod.Get
        }
        
        // Verify that the interceptors were called
        verify { mockRequestInterceptor.intercept(any()) }
        coVerify { mockResponseInterceptor.intercept(any()) }
    }

    @Test
    fun `test requestBody returns body`() = runBlocking {
        // Execute a request
        val response: String = client.requestBody {
            url {
                protocol = io.ktor.http.URLProtocol.HTTPS
                host = "example.com"
            }
            method = HttpMethod.Get
        }
        
        // Verify the response
        assertEquals("Response Content", response)
    }
}
