/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.notification

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pingidentity.pingonemfapp.data.DiagnosticLogger
import com.pingidentity.pingonemfapp.notification.NotificationActionReceiver.Companion.EXTRA_NOTIFICATION_ID
import com.pingidentity.pingonemfapp.ui.NotificationResponseScreen
import com.pingidentity.pingonemfapp.ui.theme.PingIdentityAuthenticatorTheme
import com.pingidentity.pingonemfa.push.PushNotification
import com.pingidentity.pingonemfapp.notification.NotificationActionReceiver.Companion.EXTRA_NOTIFICATION
import kotlinx.coroutines.launch

/**
 * Activity to handle full-screen display of push notifications.
 * This activity is launched when a notification is received while app is open or when the user
 * clicks on a notification.
 */
class PushNotificationActivity : ComponentActivity() {


    private val diagnosticLogger = DiagnosticLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get notification ID from intent
        val notificationId = intent?.getStringExtra(EXTRA_NOTIFICATION_ID)

        val notification = intent?.getParcelableExtra(EXTRA_NOTIFICATION, PushNotification::class.java)
        println("XXX 1 " + notification?.title)
        // If no notification ID, log and finish
        if (notificationId == null) {
            diagnosticLogger.w("No notification ID provided")
            finish()
            return
        }
        
        // Set content to show notification details
        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            var isLoading by remember { mutableStateOf(true) }
            var notificationItemState by remember { mutableStateOf<PushNotification?>(null) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            
            // Load the notification when the composable is first launched
            LaunchedEffect(Unit) {
                try {
                    notificationItemState = notification
                    isLoading = false
                } catch (e: Exception) {
                    diagnosticLogger.w("Error loading notification: ${e.message}")
                    errorMessage = "Failed to load notification: ${e.message}"
                    isLoading = false
                }
            }
            
            PingIdentityAuthenticatorTheme {
                Surface {
                    val currentNotificationItem = notificationItemState // Use a local copy for smart casting
                    when {
                        isLoading -> {
                            // Show loading indicator
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        errorMessage != null -> {
                            // Show error message
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = errorMessage!!)
                            }
                        }
                        currentNotificationItem != null -> {
                            // Display the unified notification screen
                            NotificationResponseScreen(
                                notificationItem = currentNotificationItem,
                                onDismiss = { finish() },
                                onApprove = {
                                    coroutineScope.launch {
                                        val result = notification?.approveNotification(
                                            context,
                                            "user"
                                        )
                                        when {
                                            result?.isSuccess == true -> {
                                                finish()
                                            }
                                            result?.isFailure == true -> {
                                                diagnosticLogger.e("Error approving with challenge: ${result.exceptionOrNull()?.stackTrace}")
                                                errorMessage = "Failed to approve: ${result.exceptionOrNull()?.message}"
                                            }
                                        }
                                    }
                                },
                                onBiometricApprove = {
                                    launchBiometricPrompt(notificationId, notification)
                                },
                                onDeny = {
                                    coroutineScope.launch {
                                        val result = notification?.denyNotification(context)
                                        when {
                                            result?.isSuccess == true -> {
                                                finish()
                                            }
                                            result?.isFailure == true -> {
                                                diagnosticLogger.e("Error approving with challenge: ${result.exceptionOrNull()?.stackTrace}")
                                                errorMessage = "Failed to approve: ${result.exceptionOrNull()?.message}"
                                            }
                                        }
                                    }
                                },
                                onChallengeSolution = { solution ->
                                    coroutineScope.launch {
                                        val result = notification?.approveNotification(
                                                context,
                                                "user",
                                                solution.toInt()
                                        )
                                        when {
                                            result?.isSuccess == true -> {
                                                finish()
                                            }
                                            result?.isFailure == true -> {
                                                diagnosticLogger.e("Error approving with challenge: ${result.exceptionOrNull()?.stackTrace}")
                                                errorMessage = "Failed to approve: ${result.exceptionOrNull()?.message}"
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Launches the BiometricPromptActivity for biometric authentication.
     */
    private fun launchBiometricPrompt(notificationId: String, notification: PushNotification?) {
        val intent = Intent(this, BiometricPromptActivity::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_NOTIFICATION, notification)
        }
        startActivity(intent)
        finish() // finish current activity before launching new one.
    }

}
