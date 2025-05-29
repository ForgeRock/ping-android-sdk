/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.CallbackRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A callback for providing metadata.
 *
 * @property value The metadata value.
 */
class MetadataCallback : AbstractCallback() {

    var value: JsonObject = buildJsonObject { }
        private set

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "data" -> this.value = value.jsonObject
        }
    }

}