/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive

class NumberAttributeInputCallback : AttributeInputCallback() {
    var value: Double = 0.0
    private set

    var inputValue : Double = 0.0

    override fun onAttribute(name: String, value: JsonElement) {
        super.onAttribute(name, value)
        if ("value" == name) {
            this.value = value.jsonPrimitive.double
        }
    }

    override fun asJson() = input(inputValue, inputValidateOnly)

}