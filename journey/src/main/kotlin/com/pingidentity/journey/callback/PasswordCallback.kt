/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class PasswordCallback : AbstractCallback() {
    var prompt: String = ""
        private set

    //Input
    var password: String = ""

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content
        }
    }


    override fun asJson(): JsonObject = input(password)

}