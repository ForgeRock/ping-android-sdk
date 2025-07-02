/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

/**
 * Factory for creating [NetworkClient] instances.
 */
object NetworkClientFactory {
    /**
     * Creates a new [NetworkClient] with the given configuration.
     *
     * @param config The configuration block for the client.
     * @return The configured network client.
     */
    fun create(config: NetworkClientConfig.() -> Unit = {}): NetworkClient {
        val clientConfig = NetworkClientConfig().apply(config)
        return PingNetworkClient(clientConfig)
    }
}
