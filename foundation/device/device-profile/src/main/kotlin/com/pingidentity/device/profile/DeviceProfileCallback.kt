/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.device.profile

import com.pingidentity.device.id.DefaultDeviceIdentifier
import com.pingidentity.device.id.DeviceIdentifier
import com.pingidentity.device.profile.collector.DefaultDeviceCollector
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
 * A callback implementation for collecting and processing device profile information during authentication flows.
 *
 * This callback is used within the Ping Identity journey framework to gather comprehensive device metadata
 * and location information based on server-side configuration. It provides a flexible, DSL-based approach
 * for customizing the device profiling process while maintaining security and privacy compliance.
 *
 * **Key Features:**
 * - Automatic server configuration parsing for metadata and location collection flags
 * - Extensible collector system for gathering specific device characteristics
 * - DSL-based configuration for custom collection scenarios
 * - Built-in error handling and logging capabilities
 * - Seamless integration with Ping Identity authentication journeys
 *
 * **Usage Example:**
 * ```kotlin
 * // Basic collection with server configuration
 * val result = deviceProfileCallback.collect()
 *
 * // Custom collection with specific collectors
 * val result = deviceProfileCallback.collect {
 *     collectors {
 *         add(PlatformCollector)
 *         add(HardwareCollector())
 *         add(LocationCollector())
 *     }
 *     logger = Logger.DEBUG
 * }
 * ```
 *
 * **Server Configuration:**
 * The callback expects server-side configuration with the following properties:
 * - `metadata`: Boolean flag indicating whether device metadata should be collected
 * - `location`: Boolean flag indicating whether location data should be collected
 * - `message`: Optional message containing instructions or context information
 *
 * @since 1.0
 * @see AbstractCallback
 * @see JourneyAware
 * @see DeviceProfileConfig
 * @see DeviceProfileCollector
 */
class DeviceProfileCallback : AbstractCallback(), JourneyAware {
    /**
     * Indicates whether metadata collection is enabled for this callback.
     *
     * This value is automatically set during callback initialization based on the server's
     * configuration. When `true`, the callback will collect comprehensive device metadata
     * including hardware specifications, platform information, and system characteristics.
     *
     * @see init
     */
    var metadata: Boolean = false
        private set

    /**
     * Indicates whether location collection is enabled for this callback.
     *
     * This value is automatically set during initialization based on server configuration.
     * When `true`, the callback will attempt to collect GPS coordinates and location data,
     * subject to user permissions and privacy settings.
     *
     * **Privacy Note:** Location data collection requires appropriate user permissions
     * and should comply with applicable privacy regulations.
     *
     * @see init
     */
    var location: Boolean = false
        private set

    /**
     * A message from the server providing context or instructions for device profile collection.
     *
     * This message can contain information about why device profiling is being performed,
     * user instructions, or other contextual information that may be displayed in the UI
     * or used for logging purposes.
     */
    var message: String = ""
        private set

    override lateinit var journey: Journey

    /**
     * Initializes callback properties based on server-provided configuration.
     *
     * This method is called automatically by the Journey framework during callback initialization
     * to configure the callback based on the server's requirements for device profiling.
     * It parses JSON configuration values and sets the appropriate internal state.
     *
     * **Supported Configuration Properties:**
     * - `metadata`: Boolean value enabling/disabling metadata collection
     * - `location`: Boolean value enabling/disabling location collection
     * - `message`: String value containing contextual information
     *
     * @param name The name of the property being initialized
     * @param value The JSON value containing the property configuration
     * @throws IllegalArgumentException if the property value cannot be parsed correctly
     */
    override fun init(name: String, value: JsonElement) {
        when (name) {
            "metadata" -> metadata = value.jsonPrimitive.content.toBoolean()
            "location" -> location = value.jsonPrimitive.content.toBoolean()
            "message" -> message = value.jsonPrimitive.content
        }
    }

