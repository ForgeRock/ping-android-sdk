/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

import com.pingidentity.logger.None
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging

/**
 * Default implementation of [HttpClientProvider] that creates a Ktor HTTP client.
 */
class DefaultHttpClientProvider : HttpClientProvider {
    override fun createClient(config: NetworkClientConfig): HttpClient {
        return HttpClient(CIO) {
            // Configure the client based on the provided configuration
            followRedirects = config.followRedirects
            
            // Add logging if a logger is provided
            if (config.logger !is None) {
                install(Logging) {
                    logger = object : io.ktor.client.plugins.logging.Logger {
                        override fun log(message: String) {
                            config.logger.d(message)
                        }
                    }
                    level = LogLevel.ALL
                }
            }
            
            // Add timeout
            install(HttpTimeout) {
                requestTimeoutMillis = config.timeout
            }
            
            // Add default headers
            install(DefaultRequest) {
                headers.append(Headers.X_REQUESTED_WITH, Headers.ANDROID_VALUE)
                headers.append(Headers.X_REQUESTED_PLATFORM, Headers.ANDROID_PLATFORM)
            }
        }
    }
}
