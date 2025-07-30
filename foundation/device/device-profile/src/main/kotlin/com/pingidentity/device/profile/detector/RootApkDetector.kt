package com.pingidentity.device.profile.detector

import android.content.Context
import android.util.Log
import com.pingidentity.device.root.BaseRootDetector
import java.io.File

/**
 * Check if there are well-known root apk files exist
 */
class RootApkDetector : RootDetector<BaseRootDetector> {
    override val key: String
        get() = RootApkDetector::class.java.simpleName

    override fun isRooted(context: Context): Double {
        return if (exists(ROOT_APK)) {
            1.0
        } else {
            0.0
        }
    }

    private fun exists(apks: List<String>): Boolean {
        try {
            for (apk in apks) {
                if (File(apk).exists()) {
                    return true
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to check apks", exception)
        }
        return false
    }

    companion object {
        private val TAG = RootApkDetector::class.java.simpleName

        private val ROOT_APK = listOf<String>(
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk",
            "/system/app/magisk.apk",
        )
    }
}