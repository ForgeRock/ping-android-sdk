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
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback for collecting a password.
 *
 * @property prompt The prompt for the password.
 * @property password The password input by the user.
 */
class PasswordCallback : AbstractCallback() {
    var prompt: String = ""
        private set

    //Input
    var password: String = ""

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content
        }
    }


    override fun payload(): JsonObject = input(password)

}