    /**
     * Collects comprehensive device profile information and submits it to the authentication server.
     *
     * This method orchestrates the complete device profile collection process using the provided
     * configuration block. It creates a DeviceProfileCollector with the specified configuration,
     * executes the collection process, and automatically submits the results to the journey.
     *
     * **Collection Process:**
     * 1. Creates and configures a DeviceProfileConfig with server settings
     * 2. Applies any custom configuration provided in the block parameter
     * 3. Instantiates a DeviceProfileCollector with the final configuration
     * 4. Executes the collection process asynchronously
     * 5. Serializes the collected data to JSON format
     * 6. Submits the profile data to the authentication journey
     *
     * **Configuration Options:**
     * The configuration block allows customization of:
     * - Device collectors to be used for data gathering
     * - Logger instance for debugging and monitoring
     * - Device identifier implementation for unique device identification
     * - Custom collection parameters and settings
     *
     * **Example Usage:**
     * ```kotlin
     * // Basic collection with default settings
     * val result = collect()
     *
     * // Custom collection configuration
     * val result = collect {
     *     collectors {
     *         clear()
     *         add(PlatformCollector)
     *         add(HardwareCollector())
     *         add(NetworkCollector())
     *     }
     *     logger = Logger.DEBUG
     *     deviceIdentifier = CustomDeviceIdentifier()
     * }
     * ```
     *
     * @param block A configuration block for customizing the device profile collection process.
     *              Uses default configuration if not provided.
     * @return A [Result] containing the collected device profile as a [JsonObject] on success,
     *         or an error result if collection fails due to system limitations or permissions.
     *
     * @throws SecurityException if required permissions are not granted for certain collectors
     * @throws IllegalStateException if the callback has not been properly initialized
     *
     * @since 1.0
     * @see DeviceProfileConfig
     * @see DeviceProfileCollector
     */
    suspend fun collect(block: DeviceProfileConfig.() -> Unit = { }): Result<JsonObject> {
        val config = DeviceProfileConfig()
        config.metadata = metadata
        config.location = location

        config.apply(block)

        val result = DeviceProfileCollector(config)
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val profile = json.encodeToJsonElement(result.collect()).jsonObject

        input(profile.toString())
        return Result.success(profile)
    }
}

/**
 * Configuration class for customizing device profile collection behavior using a DSL approach.
 *
 * This class provides a comprehensive configuration interface for the device profile collection
 * process, allowing fine-grained control over data gathering, logging, device identification,
 * and collector selection.
 *
 * **Configuration Categories:**
 *
 * **Collection Flags:**
 * - Metadata collection enablement
 * - Location collection enablement
 *
 * **System Components:**
 * - Logger configuration for debugging and monitoring
 * - Device identifier implementation for unique device tracking
 *
 * **Data Collectors:**
 * - Extensible list of collectors for gathering specific device information
 * - DSL-based collector configuration
 *
 * **Usage Examples:**
 * ```kotlin
 * // Basic configuration
 * val config = DeviceProfileConfig().apply {
 *     metadata = true
 *     location = false
 * }
 *
 * // Advanced configuration with custom collectors
 * val config = DeviceProfileConfig().apply {
 *     logger = Logger.DEBUG
 *     deviceIdentifier = CustomIdentifier()
 *     collectors {
 *         add(PlatformCollector)
 *         add(HardwareCollector())
 *         add(LocationCollector())
 *     }
 * }
 * ```
 *
 * @since 1.0
 * @see DeviceProfileCallback.collect
 * @see DeviceProfileCollector
 * @see DeviceCollector
 */
@PingDsl
class DeviceProfileConfig {
    /**
     * Indicates whether comprehensive device metadata collection is enabled.
     *
     * When `true`, the collection process will gather detailed device information including:
     * - Hardware specifications (CPU, memory, storage, display)
     * - Platform information (OS version, device model, manufacturer)
     * - System capabilities (camera, sensors, connectivity)
     * - Software environment (build information, security settings)
     *
     * This flag is typically set based on server configuration but can be overridden
     * in custom collection scenarios.
     */
    var metadata: Boolean = false

