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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.pingidentity.samples.pingsampleapp.authenticator.notification.NotificationHelper
import com.pingidentity.samples.pingsampleapp.navigation.AppNavigation
import com.pingidentity.samples.pingsampleapp.theme.AppTheme
import kotlinx.coroutines.launch

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

        // Initialize clients asynchronously
        setupClients()

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
                // Use initial destination from intent if available (e.g., from notifications)
                val initialRoute = getInitialDestination()
                if (initialRoute != null) {
                    AppNavigation(startDestination = initialRoute)
                } else {
                    AppNavigation()
                }
            }
        }
    }

    /**
     * Sets up the MFA clients by retrieving them from the application.
     * This ensures the Push and OATH clients are initialized before the UI is shown.
     */
    private fun setupClients() {
        lifecycleScope.launch {
            try {
                // Initialize the clients - this will wait for them to be ready
                PingSampleApplication.getPushClient()
                PingSampleApplication.getOathClient()
                // Clients are now initialized and ready to use
            } catch (e: Exception) {
                // Log any initialization errors
                e.printStackTrace()
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

    /**
     * Determines the initial destination based on intent extras
     * (e.g., when opened from a notification)
     */
    private fun getInitialDestination(): String? {
        // Check if opened from a notification or deep link
        intent?.extras?.let { extras ->
            if (extras.containsKey("NAVIGATE_TO")) {
                val destination = extras.getString("NAVIGATE_TO")

                // If we have a notification ID, navigate to that notification
                if (destination == "notifications" && extras.containsKey("NOTIFICATION_ID")) {
                    val notificationId = extras.getString("NOTIFICATION_ID")
                    return "notification/$notificationId"
                }

                return destination
            }
        }

        return null // Return null to use default navigation
    }
}