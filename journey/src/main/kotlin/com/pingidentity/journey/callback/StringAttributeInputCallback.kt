/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class StringAttributeInputCallback : AttributeInputCallback() {
    var value: String = ""
    private set

    var inputValue = ""

    override fun onAttribute(name: String, value: JsonElement) {
        super.onAttribute(name, value)
        if ("value" == name) {
            this.value = value.jsonPrimitive.content
        }
    }

    override fun asJson() = input(inputValue, inputValidateOnly)

}