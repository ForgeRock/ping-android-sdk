/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.device.profile

import android.annotation.SuppressLint
import com.pingidentity.device.id.DefaultDeviceIdentifier
import com.pingidentity.device.profile.collector.DefaultDeviceCollector
import com.pingidentity.device.profile.collector.LoggerAware
import com.pingidentity.device.profile.collector.collect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Creates a default configuration for device profile collection.
 *
 * This function provides a standard configuration that includes the default device identifier
 * and the default device collector for metadata gathering. It serves as a sensible default
 * for most device profiling scenarios.
 *
 * @return A configuration block that sets up default device profiling behavior
 */
fun DefaultProfile() : DeviceProfileConfig.() -> Unit  {
    return {
        deviceIdentifier = DefaultDeviceIdentifier
        metadata( DefaultDeviceCollector() )
    }
}

/**
 * Collects device profile information based on the provided configuration.
 *
 * This suspend function orchestrates the device profile collection process by:
 * 1. Applying the provided configuration
 * 2. Setting up loggers for all collectors that support logging
 * 3. Gathering device identifier and metadata using configured collectors
 * 4. Serializing the results into a JSON object
 *
 * @param block Configuration block for customizing device profile collection.
 *              Defaults to [DefaultProfile] if not provided.
 * @return A [JsonObject] containing the collected device profile data with
 *         identifier and metadata fields
 */
suspend fun profile(block: DeviceProfileConfig.() -> Unit = DefaultProfile()): JsonObject {
    val config =  DeviceProfileConfig().apply(block)

    config.collectors.forEach { (it as? LoggerAware)?.logger = config.logger }

    val result = DeviceProfileResult(
        identifier = config.deviceIdentifier.id.invoke(),
        metadata = config.collectors.collect()
    )

    return json.encodeToJsonElement(result).jsonObject
}

/**
 * Internal JSON serializer configuration for device profile serialization.
 *
 * Configured to ignore unknown keys during deserialization to maintain
 * compatibility with different server response formats.
 */
internal val json = Json {
    ignoreUnknownKeys = true
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
@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class DeviceProfileResult(
    val identifier: String,
    val metadata: JsonElement
)
