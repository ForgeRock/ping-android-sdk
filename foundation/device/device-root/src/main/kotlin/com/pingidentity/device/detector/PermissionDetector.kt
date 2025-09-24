/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.detector

import android.content.Context
import android.os.Build
import java.util.Scanner

/**
 * Tamper detector that identifies device compromise by examining filesystem mount permissions.
 *
 * This detector works by analyzing the output of the `mount` command to determine if
 * critical system directories are mounted with write permissions. On a secure Android device,
 * system directories should be mounted as read-only to prevent unauthorized modifications.
 *
 * The detector checks for read-write ("rw") mount options on the following critical paths:
 * - `/system` - Core Android system files
 * - `/system/bin` - System binary executables
 * - `/system/sbin` - System super-user binaries
 * - `/system/xbin` - Extended system binaries
 * - `/vendor/bin` - Vendor-specific binaries
 * - `/sbin` - System binaries directory
 * - `/etc` - System configuration files
 *
 * If any of these directories are mounted with write permissions, it typically indicates:
 * - The device has been rooted
 * - System partition has been remounted as writable
 * - Custom firmware or ROM is installed
 * - Security mechanisms have been bypassed
 *
 * The detector handles different Android SDK versions as the `mount` command output
 * format varies between Android Marshmallow (API 23) and later versions.
 *
 * Example usage:
 * ```kotlin
 * val detector = PermissionDetector()
 * val isTampered = detector.isTampered(context)
 *
 * // Or in analyze DSL:
 * val isTampered = analyze {
 *     detector {
 *         add(PermissionDetector())
 *     }
 * }
 * ```
 *
 * @since 1.0
 * @see TamperDetector
 */
class PermissionDetector : TamperDetector {

    /**
     * Determines if the device has been tampered with by checking filesystem mount permissions.
     *
     * This method executes the `mount` command and analyzes each mount point to determine
     * if any critical system directories are mounted with write permissions. The analysis
     * accounts for different output formats between Android SDK versions.
     *
     * For Android SDK > 23 (Marshmallow), the mount format is:
     * `device mountpoint fstype options1 options2 (options3)`
     *
     * For Android SDK <= 23, the format is:
     * `device mountpoint fstype options`
     *
     * @param context The Android context (not used in this implementation but required by interface)
     * @return A confidence score where:
     *         - `1.0` indicates critical system directories are mounted as writable
     *         - `0.0` indicates all critical directories are properly read-only
     */
    override suspend fun analyze(context: Context): Double {
        val sdkVersion = Build.VERSION.SDK_INT
        for (line in mountReader()) {
            val args = line.split(" ")
            if (sdkVersion <= Build.VERSION_CODES.M && args.size < 4 ||
                sdkVersion > Build.VERSION_CODES.M && args.size < 6) {
                //Log.e(TAG, "Error formatting mount line: $line")
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

    /**
     * Reads and parses the output of the `mount` command.
     *
     * This method executes the system `mount` command to retrieve information about
     * all currently mounted filesystems. The output is parsed into individual lines
     * for analysis of mount points and their permissions.
     *
     * @return A list of strings representing each line of the mount command output,
     *         or an empty list if the command fails or cannot be executed
     */
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
        /**
         * Tag used for logging purposes, derived from the class name.
         */
        private val TAG = PermissionDetector::class.java.simpleName

        /**
         * List of critical system paths that should never be writable on a secure device.
         *
         * These directories contain essential system files, binaries, and configurations
         * that must remain read-only to maintain system integrity and security:
         *
         * - `/system` - Main Android system partition
         * - `/system/bin` - Core system executable binaries
         * - `/system/sbin` - System super-user binaries
         * - `/system/xbin` - Extended system binaries and utilities
         * - `/vendor/bin` - Vendor-specific executable binaries
         * - `/sbin` - System binaries directory (alternative location)
         * - `/etc` - System configuration and settings files
         */
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