/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network.extensions

import com.pingidentity.network.NetworkClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse

/**
 * Converts a Ktor [HttpClient] to a [NetworkClient].
 * This is useful for developers who want to use their own HTTP client but still
 * want to use the SDK's network interfaces.
 *
 * @return A [NetworkClient] that delegates to this [HttpClient].
 */
fun HttpClient.toPingNetworkClient(): NetworkClient {
    return object : NetworkClient {
        override suspend fun request(block: HttpRequestBuilder.() -> Unit): HttpResponse {
            return this@toPingNetworkClient.request(block)
        }
        
        override suspend fun <T> requestBody(block: HttpRequestBuilder.() -> Unit): T {
            @Suppress("UNCHECKED_CAST")
            return this@toPingNetworkClient.request(block).body<String>() as T
        }
        
        override fun close() {
            this@toPingNetworkClient.close()
        }
    }
}
