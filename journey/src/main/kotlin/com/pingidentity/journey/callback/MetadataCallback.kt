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


class MetadataCallback : AbstractCallback(), DerivableCallback {

    var value: JsonObject = buildJsonObject { }
        private set

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "data" -> this.value = value.jsonObject
        }
    }

    override val derivedCallback: String?
        get() {
            CallbackRegistry.derivedCallbacks.forEach {
                it(value)?.let {
                    return it
                }
            }
            return null
        }

}