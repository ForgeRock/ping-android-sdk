/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback for collecting a string attribute.
 *
 * @property value The string attribute input by the user.
 */
class StringAttributeInputCallback : AttributeInputCallback() {
    var value: String = ""

    override fun init(name: String, value: JsonElement) {
        super.init(name, value)
        if ("value" == name) {
            this.value = value.jsonPrimitive.content
        }
    }

    override fun payload() = input(value, validateOnly)

}