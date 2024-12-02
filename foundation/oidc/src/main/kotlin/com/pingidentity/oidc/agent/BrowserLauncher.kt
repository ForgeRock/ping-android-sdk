/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.agent

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import com.pingidentity.android.ContextProvider
import com.pingidentity.oidc.AuthCode
import com.pingidentity.oidc.OidcConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import java.lang.IllegalStateException

/**
 * This object is responsible for launching the browser for OpenID Connect operations.
 */
object BrowserLauncher {

    private val isInitialized = MutableStateFlow(false)
    private var launcher: Launcher? = null

    //Mutex lock to control the lifecycle of the BrowserLauncherActivity
    //This is used to ensure that the BrowserLauncherActivity is processed in a synchronous manner,
    //it is locked when the BrowserLauncherActivity is launched and
    //unlocked when the BrowserLauncherActivity is destroyed.
    private val lock = Mutex()

    /**
     * Initializes the launcher.
     * @param launcher The launcher to initialize.
     */
    internal fun init(launcher: Launcher) {
        BrowserLauncher.launcher = launcher
        isInitialized.value = true
    }

    /**
     * Resets the launcher.
     */
    internal fun reset() {
        launcher?.authorize?.first?.unregister()
        launcher?.endSession?.first?.unregister()
        isInitialized.value = false
        launcher = null
        try {
            //The BrowserLauncherActivity is destroyed, unlock the lock
            lock.unlock()
        } catch (e: Exception) {
            //ignore if the lock is not locked
        }
    }

    /**
     * Starts the authorization process.
     *
     * @param oidcConfig The configuration for the OpenID Connect client.
     * @return The authorization code.
     * @throws IllegalStateException If the BrowserLauncherActivity is not initialized.
     */
    suspend fun authorize(
        oidcConfig: OidcConfig<BrowserConfig>,
    ): AuthCode {
        try {
            //Wait until the activity is destroyed
            lock.lock()
            val pending = launchActivity()

            // Wait until the launcher is initialized
            // The launcher is initialized in BrowserLauncherActivity
            return isInitialized.first { it }.let {
                launcher?.authorize(oidcConfig, pending)
                    ?: throw IllegalStateException("BrowserLauncherActivity not initialized")
            }
        } catch (e: Exception) {
            lock.unlock()
            throw e
        }

    }

    /**
     * Ends the session.
     *
     * @param oidcConfig The configuration for the OpenID Connect client.
     * @param idToken The ID token for the session.
     * @return A boolean indicating whether the session was ended successfully.
     * @throws IllegalStateException If the BrowserLauncherActivity is not initialized.
     */
    suspend fun endSession(
        oidcConfig: OidcConfig<BrowserConfig>,
        idToken: String
    ): Boolean {
        try {
            //Wait until the activity is destroyed
            lock.lock()
            val pending = launchActivity()
            return isInitialized.first { it }.let {
                launcher?.endSession(Pair(idToken, oidcConfig), pending)
                    ?: throw IllegalStateException("BrowserLauncherActivity not initialized")
            }
        } catch (e: Exception) {
            lock.unlock()
            throw e
        }
    }

    /**
     * Launches the activity if it is not already pending.
     * @return A boolean indicating whether the activity is pending.
     */
    private fun launchActivity(): Boolean {
        return if (launcher == null) {
            val intent = Intent()
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK)
            intent.setClass(ContextProvider.context, BrowserLauncherActivity::class.java)
            ContextProvider.context.startActivity(intent)
            false
        } else {
            true
        }
    }
}
