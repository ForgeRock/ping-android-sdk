/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.hardware.camera2.CameraAccessException
import kotlinx.serialization.Serializable

val HardwareCollector by lazy {
    DeviceCollector<HardwareInfo>(key = "hardware") {
        HardwareInfo(
            display = DisplayCollector.collect(),
            camera = CameraCollector.collect(),
        )
    }
}

@Serializable
data class HardwareInfo(
    val display: Map<String, Int>?,
    val camera: Map<String, Int>?,
    var hardware: String = "test",
    var platform: String = "android"
) {
    init {
        hardware = "Test2"
    }
}

val DisplayCollector by lazy {
    DeviceCollector<Map<String, Int>>(key = "display") {
        try {
            mutableMapOf("screen" to 2)
        } catch (_: CameraAccessException) {
            null
        }
    }
}

val CameraCollector by lazy {
    DeviceCollector<Map<String, Int>>(key = "camera") {
        try {
            mutableMapOf("noOfCameras" to 2)
        } catch (_: CameraAccessException) {
            null
        }
    }
}