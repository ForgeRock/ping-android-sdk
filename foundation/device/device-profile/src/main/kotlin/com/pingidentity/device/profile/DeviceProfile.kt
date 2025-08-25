/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.device.profile

import com.pingidentity.device.id.DefaultDeviceIdentifier
import com.pingidentity.device.profile.collector.DefaultDeviceCollector
import com.pingidentity.device.profile.collector.collect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

fun DefaultProfile() : DeviceProfileConfig.() -> Unit  {
    return {
        deviceIdentifier = DefaultDeviceIdentifier
        metadata( DefaultDeviceCollector() )
    }
}

suspend fun profile(block: DeviceProfileConfig.() -> Unit = DefaultProfile()): JsonObject {
    val config =  DeviceProfileConfig().apply(block)
    val result = DeviceProfileResult(
        identifier = config.deviceIdentifier.id,
        metadata = config.collectors.collect()
    )

    return json.encodeToJsonElement(result).jsonObject

}

val json = Json

@Serializable
private data class DeviceProfileResult(
    val identifier: String,
    val metadata: JsonElement
)

