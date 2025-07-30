package com.pingidentity.device.profile.detector

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.pingidentity.device.root.BaseRootDetector

/**
 * User Package Manager and see if application is installed.
 */
abstract class PackageDetector : RootDetector<BaseRootDetector> {

    fun exists(context: Context, packages: List<String>): Boolean {
        val packageManager = context.packageManager
        for (packageName in packages) {
            try {
                packageManager.getPackageInfo(packageName, 0)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Package $packageName", e)
            }
        }
        return false
    }

    override fun isRooted(context: Context): Double {
        return if (exists(context, getPackages())) {
            1.0
        } else {
            0.0
        }
    }

    internal abstract fun getPackages(): List<String>

    companion object {
        private val TAG = PackageDetector::class.java.simpleName
    }
}