/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.ValueCallback
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback for collecting a hidden value from a user.
 *
 * @property id The ID of the hidden value.
 * @property value The value of the hidden value.
 */
class HiddenValueCallback : ValueCallback, AbstractCallback() {

    override var id = ""
        private set

    override var value = ""

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "value" -> this.value = value.jsonPrimitive.content
            "id" -> this.id = value.jsonPrimitive.content
        }
    }

    override fun payload() = input(value)

}