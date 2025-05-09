/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.davinci

import com.pingidentity.davinci.plugin.Collector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A collector for FIDO2 actions.
 *
 * This class is responsible for initializing the appropriate FIDO2 collector based on the action type.
 * It delegates the initialization to either [Fido2RegistrationCollector] or [Fido2AuthenticationCollector].
 */
open class Fido2Collector : Collector<JsonObject> {

    /**
     * Base on the action, return the appropriate Fido2 collector.
     */
    override fun init(input: JsonObject): Collector<JsonObject> {
        val action = input["action"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("action is required")
        return when (action) {
            "REGISTER" -> Fido2RegistrationCollector()
            "AUTHENTICATE" -> Fido2AuthenticationCollector()
            else -> throw IllegalArgumentException("action: $action is not supported")
        }
    }
}