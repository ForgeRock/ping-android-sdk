/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Callback to collect an Identity Provider
 */
class SelectIdpCallback : AbstractCallback() {

    var providers = emptyList<IdPValue>()
    private set

    var value: String= ""

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "providers" -> {
                providers = value.jsonArray.map {
                    IdPValue(it.jsonObject)
                }
            }
        }
    }

    override fun asJson(): JsonObject {
        return input(value)
    }

    class IdPValue(jsonObject: JsonObject) {
        var provider: String = jsonObject["provider"]?.jsonPrimitive?.content ?: ""
        var uiConfig: JsonObject = jsonObject["uiConfig"]?.jsonObject ?: buildJsonObject {}
    }

}