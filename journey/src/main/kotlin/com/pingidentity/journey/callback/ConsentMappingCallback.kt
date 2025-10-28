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
 * A callback for collecting consent mapping information from a user.
 *
 * @property name The name of the consent mapping.
 * @property displayName The display name of the consent mapping.
 * @property icon The icon associated with the consent mapping.
 * @property accessLevel The access level required for the consent mapping.
 * @property isRequired Whether the consent mapping is required.
 * @property fields The list of fields associated with the consent mapping.
 * @property message The message to be displayed to the user.
 * @property accepted Whether the user accepts the consent mapping.
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

    var accepted = false

    override fun init(name: String, value: JsonElement) {
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

    override fun payload(): JsonObject {
        return input(accepted)
    }

}