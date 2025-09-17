/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.annotation.SuppressLint
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

/**
 * A device collector that gathers comprehensive hardware information about the Android device.
 *
 * This collector retrieves various hardware specifications including display metrics, camera count,
 * hardware identifier, manufacturer, storage capacity, memory size, and CPU core count.
 * The information is collected synchronously and cached for subsequent calls.
 *
 * @see HardwareInfo for the data structure containing collected hardware details
 */
class HardwareCollector(
    private val androidBuildProvider: AndroidBuildProvider = DefaultAndroidBuildProvider()
) : DeviceCollector<HardwareInfo> {
    override val key: String
        get() = "hardware"

    override suspend fun collect(): HardwareInfo {
        return HardwareInfo(
            display = DisplayCollector.collect(),
            camera = CameraCollector.collect(),
            hardware = androidBuildProvider.getHardware() ?: "HARDWARE",
            manufacturer = androidBuildProvider.getManufacturer() ?: "MANUFACTURER",
            storage = getStorageInfo(),
            memory = getMemoryInfo(),
            cpu = Runtime.getRuntime().availableProcessors(),
        )
    }

    override val serializer = HardwareInfo.serializer()
}

/**
 * Data class containing comprehensive hardware information about the Android device.
 *
 * @property hardware The hardware identifier from [Build.HARDWARE], defaults to "MyHardware" if null
 * @property manufacturer The device manufacturer from [Build.MANUFACTURER]
 * @property storage Total internal storage capacity in gigabytes (GB)
 * @property memory Total system memory (RAM) in megabytes (MB)
 * @property cpu Number of available CPU cores
 * @property display Map containing display dimensions with "width" and "height" keys in pixels, null if unavailable
 * @property camera Map containing camera information with "noOfCameras" key, null if camera access fails
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class HardwareInfo(
    var hardware: String = "test",
    var manufacturer: String = "",
    var storage: Long,
    var memory: Long,
    var cpu: Int,
    val display: Map<String, Int>?,
    val camera: Map<String, Int>?,
)

/**
 * Internal collector for gathering display metrics information.
 *
 * Retrieves the device's screen dimensions using the WindowManager service.
 * Returns null if display metrics cannot be accessed.
 */
private val DisplayCollector by lazy {
    DeviceCollector<Map<String, Int>>(key = "display") {
        val windowManager = ContextProvider.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds.let {
                    metrics.widthPixels = it.width()
                    metrics.heightPixels = it.height()
                }
            } else {
                windowManager.defaultDisplay.getMetrics(metrics)
            }
            mutableMapOf(
                "width" to metrics.widthPixels,
                "height" to metrics.heightPixels,
            )
        } catch (_: CameraAccessException) {
            null
        }
    }
}

/**
 * Internal collector for gathering camera information.
 *
 * Uses the Camera2 API to determine the number of available cameras on the device.
 * Returns null if camera access is denied or unavailable.
 */
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

/**
 * Calculates the total internal storage capacity of the device.
 *
 * Uses [StatFs] to read filesystem statistics from the data directory and
 * converts the total storage to gigabytes.
 *
 * @return Total storage capacity in gigabytes (GB)
 */
private fun getStorageInfo(): Long {
    val path: File = Environment.getDataDirectory()
    val stat = StatFs(path.path)

    val blockSize = stat.blockSizeLong
    val totalBlocks = stat.blockCountLong

    return (totalBlocks * blockSize) / (1024 * 1024 * 1024)
}

/**
 * Retrieves the total system memory (RAM) of the device.
 *
 * Uses [ActivityManager] to get memory information and converts
 * the total memory to megabytes.
 *
 * @return Total system memory in megabytes (MB)
 */
private fun getMemoryInfo(): Long {
    val activityManager = ContextProvider.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    return memoryInfo.totalMem / (1024 * 1024) // in MB
}