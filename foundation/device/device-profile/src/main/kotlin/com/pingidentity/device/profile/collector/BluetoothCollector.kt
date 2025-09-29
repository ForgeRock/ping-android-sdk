/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import com.pingidentity.android.ContextProvider
import kotlinx.serialization.Serializable

/**
 * Pre-configured device collector for gathering Bluetooth hardware capability information.
 *
 * This collector determines whether the device has Bluetooth hardware support by examining
 * the system's BluetoothManager and BluetoothAdapter availability. This information is
 * useful for device fingerprinting and capability detection.
 *
 * The collector safely handles devices without Bluetooth support and gracefully manages
 * any exceptions that might occur during Bluetooth system service access.
 *
 * **Detection Logic:**
 * - Accesses the system BluetoothManager service
 * - Checks if a BluetoothAdapter is available
 * - Returns `true` if adapter exists (indicating Bluetooth hardware support)
 * - Returns `false` if no adapter or if any exception occurs
 *
 * **Usage Example:**
 * ```kotlin
 * val bluetoothInfo = BluetoothCollector.collect()
 * if (bluetoothInfo.supported) {
 *     // Device has Bluetooth capability
 *     enableBluetoothFeatures()
 * }
 * ```
 *
 * **Integration with Device Profile:**
 * ```kotlin
 * val collectors = listOf(BluetoothCollector, /* other collectors */)
 * val deviceProfile = collectors.collect()
 * ```
 *
 * The collector is implemented as a lazy-initialized singleton for optimal performance
 * and consistent results across the application lifecycle.
 *
 * @see DeviceCollector
 * @see BluetoothData
 */
val BluetoothCollector by lazy {
    DeviceCollector(key = "bluetooth") {
        try {
            val manager = ContextProvider.context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            BluetoothData(supported = manager.adapter != null)
        } catch (_: Exception) {
            BluetoothData(supported = false)
        }
    }
}

/**
 * Data class representing Bluetooth hardware capability information of the device.
 *
 * This class encapsulates the fundamental Bluetooth hardware support status, which
 * is essential for device fingerprinting and feature availability determination.
 *
 * The information captured helps in:
 * - **Device Fingerprinting**: Bluetooth support varies across device types and models
 * - **Feature Planning**: Applications can adapt UI/UX based on Bluetooth availability
 * - **Compatibility Checks**: Ensuring Bluetooth-dependent features are only enabled on capable devices
 * - **Analytics**: Understanding the distribution of Bluetooth-capable devices in the user base
 *
 * **Implementation Notes:**
 * - Detection is based on BluetoothAdapter availability, not current state (enabled/disabled)
 * - Hardware support is detected regardless of whether Bluetooth is currently turned on
 * - Safe fallback to `false` if any system service access fails
 *
 * @property supported Indicates whether the device has Bluetooth hardware capability
 *

 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class BluetoothData(
    /**
     * Indicates whether the device has Bluetooth hardware support.
     *
     * This field represents the presence of Bluetooth hardware capability on the device,
     * determined by the availability of a BluetoothAdapter from the system's BluetoothManager.
     *
     * **Values:**
     * - `true`: Device has Bluetooth hardware and can support Bluetooth operations
     * - `false`: Device lacks Bluetooth hardware or system service access failed
     *
     * **Important Notes:**
     * - This indicates hardware capability, not current operational state
     * - A `true` value doesn't mean Bluetooth is currently enabled
     * - Value is determined at collection time and reflects hardware availability
     */
    val supported: Boolean
)