/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import java.util.Scanner

/**
 * Abstract base class for detecting device tampering by examining Android system properties.
 *
 * This detector works by checking specific system properties that may indicate the device
 * has been rooted, is running custom firmware, or has other security modifications.
 * Subclasses must provide the specific properties to check through the [getProperties] method.
 *
 * Common properties that might indicate tampering include:
 * - `ro.secure=0` (indicates insecure boot)
 * - `ro.debuggable=1` (indicates debug build)
 * - `ro.build.tags=test-keys` (indicates unofficial build)
 * - Custom properties added by root management tools
 *
 * Example usage:
 * ```kotlin
 * class RootPropertyDetector : SystemPropertyDetector() {
 *     override fun getProperties(): Map<String, String> {
 *         return mapOf(
 *             "ro.secure" to "0",
 *             "ro.debuggable" to "1",
 *             "ro.build.tags" to "test-keys"
 *         )
 *     }
 * }
 * ```
 *
 */
abstract class SystemPropertyDetector : TamperDetector {

    /**
     * Determines if the device has been tampered with by checking system properties.
     *
     * This method examines the system properties returned by [getProperties] and
     * determines if any of them indicate that the device has been compromised or modified.
     *
     * @param context The Android context (not used in this implementation but required by interface)
     * @return `true` if any suspicious system properties are detected, `false` otherwise
     */
    override suspend fun analyze(context: Context): Double {
        return if (exists(getProperties())) {
            1.0
        } else {
            0.0
        }
    }

    /**
     * Checks if any of the specified properties exist with their expected values.
     *
     * This method searches through all system properties to find matches for the
     * key-value pairs provided in the properties map. The format expected is
     * `[key]: [value]` as returned by the `getprop` command.
     *
     * @param properties A map of property names to their suspicious values
     * @return `true` if any matching property-value pair is found, `false` otherwise
     */
    internal fun exists(properties: Map<String, String>): Boolean {
        for (line in propsReader()) {
            for (entry in properties.entries.toSet()) {
                return (line.contains(entry.key) &&
                        line.contains("[${entry.value}]"))
            }
        }
        return false
    }

    /**
     * Reads all system properties using the `getprop` command.
     *
     * This method executes the Android `getprop` command to retrieve all system
     * properties and returns them as a list of strings. Each string represents
     * one property in the format `[key]: [value]`.
     *
     * @return A list of property strings, or an empty list if reading fails
     */
    private fun propsReader(): List<String> {
        try {
            val inputStream = Runtime.getRuntime().exec("getprop").inputStream
            if (inputStream == null) return emptyList()
            return Scanner(inputStream)
                .useDelimiter("\\A").next().split("\n")
        } catch (exception: Exception) {
            return emptyList()
        }
    }

    /**
     * Provides the map of system properties to check for tampering detection.
     *
     * Subclasses must implement this method to return a map where:
     * - Keys are property names (e.g., "ro.secure")
     * - Values are the suspicious values to look for (e.g., "0")
     *
     * The detector will check if any of these property-value combinations
     * exist on the current device.
     *
     * @return A map of property names to their suspicious values
     */
    internal abstract fun getProperties(): Map<String, String>
}