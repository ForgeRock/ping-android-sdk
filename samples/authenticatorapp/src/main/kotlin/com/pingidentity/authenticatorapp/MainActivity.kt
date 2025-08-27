/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.pingidentity.authenticatorapp.data.AuthenticatorViewModel
import com.pingidentity.authenticatorapp.data.AuthenticatorViewModelFactory
import com.pingidentity.authenticatorapp.data.UserPreferences
import com.pingidentity.authenticatorapp.notification.NotificationHelper
import com.pingidentity.authenticatorapp.ui.AuthenticatorNavHost
import com.pingidentity.authenticatorapp.ui.theme.PingIdentityAuthenticatorTheme

/**
 * Main activity for the Authenticator app.
 * Sets up the content view with Jetpack Compose and handles notification permissions.
 */
class MainActivity : ComponentActivity() {
    
    private val viewModel: AuthenticatorViewModel by viewModels {
        AuthenticatorViewModelFactory(
            application = application,
            userPreferences = UserPreferences(application)
        )
    }
    
    // Register for notification permission result
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, notifications can be shown
            viewModel.setMessage(getString(R.string.notification_permission_granted))
        } else {
            // Permission denied
            viewModel.setMessage(getString(R.string.notification_permission_denied))
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize notification channels
        NotificationHelper(this).createNotificationChannels()
        
        // Check notification permission for Android 13+
        checkNotificationPermission()
        
        setContent {
            PingIdentityAuthenticatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthenticatorNavHost(
                        viewModel = viewModel,
                        initialDestination = getInitialDestination()
                    )
                }
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
    private fun getInitialDestination(): String {
        // Check if opened from a notification
        intent?.extras?.let { extras ->
            if (extras.containsKey("NAVIGATE_TO")) {
                val destination = extras.getString("NAVIGATE_TO") ?: return "accounts"
                
                // If we have a notification ID, navigate to that notification
                if (destination == "notifications" && extras.containsKey("NOTIFICATION_ID")) {
                    val notificationId = extras.getString("NOTIFICATION_ID") ?: return "notifications"
                    return "notification/$notificationId"
                }
                
                return destination
            }
        }
        
        return "accounts"
    }
}
