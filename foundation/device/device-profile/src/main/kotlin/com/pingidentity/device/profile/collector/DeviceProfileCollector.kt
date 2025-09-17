/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.annotation.SuppressLint
import com.pingidentity.device.profile.DeviceProfileConfig
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A comprehensive device collector that orchestrates the collection of device profile data.
 * 
 * This collector serves as the main coordinator for gathering complete device profile information
 * including device identification, metadata, and location data based on the provided configuration.
 * It manages the collection process by:
 * - Setting up loggers for all configured collectors
 * - Invoking the device identifier to get a unique device ID
 * - Conditionally collecting metadata and location based on configuration flags
 * 
 * @property config The device profile configuration that specifies what data to collect
 *                  and how to collect it
 */
class DeviceProfileCollector(
    private val config: DeviceProfileConfig,
) : DeviceCollector<DeviceProfileResult> {
    /**
     * The key identifier for this collector. Empty string as this is the root collector.
     */
    override val key: String = ""
    
    /**
     * The serializer used to convert the collected data to JSON format.
     */
    override val serializer: KSerializer<DeviceProfileResult> = DeviceProfileResult.serializer()

    /**
     * Collects comprehensive device profile data based on the configuration.
     * 
     * This method orchestrates the entire device profile collection process:
     * 1. Configures loggers for all collectors that support logging
     * 2. Retrieves the unique device identifier
     * 3. Conditionally collects metadata if enabled in configuration
     * 4. Conditionally collects location if enabled in configuration
     * 
     * @return A [DeviceProfileResult] containing the collected device profile data
     */
    @SuppressLint("MissingPermission")
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
 * Data class representing the complete structure of collected device profile data.
 * 
 * This class encapsulates all the device profile information that can be collected,
 * including the device identifier, metadata about the device hardware and software,
 * and location information. It is serialized to JSON format for transmission to the server.
 * 
 * @property identifier The unique device identifier string used to identify this specific device
 * @property metadata Optional metadata as a JSON element, containing
 *  *                   device-specific information gathered by a collection of collectors.
 * @property location Optional location information containing latitude and longitude coordinates.
 *                   Only included if location collection is enabled in the configuration
 */
@OptIn(InternalSerializationApi::class)
@Serializable
data class DeviceProfileResult(
    val identifier: String,
    val metadata: JsonElement? = null,
    val location: LocationInfo? = null,
)