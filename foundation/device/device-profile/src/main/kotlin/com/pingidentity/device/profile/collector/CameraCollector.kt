/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.hardware.camera2.CameraAccessException

val CameraCollector by lazy {
    DeviceCollector<Map<String, Int>>(
        key = "camera",
        collect = {
            //val manager = ContextProvider.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            try {
                //manager.cameraIdList.size
                //mutableMapOf("noOfCameras" to manager.cameraIdList.size)
                mutableMapOf("noOfCameras" to 2)
            } catch (_: CameraAccessException) {
                null
            }
        })
}