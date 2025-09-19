/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.device.profile.collector

/**
 * Creates a default configuration of device collectors for gathering comprehensive device profile information.
 *
 * This function returns a configuration lambda that sets up a complete suite of device collectors
 * used by the Ping Identity SDK for device fingerprinting and profiling. The default configuration
 * provides a comprehensive device profile by collecting information across multiple categories.
 *
 * **Included Collectors:**
 *
 * **System & Platform Information:**
 * - [PlatformCollector]: Gathers Android OS version, device model, manufacturer, locale, timezone,
 *   and security information including jailbreak/root detection scores
 *
 * **Hardware Specifications:**
 * - [HardwareCollector]: Collects device hardware details including CPU cores, memory (RAM),
 *   storage capacity, display dimensions/orientation, and camera information
 *
 * @return A lambda function that takes a [MutableList] of [DeviceCollector] and adds the default collectors:
 *   - [PlatformCollector]: Collects OS and platform information
 *   - [HardwareCollector]: Collects device hardware specifications
 *   - [NetworkCollector]: Collects network connectivity status
 *   - [TelephonyCollector]: Collects telephony and carrier information
 *   - [LocationCollector]: Collects GPS location data (requires user permissions)
 *
 * **Connectivity & Network:**
 * - [NetworkCollector]: Determines current network connectivity status and connection type
 * - [BluetoothCollector]: Detects Bluetooth hardware capability and support
 * - [BrowserCollector]: Captures WebView user agent string for browser fingerprinting
 *
 * **Telephony & Carrier:**
 * - [TelephonyCollector]: Gathers carrier information, network country ISO, and telephony capabilities
 *
 * **Location Services:**
 * - [LocationCollector]: Collects GPS coordinates and location data (requires appropriate permissions)
 *
 * **Usage Examples:**
 *
 * Basic usage with default collectors:
 * ```kotlin
 * val collectors = mutableListOf<DeviceCollector<*>>()
 * collectors.DefaultDeviceCollector()
 * val deviceProfile = collectors.collect()
 * ```
 *
 * Combining with custom collectors:
 * ```kotlin
 * val collectors = mutableListOf<DeviceCollector<*>>()
 * collectors.DefaultDeviceCollector()
 * collectors.add(CustomCollector())
 * val deviceProfile = collectors.collect()
 * ```
 *
 * **Privacy Considerations:**
 * - Location data collection requires user permissions and should comply with privacy policies
 * - Some hardware information may be considered sensitive for device fingerprinting
 * - Telephony information may include carrier-specific details
 * - Browser data includes user agent strings that may contain identifying information
 *
 * **Performance Notes:**
 * - Collectors are designed to be lightweight and non-blocking
 * - Hardware collectors may perform system calls to gather specifications
 * - Location collection is the most resource-intensive and may require user interaction
 * - Results are typically cached to avoid repeated system queries
 *
 * @return A configuration lambda that accepts a [MutableList] of [DeviceCollector] and populates it
 *         with the complete set of default collectors for comprehensive device profiling
 *
 * @since 1.0
 * @see DeviceCollector
 * @see PlatformCollector
 * @see HardwareCollector
 * @see NetworkCollector
 * @see TelephonyCollector
 * @see LocationCollector
 * @see BluetoothCollector
 * @see BrowserCollector
 */
fun DefaultDeviceCollector(): MutableList<DeviceCollector<*>>.() -> Unit = {
    add(PlatformCollector)
    add(HardwareCollector())
    add(NetworkCollector())
    add(TelephonyCollector)
    add(LocationCollector())
    add(BluetoothCollector)
    add(BrowserCollector)
}