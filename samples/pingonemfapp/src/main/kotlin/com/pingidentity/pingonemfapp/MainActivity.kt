/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pingidentity.pingonemfapp.data.DiagnosticLogger
import com.pingidentity.pingonemfapp.data.PingOneMFAViewModel
import com.pingidentity.pingonemfapp.data.ThemeMode
import com.pingidentity.pingonemfapp.data.UserPreferences
import com.pingidentity.pingonemfapp.managers.AccountsManager
import com.pingidentity.pingonemfapp.managers.OTPManager
import com.pingidentity.pingonemfapp.notification.NotificationHelper
import com.pingidentity.pingonemfapp.ui.AuthenticatorNavHost
import com.pingidentity.pingonemfapp.ui.theme.PingIdentityAuthenticatorTheme
import kotlinx.coroutines.launch

/**
 * Main activity for the Authenticator app.
 * Sets up the content view with Jetpack Compose and handles notification permissions.
 */
class MainActivity : ComponentActivity() {

    private lateinit var authenticatorViewModel: PingOneMFAViewModel
    private var areViewModelsInitialized by mutableStateOf(false)

    // Register for notification permission result
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Check if ViewModel is initialized before using it
        if (::authenticatorViewModel.isInitialized) {
            if (isGranted) {
                // Permission granted, notifications can be shown
                authenticatorViewModel.setMessage(getString(R.string.notification_permission_granted))
            } else {
                // Permission denied
                authenticatorViewModel.setMessage(getString(R.string.notification_permission_denied))
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup ViewModels with dependencies
        setupViewModels(application)

        // Initialize notification channels
        NotificationHelper(this).createNotificationChannels()
        
        // Check notification permission for Android 13+
        checkNotificationPermission()

        setContent {
            if (areViewModelsInitialized) {
                val themeMode by authenticatorViewModel.themeMode.collectAsState()
                PingIdentityAuthenticatorTheme(themeMode = themeMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AuthenticatorNavHost(
                            authenticatorViewModel = authenticatorViewModel,
                            initialDestination = getInitialDestination()
                        )
                    }
                }
            } else {
                // Show a basic loading screen with system theme while ViewModels initialize
                PingIdentityAuthenticatorTheme(themeMode = ThemeMode.SYSTEM) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // You could add a proper loading screen here if needed
                    }
                }
            }
        }
    }

    /**
     * Sets up the ViewModels with their dependencies.
     */
    private fun setupViewModels(application: Application) {
        // Initialize clients and ViewModels asynchronously
        lifecycleScope.launch {
            val diagnosticLogger = DiagnosticLogger
            val userPreferences = UserPreferences(application)
            val accountsManager = AccountsManager(diagnosticLogger = diagnosticLogger)
            val otpManager = OTPManager(diagnosticLogger = diagnosticLogger)

            // Create ViewModels with clients already set
            authenticatorViewModel = PingOneMFAViewModel(
                application = application,
                userPreferences = userPreferences,
                accountsManager = accountsManager,
                otpManager = otpManager,
            )

            // Mark ViewModels as initialized and trigger UI update
            areViewModelsInitialized = true
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
