/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class ValidatedUsernameCallback : AbstractValidatedCallback() {
    var prompt: String = ""
        private set

    var username: String = ""

    override fun onAttribute(name: String, value: JsonElement) {
        super.onAttribute(name, value)
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content
        }
    }

    override fun asJson() = input(username, inputValidateOnly)

}