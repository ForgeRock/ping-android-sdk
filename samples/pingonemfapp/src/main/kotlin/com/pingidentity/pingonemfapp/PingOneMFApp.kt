/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.pingonemfa.commons.PingOneMFA
import com.pingidentity.pingonemfapp.data.DiagnosticLogger
import com.pingidentity.pingonemfapp.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Main application class for the PingOne MFA Authenticator app.
 * Initializes the PingOne SDK on application startup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PingOneMFApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize diagnostic logging if enabled
        val userPreferences = UserPreferences(this)
        val diagnosticLogger = if (userPreferences.isDiagnosticLoggingEnabled()) {
            DiagnosticLogger
        } else {
            Logger.STANDARD
        }

        // Set the global logger
        Logger.logger = diagnosticLogger

        // Log initial startup
        if (userPreferences.isDiagnosticLoggingEnabled()) {
            diagnosticLogger.i("AuthenticatorApp: Diagnostic logging enabled")
            diagnosticLogger.i("AuthenticatorApp: Starting SDK initialization")
        }

        CoroutineScope(Dispatchers.Default).launch {
            // initialize PingOneMFA SDK
            try {
                PingOneMFA.initialize()
                diagnosticLogger.i("PingOneMFA SDK initialized")
            }catch (e: Exception){
                diagnosticLogger.e("PingOneMFA SDK initialization failed", e)
            }

            // Obtain the device token from Firebase and set register it with PingOneMFA SDK
            try {
                FirebaseApp.initializeApp(this@PingOneMFApp)
                PingOneMFA.register(FirebaseMessaging.getInstance().token.await())
                diagnosticLogger.i("PingOneMFA SDK: Firebase device token set")
            } catch (e: IllegalStateException) {
                diagnosticLogger.e("Firebase not configured properly", e)
            }
            diagnosticLogger.i("AuthenticatorApp: SDK initialization complete")
        }
    }
}
