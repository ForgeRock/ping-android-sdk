/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse

/**
 * Interface for performing network operations.
 * This is the main interface that modules should use to make network requests.
 */
interface NetworkClient {
    /**
     * Executes a network request and returns the response.
     *
     * @param block The request builder block.
     * @return The response from the request.
     */
    suspend fun request(block: HttpRequestBuilder.() -> Unit): HttpResponse

    /**
     * Executes a network request and returns the response body.
     *
     * @param block The request builder block.
     * @return The response body from the request.
     */
    suspend fun <T> requestBody(block: HttpRequestBuilder.() -> Unit): T
    
    /**
     * Closes the client and releases resources.
     */
    fun close()
}
