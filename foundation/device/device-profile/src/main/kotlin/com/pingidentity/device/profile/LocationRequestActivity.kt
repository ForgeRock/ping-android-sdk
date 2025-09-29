/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.pingidentity.device.profile.collector.PermissionResultManager

/**
 * A transparent activity that handles location permission requests for the device profile collector.
 *
 * This activity is launched when location permissions are needed but not yet granted. It presents
 * the system permission dialog to the user and communicates the result back to the LocationCollector
 * through the PermissionResultManager. The activity finishes automatically after handling the permission result.
 *
 * **Features:**
 * - Locks screen orientation to prevent unwanted rotations during permission request
 * - Uses ActivityResultContracts for modern permission handling
 * - Automatically finishes after permission result is processed
 * - Handles edge cases with delayed finish to ensure proper result delivery
 */
class LocationRequestActivity : ComponentActivity() {

    /**
     * Permission launcher that handles the location permission request flow.
     *
     * This launcher requests ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION permission and delivers the result
     * to the waiting LocationCollector through PermissionResultManager.
     */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions: Map<String, Boolean> ->
            // Complete the SDK's deferred object with the result
            val allGranted = permissions.values.all { it }
            PermissionResultManager.permissionResultDeferred?.complete(allGranted)

            // Use a small delay to ensure the permission result is fully processed
            // before finishing the activity. This prevents potential race conditions
            // where the activity finishes before the result is properly delivered.
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) {
                    finish()
                }
            }, 100) // 100ms delay should be sufficient for result processing
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lock orientation to prevent unwanted rotations during permission request
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        // Immediately request the location permission
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        )
    }

    /**
     * Called when the activity is being destroyed.
     *
     * Ensures that any pending permission result is marked as denied if the activity
     * is destroyed without a proper result (e.g., user backs out of permission dialog).
     */
    override fun onDestroy() {
        super.onDestroy()
        // If the deferred is still active (not completed), complete it with false
        // This handles cases where the activity is destroyed without a permission result
        PermissionResultManager.permissionResultDeferred?.let { deferred ->
            if (!deferred.isCompleted) {
                deferred.complete(false)
            }
        }
    }

    companion object {
        /**
         * Creates an intent to launch the LocationRequestActivity.
         *
         * The intent is configured with FLAG_ACTIVITY_NEW_TASK to allow launching
         * from non-Activity contexts (such as from a service or application context).
         *
         * @param context The context from which to launch the activity
         * @return An intent configured to launch LocationRequestActivity
         */
        fun createIntent(context: Context): Intent {
            return Intent(context, LocationRequestActivity::class.java).also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}