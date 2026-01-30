/*
 * Copyright (c) 2025-2026 Ping Identity Corporation. All rights reserved.
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
import com.pingidentity.journey.Journey
import com.pingidentity.journey.module.Oidc
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.mfa.oath.OathClient
import com.pingidentity.mfa.oath.storage.SQLOathStorage
import com.pingidentity.mfa.push.PushClient
import com.pingidentity.mfa.push.storage.SQLPushStorage
import com.pingidentity.storage.sqlite.passphrase.KeyStorePassphraseProvider
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
    private lateinit var pushClient: PushClient

    @Volatile
    private lateinit var oathClient: OathClient

    @Volatile
    private lateinit var journey: Journey

    @Volatile
    private lateinit var oathStorage: SQLOathStorage

    @Volatile
    private lateinit var pushStorage: SQLPushStorage

    private val pushClientDeferred = CompletableDeferred<PushClient>()
    private val oathClientDeferred = CompletableDeferred<OathClient>()
    private val journeyDeferred = CompletableDeferred<Journey>()
    private val oathStorageDeferred = CompletableDeferred<SQLOathStorage>()
    private val pushStorageDeferred = CompletableDeferred<SQLPushStorage>()

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
            // TODO: Update with your Journey configuration
            // Initialize Journey SDK
            journey = Journey {
                logger = diagnosticLogger
                serverUrl = "<YOUR_SERVER_URL>" // e.g. https://openam.example.com/am
                realm = "<YOUR_REALM>" // e.g. /alpha
                cookie = "<YOUR_COOKIE>" // e.g. iPlanetDirectoryPro
                // Oidc as module
                module(Oidc) {
                    clientId = "<YOUR_CLIENT_ID>" // e.g. myclient
                    discoveryEndpoint = "<YOUR_DISCOVERY_ENDPOINT>" // e.g. https://openam.example.com/am/oauth2/.well-known/openid-configuration?realm=/alpha
                    // Scopes to request - adjust as needed
                    scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
                    redirectUri = "<YOUR_REDIRECT_URI>" // e.g. myapp://callback
                }
            }
            journeyDeferred.complete(journey)
            diagnosticLogger.i("AuthenticatorApp: Journey client initialized")

            // Create storage instances
            oathStorage = SQLOathStorage {
                context = this@AuthenticatorApp
                passphraseProvider = KeyStorePassphraseProvider(
                    this@AuthenticatorApp,
                    logger = diagnosticLogger
                )
                this.logger = diagnosticLogger
            }
            oathStorageDeferred.complete(oathStorage)
            diagnosticLogger.i("AuthenticatorApp: OATH storage created")

            pushStorage = SQLPushStorage {
                context = this@AuthenticatorApp
                passphraseProvider = KeyStorePassphraseProvider(
                    this@AuthenticatorApp,
                    logger = diagnosticLogger
                )
                this.logger = diagnosticLogger
            }
            pushStorageDeferred.complete(pushStorage)
            diagnosticLogger.i("AuthenticatorApp: Push storage created")

            // Initialize OATH client with storage
            oathClient = OathClient {
                // Use the pre-created storage instance
                storage = oathStorage
                // Enable credential caching
                enableCredentialCache = true
                // Set diagnostic logger if enabled, otherwise standard logger
                this.logger = diagnosticLogger
            }
            oathClientDeferred.complete(oathClient)
            diagnosticLogger.i("AuthenticatorApp: OATH client initialized")

            // Initialize Push client with storage
            pushClient = PushClient {
                // Use the pre-created storage instance
                storage = pushStorage
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
            diagnosticLogger.i("AuthenticatorApp: SDK initialization complete")
        }
    }

    companion object {
        suspend fun getPushClient(context: Application): PushClient {
            val app = context as? AuthenticatorApp
                ?: throw IllegalStateException("Context must be AuthenticatorApp")

            if (app.pushClientDeferred.isCompleted) {
                return app.pushClientDeferred.getCompleted()
            }
            return app.pushClientDeferred.await()
        }

        suspend fun getOathClient(context: Application): OathClient {
            val app = context as AuthenticatorApp
            if (app.oathClientDeferred.isCompleted) {
                return app.oathClientDeferred.getCompleted()
            }
            return app.oathClientDeferred.await()
        }

        suspend fun getJourney(context: Application): Journey {
            val app = context as AuthenticatorApp
            if (app.journeyDeferred.isCompleted) {
                return app.journeyDeferred.getCompleted()
            }
            return app.journeyDeferred.await()
        }

        suspend fun getOathStorage(context: Application): SQLOathStorage {
            val app = context as? AuthenticatorApp
                ?: throw IllegalStateException("Context must be AuthenticatorApp")
            if (app.oathStorageDeferred.isCompleted) {
                return app.oathStorageDeferred.getCompleted()
            }
            return app.oathStorageDeferred.await()
        }

        suspend fun getPushStorage(context: Application): SQLPushStorage {
            val app = context as? AuthenticatorApp
                ?: throw IllegalStateException("Context must be AuthenticatorApp")
            if (app.pushStorageDeferred.isCompleted) {
                return app.pushStorageDeferred.getCompleted()
            }
            return app.pushStorageDeferred.await()
        }
    }
}
