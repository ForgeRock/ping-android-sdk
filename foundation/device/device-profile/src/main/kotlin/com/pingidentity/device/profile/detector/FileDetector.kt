package com.pingidentity.device.profile.detector

import android.content.Context
import com.pingidentity.device.root.BaseRootDetector
import java.io.File

/**
 * Check file exists in predefined path
 */
abstract class FileDetector : RootDetector<BaseRootDetector> {
    override fun isRooted(context: Context): Double {
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