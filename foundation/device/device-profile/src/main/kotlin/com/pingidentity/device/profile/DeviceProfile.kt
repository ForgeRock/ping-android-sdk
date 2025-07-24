/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile

import com.pingidentity.device.id.DefaultDeviceIdentifier
import com.pingidentity.device.profile.collector.DefaultDeviceCollector
import com.pingidentity.device.profile.collector.DeviceCollector
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
    val json = Json

    val config =  DeviceProfileConfig().apply(block)

    val metadataMap = config.collectors.mapNotNull { collector ->
        @Suppress("UNCHECKED_CAST")
        collector as DeviceCollector<Any?>
        val data = collector.collect()
        data?.let {
            collector.key to json.encodeToJsonElement(collector.serializer, data)
        }
    }.toMap()
    val result = DeviceProfileResult(
        identifier = config.deviceIdentifier.id,
        metadata = metadataMap
    )

    return json.encodeToJsonElement(result).jsonObject

}

@Serializable
private data class DeviceProfileResult(
    val identifier: String,
    val metadata: Map<String, JsonElement>
)