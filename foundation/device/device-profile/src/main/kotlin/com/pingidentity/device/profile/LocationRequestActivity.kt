/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.pingidentity.device.profile.collector.PermissionResultManager

class LocationRequestActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            // 1. When the user responds, complete the SDK's deferred object with the result.
            PermissionResultManager.permissionResultDeferred?.complete(isGranted)
            // 2. Finish this activity to return control.
            finish()
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, LocationRequestActivity::class.java).also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}