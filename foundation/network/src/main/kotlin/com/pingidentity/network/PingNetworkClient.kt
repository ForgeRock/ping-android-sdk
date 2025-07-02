/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse

/**
 * Default implementation of [NetworkClient] using Ktor.
 *
 * @param config The configuration for the network client.
 */
class PingNetworkClient(
    private val config: NetworkClientConfig
) : NetworkClient {
    private val httpClient: HttpClient = createHttpClient()

    private fun createHttpClient(): HttpClient {
        // Use the provided client provider or the default
        val provider = config.httpClientProvider ?: DefaultHttpClientProvider()
        return provider.createClient(config)
    }

    override suspend fun request(block: HttpRequestBuilder.() -> Unit): HttpResponse {
        val requestBuilder = HttpRequestBuilder().apply(block)
        
        // Apply request interceptors
        val modifiedRequest = config.requestInterceptors.fold(requestBuilder) { req, interceptor ->
            interceptor.intercept(req)
        }
        
        // Execute the request
        var response = httpClient.request(modifiedRequest)
        
        // Apply response interceptors
        for (interceptor in config.responseInterceptors) {
            response = interceptor.intercept(response)
        }
        
        return response
    }

    override suspend fun <T> requestBody(block: HttpRequestBuilder.() -> Unit): T {
        val response = request(block)
        @Suppress("UNCHECKED_CAST")
        return response.body<String>() as T
    }

    override fun close() {
        httpClient.close()
    }
}