    /**
     * Indicates whether device location data collection is enabled.
     *
     * When `true`, the collection process will attempt to gather GPS coordinates
     * and location-based information, subject to:
     * - User permission grants for location access
     * - Device location service availability
     * - Privacy policy compliance requirements
     *
     * **Privacy Considerations:**
     * Location data is considered sensitive personal information and should only be
     * collected when explicitly required and with proper user consent.
     */
    var location: Boolean = false

    /**
     * Logger instance used for monitoring and debugging during device profile collection.
     *
     * The logger captures important events, errors, and diagnostic information throughout
     * the collection process. Different log levels can be configured based on deployment
     * environment and debugging requirements.
     *
     * **Default:** [Logger.w] - Captures warnings and errors only
     * **Debug:** [Logger.d] - Detailed collection process information
     * **Production:** [Logger.e] - Critical errors only
     */
    var logger: Logger = Logger.WARN

    /**
     * Device identifier implementation used to generate unique device fingerprints.
     *
     * The device identifier creates consistent, unique identifiers for devices across
     * application sessions while maintaining user privacy. Different implementations
     * may use various strategies such as:
     * - Hardware-based fingerprinting
     * - Secure random identifier generation
     * - Cryptographic key-based identification
     *
     * **Default:** [DefaultDeviceIdentifier] - Standard implementation using device characteristics
     */
    var deviceIdentifier: DeviceIdentifier = DefaultDeviceIdentifier

    /**
     * Mutable list of device collectors responsible for gathering specific categories of device information.
     *
     * Each collector in this list specializes in gathering particular types of device data:
     * - [com.pingidentity.device.profile.collector.PlatformCollector]: OS and platform information
     * - [com.pingidentity.device.profile.collector.HardwareCollector]: Hardware specifications and capabilities
     * - [com.pingidentity.device.profile.collector.NetworkCollector]: Connectivity and network status
     * - [com.pingidentity.device.profile.collector.LocationCollector]: GPS coordinates and location data
     * - Custom collectors: Application-specific data gathering
     *
     * Collectors are executed in the order they appear in this list, and their results
     * are combined into the final device profile.
     */
    internal val collectors: MutableList<DeviceCollector<*>> = mutableListOf<DeviceCollector<*>>().apply(DefaultDeviceCollector())

    /**
     * DSL function for configuring the list of device collectors to be used during profile collection.
     *
     * This function provides a clean, readable syntax for specifying which collectors should
     * be used for gathering device metadata. The configuration block operates directly on
     * the collectors list, allowing for intuitive collector management.
     *
     * **Usage Examples:**
     * ```kotlin
     * collectors {
     *     // Add standard collectors
     *     add(PlatformCollector)
     *     add(HardwareCollector())
     *
     *     // Add conditional collectors
     *     if (requiresLocation) {
     *         add(LocationCollector())
     *     }
     *
     *     // Add custom collectors
     *     add(CustomApplicationCollector())
     * }
     * ```
     *
     * **Collector Categories:**
     * - **Platform Collectors**: OS version, device model, system information
     * - **Hardware Collectors**: CPU, memory, storage, display specifications
     * - **Connectivity Collectors**: Network status, Bluetooth, telephony information
     * - **Location Collectors**: GPS coordinates, location services
     * - **Custom Collectors**: Application-specific data gathering
     *
     * @param block A configuration block that operates on the collectors list, providing
     *              DSL-style collector configuration capabilities
     *
     * @since 1.0
     * @see DeviceCollector
     * @see collectors
     */
    fun collectors(block: MutableList<DeviceCollector<*>>.() -> Unit) {
        collectors.apply(block)
    }
}