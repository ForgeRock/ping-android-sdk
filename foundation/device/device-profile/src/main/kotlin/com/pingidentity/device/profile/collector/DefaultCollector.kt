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
 * This function returns a lambda that configures a mutable list with the standard set of device collectors
 * used by the Ping Identity SDK. The collectors gather information about platform details, hardware specs,
 * network connectivity, telephony capabilities, and location data.
 *
 * @return A lambda function that takes a [MutableList] of [DeviceCollector] and adds the default collectors:
 *   - [PlatformCollector]: Collects OS and platform information
 *   - [HardwareCollector]: Collects device hardware specifications
 *   - [NetworkCollector]: Collects network connectivity status
 *   - [TelephonyCollector]: Collects telephony and carrier information
 *   - [LocationCollector]: Collects GPS location data (requires user permissions)
 *
 * @sample
 * ```kotlin
 * val collectors = mutableListOf<DeviceCollector<*>>()
 * collectors.DefaultDeviceCollector()
 * // collectors now contains all default device collectors
 * ```
 */
fun DefaultDeviceCollector(): MutableList<DeviceCollector<*>>.() -> Unit = {
    add(PlatformCollector)
    add(HardwareCollector)
    add(NetworkCollector())
    add(TelephonyCollector)
    add(LocationCollector())
}