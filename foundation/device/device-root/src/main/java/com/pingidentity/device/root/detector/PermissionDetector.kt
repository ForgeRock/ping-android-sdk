/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import android.os.Build
import android.util.Log
import java.util.Scanner

/**
 * After the device is rooted, the super user may change permission of some files.
 */
class PermissionDetector : RootDetector {

    override suspend fun isRooted(context: Context): Double {
        val sdkVersion = Build.VERSION.SDK_INT
        for (line in mountReader()) {
            val args = line.split(" ")
            if (sdkVersion <= Build.VERSION_CODES.M && args.size < 4 ||
                sdkVersion > Build.VERSION_CODES.M && args.size < 6) {
                Log.e(TAG, "Error formatting mount line: $line")
                continue
            }
            var mountPoint: String
            var mountOptions: String
            if (sdkVersion > Build.VERSION_CODES.M) {
                mountPoint = args[2]
                mountOptions = args[5]
            } else {
                mountPoint = args[1]
                mountOptions = args[3]
            }

            for (pathToCheck in NOT_WRITABLE_PATH) {
                if (mountPoint.equals(pathToCheck, true)) {
                    if (sdkVersion > Build.VERSION_CODES.M) {
                        mountOptions = mountOptions.replace("(", "")
                        mountOptions = mountOptions.replace(")", "")
                    }

                    for (option in mountOptions.split(",")) {
                        if (option.equals("rw", true)) {
                            return 1.0
                        }
                    }
                }
            }
        }
        return 0.0
    }

    private fun mountReader(): List<String> {
        try {
            val inputStream = Runtime.getRuntime().exec("mount").inputStream
            if (inputStream == null) return emptyList()
            return Scanner(inputStream).useDelimiter("\\A").next().split("\n")
        } catch (exception: Exception) {
            return emptyList()
        }
    }

    companion object {
        private val TAG = PermissionDetector::class.java.simpleName

        private val NOT_WRITABLE_PATH = listOf(
            "/system",
            "/system/bin",
            "/system/sbin",
            "/system/xbin",
            "/vendor/bin",
            "/sbin",
            "/etc",
        )
    }
}