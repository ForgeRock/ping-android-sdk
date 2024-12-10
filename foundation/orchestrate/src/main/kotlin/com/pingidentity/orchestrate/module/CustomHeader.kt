/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate.module

import com.pingidentity.orchestrate.Module
import com.pingidentity.utils.PingDsl

/**
 * Configuration class for CustomHeader.
 * Allows adding custom headers to be injected into requests.
 */
@PingDsl
class CustomHeaderConfig {
    internal val headers = mutableListOf<Pair<String, String>>()

    /**
     * Adds a custom header to the configuration.
     * @param name The name of the header.
     * @param value The value of the header.
     */
    fun header(name: String, value: String) {
        headers.add(Pair(name, value))
    }
}

/**
 * Module for injecting custom headers into requests.
 */
val CustomHeader =
    Module.of(::CustomHeaderConfig) {

        /**
         * Intercepts all send requests and injects custom headers.
         * @param request The request to be modified.
         * @return The modified request with custom headers.
         */
        next { _, request ->
            config.headers.forEach { (name, value) ->
                request.header(name, value)
            }
            request
        }

        /**
         * Adds custom headers at the start of the request.
         * @param it The request to be modified.
         * @return The modified request with custom headers.
         */
        start {
            config.headers.forEach { (name, value) ->
                it.header(name, value)
            }
            it
        }
    }