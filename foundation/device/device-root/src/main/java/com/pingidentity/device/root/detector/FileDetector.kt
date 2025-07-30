/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import java.io.File

/**
 * Check file exists in predefined path
 */
abstract class FileDetector : RootDetector {
    override suspend fun isRooted(context: Context): Double {
        return if (getFilenames().any { exists(it) }) {
            1.0
        } else {
            0.0
        }
    }

    private fun exists(fileName: String) = getPaths().any {
        File(it, fileName).exists()
    }

    private fun getPaths(): List<String> {
        val paths = ArrayList(PATHS)
        val sysPaths = System.getenv("PATH")

        if (sysPaths.isNullOrEmpty()) {
            return emptyList()
        }

        for (path in sysPaths.split(':')) {
            var currentPath = path
            if (!currentPath.endsWith('/')) {
                currentPath += '/'
            }

            if (!paths.contains(currentPath)) {
                paths.add(currentPath)
            }
        }

        return paths
    }

    abstract fun getFilenames(): List<String>

    companion object {
        internal val PATHS = listOf(
            "/data/local/",
            "/data/local/bin/",
            "/data/local/xbin/",
            "/sbin/",
            "/su/bin/",
            "/system/bin/",
            "/system/bin/.ext/",
            "/system/bin/failsafe/",
            "/system/sd/xbin/",
            "/system/usr/we-need-root/",
            "/system/xbin/",
            "/cache/",
            "/data/",
            "/dev/"
        )
    }
}