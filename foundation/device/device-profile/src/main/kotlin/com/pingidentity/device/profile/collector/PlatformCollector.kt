/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.annotation.SuppressLint
import android.os.Build
import com.pingidentity.device.DefaultTamperDetector
import com.pingidentity.device.analyze
import com.pingidentity.device.root.detector.TamperDetector
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.TimeZone

/**
 * A device collector that gathers platform and device identification information.
 *
 * This collector retrieves comprehensive device information including hardware identifiers,
 * device model details, branding information, and regional settings like locale and timezone.
 * All information is collected synchronously from Android system properties and settings.
 *
 * **Collected Information:**
 * - Device hardware identifier and model name
 * - Brand and manufacturer information
 * - Current locale and timezone settings
 * - Platform type (always "android" for Android devices)
 *
 * @see PlatformInfo for the data structure containing all collected platform details
 */
class PlatformCollector(
    private val tamperDetector: MutableList<TamperDetector>.() -> Unit = DefaultTamperDetector(),
) : DeviceCollector<PlatformInfo> {
    override val key = "platform"

    override suspend fun collect(): PlatformInfo {
        return PlatformInfo(
            device = Build.DEVICE ?: "Device",
            deviceName = Build.MODEL ?: "",
            model = Build.MODEL ?: "",
            brand = Build.BRAND ?: "",
            locale = Locale.getDefault().toString(), // e.g., "en_US"
            timeZone = TimeZone.getDefault().id,      // e.g., "America/Vancouver"
            jailBreakScore = analyze { detector { tamperDetector } }.toInt(),
        )
    }

    override val serializer: KSerializer<PlatformInfo> = PlatformInfo.serializer()
}

/**
 * Data class containing comprehensive platform and device information.
 *
 * This class holds various device identifiers, regional settings, and platform details
 * that can be used for device fingerprinting and analytics purposes.
 *
 * @property platform The platform type, defaults to "android" for Android devices
 * @property version The platform version number, currently unused (null)
 * @property device The device hardware identifier from [Build.DEVICE], defaults to "Device" if null
 * @property deviceName The user-visible device name from [Build.MODEL]
 * @property model The device model name from [Build.MODEL]
 * @property brand The device brand/manufacturer from [Build.BRAND]
 * @property locale The current system locale in format "language_COUNTRY" (e.g., "en_US")
 * @property timeZone The current system timezone ID (e.g., "America/Vancouver", "UTC")
 * @property jailBreakScore Security score indicating device modification level, currently unused (null)
 *
 * @sample
 * ```kotlin
 * val platformInfo = PlatformInfo(
 *     device = "pixel6",
 *     deviceName = "Pixel 6",
 *     model = "Pixel 6",
 *     brand = "google",
 *     locale = "en_US",
 *     timeZone = "America/New_York"
 * )
 * ```
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class PlatformInfo(
    var platform: String = "android",
    var version: Int? = null,
    var device: String = "",
    var deviceName: String = "",
    var model: String = "",
    var brand: String = "",
    var locale: String = "",
    var timeZone: String = "",
    var jailBreakScore: Int? = null,
)