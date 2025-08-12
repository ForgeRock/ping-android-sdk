/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.device.profile

import com.pingidentity.device.id.DefaultDeviceIdentifier
import com.pingidentity.device.id.DeviceIdentifier
import com.pingidentity.device.profile.collector.DeviceCollector
import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.utils.PingDsl
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class DeviceProfileCallback : AbstractCallback() {
    var metadata: Boolean = false
        private set
    var location: Boolean = false
        private set

    var message: String = ""
        private set

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "metadata" -> metadata = value.jsonPrimitive.content.toBoolean()
            "location" -> location = value.jsonPrimitive.content.toBoolean()
            "message" -> message = value.jsonPrimitive.content
        }
    }

    suspend fun collect(block: DeviceProfileConfig.() -> Unit = DefaultProfile()) {
        val json = Json {
            prettyPrint = true
        }
        val prettyString = json.encodeToString(profile(block))
        println(prettyString)
        if (metadata) {
            input(prettyString)
        }
    }
}

@PingDsl
class DeviceProfileConfig {
    var deviceIdentifier: DeviceIdentifier = DefaultDeviceIdentifier
    val collectors: MutableList<DeviceCollector<*>> = mutableListOf()

    fun metadata(block: MutableList<DeviceCollector<*>>.() -> Unit) {
        collectors.apply(block)
    }
}