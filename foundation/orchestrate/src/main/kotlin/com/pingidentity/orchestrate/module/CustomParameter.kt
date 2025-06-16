/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate.module

import com.pingidentity.orchestrate.Module
import com.pingidentity.utils.PingDsl

/**
 * Configuration class for CustomParameter.
 * Allows adding custom parameter to be injected into requests.
 */
@PingDsl
class CustomParameterConfig {
    internal val parameters = mutableListOf<Pair<String, String>>()

    /**
     * Adds a custom parameter to the configuration.
     * @param name The name of the parameter.
     * @param value The value of the parameter.
     */
    fun parameter(name: String, value: String) {
        parameters.add(Pair(name, value))
    }
}

/**
 * Module for injecting custom parameters into requests.
 */
val CustomParameter =
    Module.of(::CustomParameterConfig) {

        /**
         * Intercepts all send requests and injects custom parameters.
         * @return The modified request with custom parameters.
         */
        next { _, request ->
            config.parameters.forEach { (name, value) ->
                request.parameter(name, value)
            }
            request
        }

        /**
         * Adds custom parameters at the start of the request.
         * @return The modified request with custom parameters.
         */
        start {
            config.parameters.forEach { (name, value) ->
                it.parameter(name, value)
            }
            it
        }
    }