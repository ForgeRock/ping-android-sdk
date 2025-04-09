/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Submittable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A collector for device authentication.
 */
class DeviceAuthenticationCollector : FieldCollector<JsonObject>(), Submittable {

    override fun eventType(): String = "submit"

    // The list of devices available for authentication.
    lateinit var devices: List<Device>
        private set

    var value: Device? = null

    override fun init(input: JsonObject) {
        super.init(input)
        devices = Device.devices(input)
    }

    override fun payload(): JsonObject? {
        value?.let {
            return buildJsonObject {
                put("type", it.type)
                put("id", it.id)
                put("value", it.value)
            }
        } ?: return null
    }

}
