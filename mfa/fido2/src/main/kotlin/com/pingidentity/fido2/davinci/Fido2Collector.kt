/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.davinci

import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.fido2.Constants
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Factory collector for FIDO2 operations in DaVinci workflows.
 *
 * This collector acts as a factory that determines the appropriate FIDO2 collector
 * to use based on the action specified in the input. It supports both registration
 * and authentication operations by delegating to specialized collector implementations.
 *
 * The collector examines the "action" field in the input JSON to determine whether
 * to create a [Fido2RegistrationCollector] or [Fido2AuthenticationCollector].
 */
open class Fido2Collector : Collector<JsonObject> {

    /**
     * Initializes and returns the appropriate FIDO2 collector based on the action type.
     *
     * This method examines the "action" field in the input JSON and creates the
     * corresponding collector:
     * - "REGISTER" creates a [Fido2RegistrationCollector]
     * - "AUTHENTICATE" creates a [Fido2AuthenticationCollector]
     *
     * @param input The JSON object containing the action and other initialization parameters
     * @return The appropriate FIDO2 collector instance for the specified action
     * @throws IllegalArgumentException if the action field is missing or not supported
     */
    override fun init(input: JsonObject): Collector<JsonObject> {
        val action = input[Constants.FIELD_ACTION]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("${Constants.FIELD_ACTION} is required")
        return when (action) {
            Constants.ACTION_REGISTER -> Fido2RegistrationCollector()
            Constants.ACTION_AUTHENTICATE -> Fido2AuthenticationCollector()
            else -> throw IllegalArgumentException("${Constants.FIELD_ACTION}: $action is not supported")
        }
    }
}