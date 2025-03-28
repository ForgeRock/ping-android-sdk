/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

abstract class AttributeInputCallback : AbstractValidatedCallback() {
    var prompt = ""
        private set
    var name = ""
        private set
    var required = false
        private set

    override fun onAttribute(name: String, value: JsonElement) {
        super.onAttribute(name, value)
        when (name) {
            "name" -> this.name = value.jsonPrimitive.content
            "prompt" -> this.prompt = value.jsonPrimitive.content
            "required" -> this.required = value.jsonPrimitive.boolean
        }
    }
}