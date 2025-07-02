/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

import io.ktor.client.HttpClient

/**
 * Interface for providing HTTP clients.
 * This allows developers to provide their own HTTP client implementations.
 */
interface HttpClientProvider {
    /**
     * Creates and returns an HTTP client with the given configuration.
     *
     * @param config The configuration for the HTTP client.
     * @return The configured HTTP client.
     */
    fun createClient(config: NetworkClientConfig): HttpClient
}
