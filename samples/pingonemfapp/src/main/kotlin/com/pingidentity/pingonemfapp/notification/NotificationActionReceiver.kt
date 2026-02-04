/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.pingidentity.pingonemfa.commons.PingOneMFA
import com.pingidentity.pingonemfa.push.PushNotification
import com.pingidentity.pingonemfapp.data.DiagnosticLogger

/**
 * BroadcastReceiver to handle notification actions.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    private val diagnosticLogger = DiagnosticLogger

    companion object {
        const val ACTION_APPROVE = "com.pingidentity.pingonemfapp.ACTION_APPROVE"
        const val ACTION_DENY = "com.pingidentity.pingonemfapp.ACTION_DENY"
        const val ACTION_BIOMETRIC = "com.pingidentity.pingonemfapp.ACTION_BIOMETRIC"

        const val EXTRA_NOTIFICATION = "com.pingidentity.pingonemfapp.notification"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_NOTIFICATION, PushNotification::class.java)
        } else {
            @Suppress("DEPRECATION") // Suppress deprecation warning for backward compatibility
            intent.getParcelableExtra(EXTRA_NOTIFICATION)
        } ?: return

        val notificationHashCode = notification.id.hashCode()
        // Cancel the notification immediately to provide feedback that the action was received
        NotificationManagerCompat.from(context).cancel(notificationHashCode)
        
        when (intent.action) {
            ACTION_APPROVE -> {
                diagnosticLogger.d("Approve action received for notification: ${notification.id}")
                PingOneMFA.approvePushNotificationFromBanner(notification = notification)
            }
            ACTION_DENY -> {
                diagnosticLogger.d("Deny action received for notification: ${notification.id}")
                PingOneMFA.denyPushNotificationFromBanner(notification = notification)
            }
            ACTION_BIOMETRIC -> {
                diagnosticLogger.d("Biometric action received for notification: ${notification.id}")
                handleBiometricAuthentication(context, notification)
            }
        }
    }

    /**
     * Handles biometric authentication for the notification with the given ID.
     * This launches the BiometricPrompt activity.
     */
    private fun handleBiometricAuthentication(context: Context, notification: PushNotification) {
        val intent = Intent(context, BiometricPromptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_NOTIFICATION, notification)
        }
        context.startActivity(intent)
    }
}
