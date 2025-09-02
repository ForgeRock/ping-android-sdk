/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile

import android.os.Build

/**
 * Interface for providing Android Build properties in a testable way.
 *
 * This abstraction allows for easier unit testing by enabling dependency injection
 * of Build property access rather than directly accessing static Build fields.
 * It provides access to key device identification properties from [Build] class.
 *
 * **Testing Benefits:**
 * - Enables mocking of Build properties during unit tests
 * - Allows testing with different device configurations
 * - Prevents tests from being dependent on actual device properties
 *
 * **Usage:**
 * ```kotlin
 * class MyCollector(private val buildProvider: AndroidBuildProvider = DefaultAndroidBuildProvider()) {
 *     fun getDeviceInfo() = buildProvider.getHardware()
 * }
 * ```
 *
 * @see DefaultAndroidBuildProvider for the production implementation
 * @see Build for the underlying Android Build class
 */
interface AndroidBuildProvider {
    /**
     * Returns the hardware identifier string.
     *
     * @return The hardware name from [Build.HARDWARE], or null if not available
     * @see Build.HARDWARE
     */
    fun getHardware(): String? = null

    /**
     * Returns the device manufacturer name.
     *
     * @return The manufacturer name from [Build.MANUFACTURER], or null if not available
     * @see Build.MANUFACTURER
     */
    fun getManufacturer(): String? = null

    /**
     * Returns the device model name.
     *
     * @return The model name from [Build.MODEL], or null if not available
     * @see Build.MODEL
     */
    fun getModel(): String? = null

    /**
     * Returns the device brand name.
     *
     * @return The brand name from [Build.BRAND], or null if not available
     * @see Build.BRAND
     */
    fun getBrand(): String? = null

    /**
     * Returns the device identifier.
     *
     * @return The device identifier from [Build.DEVICE], or null if not available
     * @see Build.DEVICE
     */
    fun getDevice(): String? = null
}

/**
 * Default implementation of [AndroidBuildProvider] that returns actual Android Build properties.
 *
 * This production implementation directly accesses the Android [Build] class to retrieve
 * device information. It provides the real device properties for use in production code.
 *
 * **Implementation Notes:**
 * - Currently implements only [getHardware] and [getManufacturer] methods
 * - Other methods ([getModel], [getBrand], [getDevice]) use default interface implementations (return null)
 * - This partial implementation can be extended as needed for additional Build properties
 *
 * **Thread Safety:**
 * This class is thread-safe as it only accesses static final fields from the Build class.
 *
 * @see AndroidBuildProvider for the interface definition
 * @see Build for the underlying Android system properties
 */
class DefaultAndroidBuildProvider : AndroidBuildProvider {
    /**
     * Returns the actual device hardware identifier from Android Build properties.
     *
     * @return The hardware identifier string from [Build.HARDWARE], or null if not set
     */
    override fun getHardware(): String? {
        return Build.HARDWARE
    }

    /**
     * Returns the actual device manufacturer from Android Build properties.
     *
     * @return The manufacturer name from [Build.MANUFACTURER], or null if not set
     */
    override fun getManufacturer(): String? {
        return Build.MANUFACTURER
    }
}