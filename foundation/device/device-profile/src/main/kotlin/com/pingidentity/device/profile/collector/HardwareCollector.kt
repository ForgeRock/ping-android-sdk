/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.app.ActivityManager
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import com.pingidentity.android.ContextProvider
import kotlinx.serialization.Serializable
import java.io.File

val HardwareCollector by lazy {
    DeviceCollector<HardwareInfo>(key = "hardware") {
        HardwareInfo(
            display = DisplayCollector.collect(),
            camera = CameraCollector.collect(),
            hardware = Build.HARDWARE ?: "HARDWARE",
            manufacturer = Build.MANUFACTURER ?: "MANUFACTURER",
            storage = getStorageInfo(),
            memory = getMemoryInfo(),
            cpu = Runtime.getRuntime().availableProcessors(),
        )
    }
}

@Serializable
data class HardwareInfo(
    var hardware: String = "test",
    var manufacturer: String = "",
    var storage: Long,
    var memory: Long,
    var cpu: Int,
    val display: Map<String, Int>?,
    val camera: Map<String, Int>?,
) {
    init {
        hardware = Build.HARDWARE ?: "MyHardware"
    }
}

private val DisplayCollector by lazy {
    DeviceCollector<Map<String, Int>>(key = "display") {
        val windowManager = ContextProvider.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            mutableMapOf(
                "width" to metrics.widthPixels,
                "height" to metrics.heightPixels,
            )
        } catch (_: CameraAccessException) {
            null
        }
    }
}

private val CameraCollector by lazy {
    DeviceCollector<Map<String, Int>>(key = "camera") {
        val manager =
            ContextProvider.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            manager.cameraIdList.size
            mutableMapOf("noOfCameras" to manager.cameraIdList.size)
        } catch (_: CameraAccessException) {
            null
        }
    }
}

private fun getStorageInfo(): Long {
    val path: File = Environment.getDataDirectory()
    val stat = StatFs(path.path)

    val blockSize = stat.blockSizeLong
    val totalBlocks = stat.blockCountLong

    return (totalBlocks * blockSize) / (1024 * 1024 * 1024)
}

private fun getMemoryInfo(): Long {
    val activityManager = ContextProvider.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    return memoryInfo.totalMem / (1024 * 1024) // in MB
}