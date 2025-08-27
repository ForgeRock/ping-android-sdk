/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.pingidentity.authenticatorapp.data.DiagnosticLogger
import com.pingidentity.authenticatorapp.data.UserPreferences
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.mfa.oath.OathClient
import com.pingidentity.mfa.oath.OathMfaClient
import com.pingidentity.mfa.push.PushClient
import com.pingidentity.mfa.push.PushMfaClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Main application class for the Authenticator app.
 * Initializes the Push and OATH MFA clients on application startup and provide access to them throughout the app.
 * It also allow the clients to be accessed in background services or other components that require MFA functionality.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticatorApp : Application() {
    @Volatile
    private lateinit var pushClient: PushMfaClient

    @Volatile
    private lateinit var oathClient: OathMfaClient

    private val pushClientDeferred = CompletableDeferred<PushMfaClient>()
    private val oathClientDeferred = CompletableDeferred<OathMfaClient>()

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
            pushClient = PushClient {
                // Enable credential caching
                enableCredentialCache = true

                // Set diagnostic logger if enabled, otherwise standard logger
                this.logger = diagnosticLogger
            }
            pushClientDeferred.complete(pushClient)
            diagnosticLogger.i("AuthenticatorApp: Push client initialized")

            // Obtain the device token from Firebase and set it in the Push client
            try {
                FirebaseApp.getInstance()
                pushClient.setDeviceToken(FirebaseMessaging.getInstance().token.await())
                diagnosticLogger.i("AuthenticatorApp: Firebase device token set")
            } catch (e: IllegalStateException) {
                diagnosticLogger.e("Firebase not configured properly", e)
            }

            // Initialize OATH client synchronously
            oathClient = OathClient {
                // Enable credential caching
                enableCredentialCache = true

                // Set diagnostic logger if enabled, otherwise standard logger
                this.logger = diagnosticLogger
            }
            oathClientDeferred.complete(oathClient)
            diagnosticLogger.i("AuthenticatorApp: OATH client initialized")
            diagnosticLogger.i("AuthenticatorApp: SDK initialization complete")
        }
    }

    companion object {
        suspend fun getPushClient(context: Application): PushMfaClient {
            val app = context as? AuthenticatorApp
                ?: throw IllegalStateException("Context must be AuthenticatorApp")

            if (app.pushClientDeferred.isCompleted) {
                return app.pushClientDeferred.getCompleted()
            }
            return app.pushClientDeferred.await()
        }

        suspend fun getOathClient(context: Application): OathMfaClient {
            val app = context as AuthenticatorApp
            if (app.oathClientDeferred.isCompleted) {
                return app.oathClientDeferred.getCompleted()
            }
            return app.oathClientDeferred.await()
        }
    }
}
