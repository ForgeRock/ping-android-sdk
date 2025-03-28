/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class NameCallback : AbstractCallback() {
    var prompt: String = ""
        private set

    //Input
    var name: String = ""

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content
        }
    }

    override fun asJson() = input(name)

}