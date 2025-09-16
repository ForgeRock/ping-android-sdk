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
import com.pingidentity.device.profile.collector.DeviceProfileCollector
import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.Journey
import com.pingidentity.journey.plugin.JourneyAware
import com.pingidentity.logger.Logger
import com.pingidentity.logger.WARN
import com.pingidentity.utils.PingDsl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback implementation for collecting and processing device profile information.
 *
 * This callback is used within the Ping Identity journey framework to gather device metadata
 * and location information based on server configuration. It extends AbstractCallback and
 * implements LoggerAware for logging capabilities.
 */
class DeviceProfileCallback : AbstractCallback(), JourneyAware {
    /**
     * Indicates whether metadata collection is enabled for this callback.
     * This value is set during initialization based on server configuration.
     */
    var metadata: Boolean = false
        private set

    /**
     * Indicates whether location collection is enabled for this callback.
     * This value is set during initialization based on server configuration.
     */
    var location: Boolean = false
        private set

    /**
     * A message from the server, typically containing instructions or information
     * about the device profile collection process.
     */
    var message: String = ""
        private set

    override lateinit var journey: Journey

    /**
     * Initializes callback properties based on server-provided configuration.
     *
     * This method is called automatically during callback initialization to set up
     * the callback based on the server's requirements for device profiling.
     *
     * @param name The name of the property being initialized
     * @param value The JSON value containing the property configuration
     */
    override fun init(name: String, value: JsonElement) {
        when (name) {
            "metadata" -> metadata = value.jsonPrimitive.content.toBoolean()
            "location" -> location = value.jsonPrimitive.content.toBoolean()
            "message" -> message = value.jsonPrimitive.content
        }
    }

    /**
     * Collects device profile information and submits it to the server.
     *
     * This method executes the device profile collection process using the provided
     * configuration block. If metadata collection is enabled, the collected profile
     * is automatically submitted as input to the journey.
     *
     * @param block A configuration block for customizing the device profile collection.
     *              Defaults to [DefaultProfile] if not provided.
     * @return A [Result] containing the collected device profile as a [JsonObject],
     *         or an error if collection fails.
     */
    suspend fun collect(block: DeviceProfileConfig.() -> Unit = {}): Result<JsonObject> {
        val config = DeviceProfileConfig()
        config.metadata = metadata
        config.location = location
        
        config.apply(block)

        val result = DeviceProfileCollector(config)
        json = Json.encodeToJsonElement(result.collect()).jsonObject
        input(json.toString())
        return Result.success(json)
    }
}

/**
 * Configuration class for customizing device profile collection behavior.
 *
 * This DSL-enabled class allows fine-grained control over the device profile
 * collection process, including logger configuration, device identification,
 * and selection of specific data collectors.
 */
@PingDsl
class DeviceProfileConfig {
    /**
     * Indicates whether metadata collection is enabled for this callback.
     * This value is set during initialization based on server configuration.
     */
    var metadata: Boolean = false

    /**
     * Indicates whether location collection is enabled for this callback.
     * This value is set during initialization based on server configuration.
     */
    var location: Boolean = false

    /**
     * Logger instance used for logging during device profile collection.
     * Defaults to [Logger.WARN] level logging.
     */
    var logger: Logger = Logger.WARN

    /**
     * Device identifier implementation used to uniquely identify the device.
     * Defaults to [DefaultDeviceIdentifier].
     */
    var deviceIdentifier: DeviceIdentifier = DefaultDeviceIdentifier

    /**
     * Mutable list of device collectors that will gather specific device information.
     * Add collectors to this list to customize what device data is collected.
     */
    val collectors: MutableList<DeviceCollector<*>> = mutableListOf()

    /**
     * DSL function for configuring metadata collectors.
     *
     * Use this function to specify which device collectors should be used
     * for gathering device metadata during the profile collection process.
     *
     * @param block A configuration block that operates on the collectors list
     */
    fun metadata(block: MutableList<DeviceCollector<*>>.() -> Unit) {
        collectors.apply(block)
    }
}