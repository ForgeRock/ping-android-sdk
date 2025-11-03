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
 * A callback for collecting a username.
 *
 * @property prompt The prompt for the username.
 * @property username The username input by the user.
 */
class ValidatedUsernameCallback : AbstractValidatedCallback() {
    var username: String = ""

    override fun init(name: String, value: JsonElement) {
        super.init(name, value)
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content
        }
    }

    override fun payload() = input(username, validateOnly)

}