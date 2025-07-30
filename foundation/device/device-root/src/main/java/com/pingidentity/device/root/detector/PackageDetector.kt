/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * User Package Manager and see if application is installed.
 */
abstract class PackageDetector : RootDetector {

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

    override suspend fun isRooted(context: Context): Double {
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