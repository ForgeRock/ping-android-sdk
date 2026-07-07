/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.mfa.oath.OathClient
import com.pingidentity.mfa.push.PushClient
import com.pingidentity.samples.pingsampleapp.authenticator.data.AuthenticatorViewModel
import com.pingidentity.samples.pingsampleapp.authenticator.data.DiagnosticLogger
import com.pingidentity.samples.pingsampleapp.authenticator.data.UserPreferences
import com.pingidentity.samples.pingsampleapp.authenticator.managers.AccountGroupingManager
import com.pingidentity.samples.pingsampleapp.authenticator.managers.JourneyManager
import com.pingidentity.samples.pingsampleapp.authenticator.managers.OathManager
import com.pingidentity.samples.pingsampleapp.authenticator.managers.PushManager
import com.pingidentity.samples.pingsampleapp.authenticator.managers.TestAccountFactory
import com.pingidentity.samples.pingsampleapp.config.initConfigs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Main Application class for PingSampleApp.
 * Initializes all SDK clients and managers on application startup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PingSampleApplication : Application() {

    // MFA Clients
    @Volatile
    private lateinit var pushClient: PushClient

    @Volatile
    private lateinit var oathClient: OathClient

    // Deferred for async initialization
    private val pushClientDeferred = CompletableDeferred<PushClient>()
    private val oathClientDeferred = CompletableDeferred<OathClient>()
    private val viewModelDeferred = CompletableDeferred<AuthenticatorViewModel>()

    // Managers
    private lateinit var oathManager: OathManager
    private lateinit var pushManager: PushManager
    private lateinit var journeyManager: JourneyManager
    private lateinit var accountGroupingManager: AccountGroupingManager
    private lateinit var testAccountFactory: TestAccountFactory

    // UserPreferences
    private lateinit var userPreferences: UserPreferences

    // DiagnosticLogger
    private val diagnosticLogger = DiagnosticLogger

    // AuthenticatorViewModel
    @Volatile
    private lateinit var authenticatorViewModel: AuthenticatorViewModel

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        instance = this

        // Initialize user preferences
        userPreferences = UserPreferences(this)

        // Set the global logger
        Logger.logger = if (userPreferences.isDiagnosticLoggingEnabled()) {
            diagnosticLogger
        } else {
            Logger.STANDARD
        }

        // Log initial startup
        if (userPreferences.isDiagnosticLoggingEnabled()) {
            diagnosticLogger.i("PingSampleApplication: Diagnostic logging enabled")
            diagnosticLogger.i("PingSampleApplication: Starting SDK initialization")
        }

        // Initialize SDK clients and managers asynchronously
        CoroutineScope(Dispatchers.Default).launch {
            withContext(Dispatchers.IO) {
                // Load persisted SDK configs (Journey, DaVinci, OIDC Web) immediately so
                // all flows are ready before the user visits the Configuration screen.
                initConfigs()
            }
            initializeSdkClients()
            initializeManagers()
            initializeAuthenticatorViewModel()
        }
    }

    /**
     * Initializes the Push and OATH MFA clients.
     */
    private suspend fun initializeSdkClients() {
        try {
            // Initialize Push client
            pushClient = PushClient {
                enableCredentialCache = true
                this.logger = if (userPreferences.isDiagnosticLoggingEnabled()) {
                    diagnosticLogger
                } else {
                    Logger.STANDARD
                }
            }
            pushClientDeferred.complete(pushClient)
            diagnosticLogger.i("PingSampleApplication: Push client initialized")

            // Obtain the device token from Firebase and set it in the Push client
            try {
                pushClient.setDeviceToken(FirebaseMessaging.getInstance().token.await())
                diagnosticLogger.i("PingSampleApplication: Firebase device token set")
            } catch (e: IllegalStateException) {
                diagnosticLogger.e("Firebase not configured properly", e)
            } catch (e: Exception) {
                diagnosticLogger.w("Failed to get Firebase token", e)
            }

            // Initialize OATH client
            oathClient = OathClient {
                enableCredentialCache = true
                this.logger = if (userPreferences.isDiagnosticLoggingEnabled()) {
                    diagnosticLogger
                } else {
                    Logger.STANDARD
                }
            }
            oathClientDeferred.complete(oathClient)
            diagnosticLogger.i("PingSampleApplication: OATH client initialized")

        } catch (e: Exception) {
            diagnosticLogger.e("Failed to initialize SDK clients", e)
            throw e
        }
    }

    /**
     * Initializes the managers that handle business logic.
     */
    private fun initializeManagers() {
        try {
            // Initialize managers
            oathManager = OathManager(oathClient, diagnosticLogger)
            pushManager = PushManager(pushClient, diagnosticLogger)
            journeyManager = JourneyManager(null, diagnosticLogger) // Journey will be set from config
            accountGroupingManager = AccountGroupingManager(userPreferences, diagnosticLogger)
            testAccountFactory = TestAccountFactory()

            pushManager.setClient(pushClient)
            oathManager.setClient(oathClient)


            diagnosticLogger.i("PingSampleApplication: Managers initialized")
        } catch (e: Exception) {
            diagnosticLogger.e("Failed to initialize managers", e)
            throw e
        }
    }

    /**
     * Initializes the AuthenticatorViewModel.
     */
    private fun initializeAuthenticatorViewModel() {
        try {
            authenticatorViewModel = AuthenticatorViewModel(
                application = this,
                userPreferences = userPreferences,
                oathManager = oathManager,
                pushManager = pushManager,
                accountGroupingManager = accountGroupingManager,
                testAccountFactory = testAccountFactory
            )
            viewModelDeferred.complete(authenticatorViewModel)
            diagnosticLogger.i("PingSampleApplication: AuthenticatorViewModel initialized")
            diagnosticLogger.i("PingSampleApplication: SDK initialization complete")
        } catch (e: Exception) {
            diagnosticLogger.e("Failed to initialize ViewModel", e)
            throw e
        }
    }

    companion object {
        @Volatile
        private lateinit var instance: PingSampleApplication

        /**
         * Gets the Push client. Suspends until initialization is complete.
         */
        suspend fun getPushClient(): PushClient {
            if (instance.pushClientDeferred.isCompleted) {
                return instance.pushClientDeferred.getCompleted()
            }
            return instance.pushClientDeferred.await()
        }

        /**
         * Gets the OATH client. Suspends until initialization is complete.
         */
        suspend fun getOathClient(): OathClient {
            if (instance.oathClientDeferred.isCompleted) {
                return instance.oathClientDeferred.getCompleted()
            }
            return instance.oathClientDeferred.await()
        }

        /**
         * Gets the AuthenticatorViewModel. Suspends until initialization is complete.
         */
        suspend fun getAuthenticatorViewModel(): AuthenticatorViewModel {
            if (instance.viewModelDeferred.isCompleted) {
                return instance.viewModelDeferred.getCompleted()
            }
            return instance.viewModelDeferred.await()
        }

        /**
         * Closes the existing MFA clients (clearing their in-memory credential caches and
         * SQLite database connections), then re-initializes fresh instances and wires them
         * back into the managers.
         *
         * Call this after a data migration so that stale caches are purged and the new
         * database connections can see the migrated credentials. Follow up with a call to
         * [AuthenticatorViewModel.refreshCredentials] to push the new data into the UI.
         */
        suspend fun reinitializeMfaClients() {
            with(instance) {
                // Close old clients — this clears the in-memory credential caches and
                // releases the SQLite database connections.
                oathManager.close()
                pushManager.close()

                // Create fresh clients with new database connections.
                initializeSdkClients()

                // Wire the new client instances into the managers so subsequent
                // operations use the freshly-opened databases.
                oathManager.setClient(oathClient)
                pushManager.setClient(pushClient)
            }
        }
    }
}

