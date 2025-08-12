/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.os.Build
import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.TimeZone

val PlatformCollector by lazy {
    DeviceCollector<PlatformInfo>(key = "platform") {
        PlatformInfo(
            device = Build.DEVICE ?: "Device",
            deviceName = Build.MODEL ?: "",
            model = Build.MODEL ?: "",
            brand = Build.BRAND ?: "",
            locale = Locale.getDefault().toString(), // e.g., "en_US"
            timeZone = TimeZone.getDefault().id      // e.g., "America/Vancouver"
        )
    }
}

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