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
 * A callback for collecting a validated password from a user.
 *
 * @property prompt The prompt for the password.
 * @property echoOn Whether to echo the password input.
 * @property password The password input by the user.
 */
class ValidatedPasswordCallback : AbstractValidatedCallback() {
    var echoOn = false
        private set

    var password: String = ""

    override fun init(name: String, value: JsonElement) {
        super.init(name, value)
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content
            "echoOn" -> this.echoOn = value.jsonPrimitive.boolean
        }
    }

    override fun payload() = input(password, validateOnly)


}