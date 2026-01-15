/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.snackbar.Snackbar
import com.pingidentity.samples.pingsampleapp.authenticator.notification.NotificationHelper
import com.pingidentity.samples.pingsampleapp.navigation.AppNavigation
import com.pingidentity.samples.pingsampleapp.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    // Register for notification permission result
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, notifications can be shown
            Toast.makeText(
                this,
                "Notification permission granted",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            // Permission denied, show Snackbar with action to go to settings
            window.decorView.rootView.let { view ->
                Snackbar.make(
                    view,
                    "Notification permission denied. You won't receive push notifications.",
                    Snackbar.LENGTH_LONG
                ).setAction("Settings") {
                    // Open app settings page
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                }.show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize notification channels
        NotificationHelper(this).createNotificationChannels()

        // Check notification permission for Android 13+
        checkNotificationPermission()

        installSplashScreen().apply {
            setKeepOnScreenCondition {
                viewModel.isLoading.value
            }
        }

        // Show the home screen
        setContent {
            AppTheme() {
                AppNavigation()
            }
        }
    }

    /**
     * Checks if notification permission is granted and requests if not
     * (required for Android 13+/API 33+)
     */
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionState = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)

            if (permissionState != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}