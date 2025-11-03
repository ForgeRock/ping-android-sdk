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

/**
 * Base implementation of a Callback for collection of a single identity object attribute from a user.
 * @property prompt A string to display to the end-user to describe this input
 * @property name The displayable name of the attribute.
 * @property required Whether the attribute is required or not.
 */
abstract class AttributeInputCallback : AbstractValidatedCallback() {
    var name = ""
        private set
    var required = false
        private set

    override fun init(name: String, value: JsonElement) {
        super.init(name, value)
        when (name) {
            "name" -> this.name = value.jsonPrimitive.content
            "prompt" -> this.prompt = value.jsonPrimitive.content
            "required" -> this.required = value.jsonPrimitive.boolean
        }
    }
}