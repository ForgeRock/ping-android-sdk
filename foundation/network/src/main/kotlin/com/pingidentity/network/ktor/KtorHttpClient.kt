/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network.ktor

import com.pingidentity.logger.None
import com.pingidentity.network.HttpClient
import com.pingidentity.network.HttpClientConfig
import com.pingidentity.network.HttpRequest
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.request
import io.ktor.client.statement.request
import kotlin.time.DurationUnit
import io.ktor.client.HttpClient as KtorClient

/**
 * Creates a default KtorHttpClient with CIO engine and standard configuration.
 *
 * This factory function creates a Ktor HTTP client with sensible defaults including
 * disabled redirects, optional logging, and configurable timeout.
 *
 * @param config Configuration block for setting up the client.
 * @return A configured HttpClient instance using Ktor with CIO engine.
 *
 */
fun HttpClient(config: HttpClientConfig.() -> Unit): HttpClient {
    val configure = HttpClientConfig().apply(config)
    val ktorClient = KtorClient(CIO) {
        followRedirects = false

        if (configure.logger !is None) {
            install(Logging) {
                this.logger = object : io.ktor.client.plugins.logging.Logger {
                    override fun log(message: String) {
                        configure.logger.d(message)
                    }
                }
                level = LogLevel.ALL
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = configure.timeout.toLong(DurationUnit.MILLISECONDS)
        }

        for (interceptor in configure.requestInterceptors) {
            install(createClientPlugin(interceptor.toString()) {
                onRequest { request, _ ->
                    interceptor(KtorHttpRequest(request))
                }
            })
        }
        for (interceptor in configure.responseInterceptors) {
            install(createClientPlugin(interceptor.toString()) {
                onResponse { response ->
                    interceptor(
                        KtorHttpResponse(
                            KtorImmutableHttpRequest(response.request),
                            response
                        )
                    )
                }
            })
        }
    }

    return KtorHttpClient(ktorClient)
}


/**
 * Ktor-based HTTP client implementation using the CIO engine.
 *
 * This class implements the HttpClient interface using Ktor's HTTP client library with
 * the CIO (Coroutine-based I/O) engine. It provides a bridge between the SDK's generic
 * HttpClient interface and Ktor's powerful HTTP client capabilities.
 *
 * The CIO engine is a pure Kotlin implementation that:
 * - Uses Kotlin Coroutines for asynchronous operations
 * - Has minimal external dependencies
 * - Is lightweight and efficient
 * - Works across all platforms (JVM, Android, Native)
 *
 * @property ktorClient The underlying Ktor HttpClient instance.
 *
 * @see HttpClient
 * @see KtorHttpRequest
 * @see KtorHttpResponse
 */
class KtorHttpClient(
    private val ktorClient: KtorClient
) : HttpClient {

    /**
     * Creates a new KtorHttpRequest instance.
     *
     * This factory method creates a new request object that can be configured
     * with URL, headers, parameters, and body before being sent.
     *
     * @return A new KtorHttpRequest instance.
     *
     */
    override fun request(): KtorHttpRequest {
        return KtorHttpRequest()
    }

    /**
     * Sends an HTTP request and returns the response.
     *
     * This method takes a fully configured KtorHttpRequest, converts it to a Ktor
     * request, executes it using the underlying Ktor client, and wraps the response
     * in a KtorHttpResponse.
     *
     * @param request The HTTP request to send. Must be a KtorHttpRequest instance.
     * @return The HTTP response wrapped in a KtorHttpResponse.
     * @throws Exception if the request fails, times out, or encounters a network error.
     *
     */
    override suspend fun request(request: HttpRequest): KtorHttpResponse {
        require(request is KtorHttpRequest)
        val ktorResponse = ktorClient.request(request.builder)
        return KtorHttpResponse(request, ktorResponse)
    }

    /**
     * Sends an HTTP request built with a builder lambda and returns the response.
     *
     * This is the most convenient way to make HTTP requests. It creates a new request,
     * applies the builder lambda to configure it, and then sends it.
     *
     * @param requestBuilder A lambda that configures the request using DSL syntax.
     * @return The HTTP response wrapped in a KtorHttpResponse.
     * @throws Exception if the request fails, times out, or encounters a network error.
     *
     */
    override suspend fun request(requestBuilder: HttpRequest.() -> Unit): KtorHttpResponse {
        val request = KtorHttpRequest()
        request.apply(requestBuilder)
        return request(request)
    }

    /**
     * Closes the underlying Ktor HTTP client and releases all resources.
     *
     * This method should be called when the client is no longer needed to properly
     * clean up connections, thread pools, and other resources. After calling close(),
     * the client should not be used anymore.
     *
     */
    override fun close() {
        ktorClient.close()
    }
}

