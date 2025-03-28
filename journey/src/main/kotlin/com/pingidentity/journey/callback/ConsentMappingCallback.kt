/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Callback to collect user's Consent.
 */
class ConsentMappingCallback : AbstractCallback() {
    var name = ""
        private set
    var displayName = ""
        private set
    var icon = ""
        private set
    var accessLevel = ""
        private set
    var isRequired = false
        private set
    var fields = emptyList<String>()
        private set
    var message = ""
        private set

    var accept = false

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "name" -> this.name = value.jsonPrimitive.content
            "displayName" -> this.displayName = value.jsonPrimitive.content
            "icon" -> this.icon = value.jsonPrimitive.content
            "accessLevel" -> this.accessLevel = value.jsonPrimitive.content
            "isRequired" -> this.isRequired = value.jsonPrimitive.boolean
            "fields" -> this.fields = value.jsonArray.map {
                it.jsonPrimitive.content
            }
            "message" -> this.message = value.jsonPrimitive.content
        }
    }

    override fun asJson(): JsonObject {
        return input(accept)
    }

}