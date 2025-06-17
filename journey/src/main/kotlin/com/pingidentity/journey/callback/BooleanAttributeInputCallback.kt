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
 * A callback for collecting a boolean attribute from a user.
 * @property value The value of the attribute.
 * @property validateOnly Whether to validate only or not.
 */
class BooleanAttributeInputCallback : AttributeInputCallback() {
    var value = false

    override fun init(name: String, value: JsonElement) {
        super.init(name, value)
        if ("value" == name) {
            this.value = value.jsonPrimitive.boolean
        }
    }

    override fun payload() = input(value, validateOnly)


}