/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import com.pingidentity.device.profile.DeviceProfileConfig
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

class DeviceProfileCollector(
    private val config: DeviceProfileConfig,
) : DeviceCollector<DeviceProfileResult> {
    override val key: String = ""
    override val serializer: KSerializer<DeviceProfileResult> = DeviceProfileResult.serializer()

    override suspend fun collect(): DeviceProfileResult {
        config.collectors.forEach { if (it is LoggerAware) it.logger = config.logger }

        return DeviceProfileResult(
            identifier = config.deviceIdentifier.id.invoke(),
            metadata = if (config.metadata) config.collectors.collect() else null,
            location = if (config.location) LocationCollector().collect() else null,
        )
    }
}

/**
 * Internal data class representing the structure of collected device profile data.
 *
 * This class is used internally to serialize device profile information into
 * the expected JSON format for transmission to the server.
 *
 * @property identifier The unique device identifier string
 * @property metadata The collected metadata as a JSON element, containing
 *                   device-specific information gathered by the collectors
 */
@OptIn(InternalSerializationApi::class)
@Serializable
data class DeviceProfileResult(
    val identifier: String,
    val metadata: JsonElement? = null,
    val location: LocationInfo? = null,
)