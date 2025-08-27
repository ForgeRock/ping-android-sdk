/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.browser

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.logger.NONE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URL

/**
 * The BrowserLauncher is responsible for launching authentication URLs in a browser tab.
 *
 * It supports both regular Custom Tabs and Auth Tabs (enhanced security for OAuth flows).
 * Auth Tabs are used when supported by the device and when the redirect URI uses a custom scheme.
 *
 * This singleton object manages the browser launch lifecycle and coordinates between the
 * various components involved in browser-based authentication.
 *
 * WARNING: GLOBAL CONFIGURATION
 * This object contains global configuration properties that affect all browser behavior. These properties
 * are NOT thread-safe and should be configured before any browser launches occur, ideally during
 * application initialization.
 *
 * @see <a href="https://developer.chrome.com/docs/android/custom-tabs/overview">Chrome Custom Tabs</a>
 * @see <a href="https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab">Auth Tab</a>
 */
object BrowserLauncher {

    // The logger for the BrowserLauncher
    var logger: Logger = Logger.NONE

    /**
     * The redirect URI used for browser authentication.
     *
     * This URI is used when redirecting back from the authentication server to your app.
     * For Auth Tabs, using a custom scheme (e.g., "app://callback") is recommended for better security.
     *
     * WARNING: GLOBAL CONFIGURATION PROPERTY - NOT THREAD-SAFE
     * Set this value during application initialization before any browser launches occur.
     * Changes to this property will affect all subsequent browser launches across the entire app.
     */
    var redirectUri: Uri = "".toUri()

    // A state flow to track if the launcher is initialized.
    private val isInitialized = MutableStateFlow(false)
    private var tabsIntentLauncher: TabIntentLauncher? = null

    // Constant for the extra key used to pass redirect URI
    internal const val EXTRA_REDIRECT_URI = "com.pingidentity.browser.EXTRA_REDIRECT_URI"

    /**
     * Mutex lock to control the lifecycle of the BrowserLauncherActivity.
     *
     * This ensures that browser operations are processed synchronously:
     * - Locked when BrowserLauncherActivity is launched
     * - Unlocked when BrowserLauncherActivity is destroyed
     *
     * This prevents race conditions that could occur with multiple simultaneous browser launches.
     */
    private val lock = Mutex()

    /**
     * A customizer for [CustomTabsIntent.Builder].
     *
     * Use this to customize the appearance and behavior of Custom Tabs, such as:
     * - Setting toolbar color
     * - Configuring exit animations
     * - Adding menu items
     * - Enabling instant apps
     *
     * Example:
     * ```
     * BrowserLauncher.customTabsCustomizer = {
     *     setToolbarColor(Color.BLUE)
     *     setShowTitle(true)
     * }
     * ```
     *
     * WARNING: GLOBAL CONFIGURATION PROPERTY - NOT THREAD-SAFE
     * Set this value during application initialization before any browser launches occur.
     * Changes to this property will affect all subsequent browser launches across the entire app.
     */
    var customTabsCustomizer: CustomTabsIntent.Builder.() -> Unit = {}

    /**
     * A customizer for [AuthTabIntent.Builder].
     *
     * Use this to customize the appearance and behavior of Auth Tabs, such as:
     * - Adding additional custom parameters
     * - Setting extra headers
     *
     * Example:
     * ```
     * BrowserLauncher.authTabCustomizer = {
     *     // Auth Tab customizations
     * }
     * ```
     *
     * WARNING: GLOBAL CONFIGURATION PROPERTY - NOT THREAD-SAFE
     * Set this value during application initialization before any browser launches occur.
     * Changes to this property will affect all subsequent browser launches across the entire app.
     */
    var authTabCustomizer: AuthTabIntent.Builder.() -> Unit = {}

    /**
     * A customizer for the [Intent] that will launch the browser tab.
     *
     * Use this to add additional flags or extras to the browser launch intent.
     *
     * Example:
     * ```
     * BrowserLauncher.intentCustomizer = {
     *     addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
     * }
     * ```
     *
     * WARNING: GLOBAL CONFIGURATION PROPERTY - NOT THREAD-SAFE
     * Set this value during application initialization before any browser launches occur.
     * Changes to this property will affect all subsequent browser launches across the entire app.
     */
    var intentCustomizer: Intent.() -> Unit = {}

    /**
     * Initializes the launcher. This method is called by the [BrowserLauncherActivity.onCreate]
     *
     * @param intentLauncher The launcher to initialize (either AuthTabIntentLauncher or CustomTabsIntentLauncher).
     */
    internal fun onLauncherCreated(intentLauncher: TabIntentLauncher) {
        this.tabsIntentLauncher = intentLauncher
        isInitialized.value = true
    }

    /**
     * Resets the launcher when the browser session completes.
     *
     * This method unregisters the activity result launcher and clears all state,
     * preparing the launcher for the next browser session.
     */
    internal fun reset() {
        tabsIntentLauncher?.activityResultLauncher?.unregister()
        isInitialized.value = false
        tabsIntentLauncher = null
    }

    /**
     * Internal method to launch a URL using the appropriate tab launcher.
     *
     * @param url The URL to launch in the browser.
     * @param redirectUri The URI to which the authentication server should redirect after completion.
     * @param pending A flag indicating if the browser activity is already pending.
     * @return A Result containing the redirect Uri on success, or an appropriate exception on failure.
     * @throws IllegalStateException if the launcher is not properly initialized.
     */
    private suspend fun launch(
        url: URL,
        redirectUri: Uri,
        pending: Boolean = false,
    ): Result<Uri> {
        // Wait until the launcher is initialized
        // The launcher is initialized in BrowserLauncherActivity
        return isInitialized.first { it }.let {
            logger.d("BrowserLauncherActivity is initialized")
            tabsIntentLauncher?.launch(url.toString(), redirectUri, pending)
                ?: throw IllegalStateException("Browser launcher not initialized")
        }
    }

    /**
     * Launches the BrowserLauncherActivity if not already pending.
     *
     * @param redirectUri The URI to which the authentication server should redirect after completion.
     * @return A boolean indicating whether the activity is already pending (true) or was newly launched (false).
     */
    private fun launchIfNotPending(redirectUri: Uri): Boolean {
        // If launcher is not null, it means the Activity is resumed from backstack
        // We don't need to launch the Activity again
        return if (tabsIntentLauncher == null) {
            logger.d("Launching the BrowserLauncherActivity")
            val intent = Intent()
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK)
            intent.setClass(ContextProvider.context, BrowserLauncherActivity::class.java)
            // Pass the redirectUri to BrowserLauncherActivity
            intent.putExtra(EXTRA_REDIRECT_URI, redirectUri)
            ContextProvider.context.startActivity(intent)
            false
        } else {
            true
        }
    }

    /**
     * Launches a URL in a browser tab and waits for authentication to complete.
     *
     * This method handles the complete lifecycle of a browser-based authentication flow:
     * 1. Launches the appropriate browser tab (Auth Tab or Custom Tab based on device support and URI scheme)
     * 2. Waits for the authentication process to complete
     * 3. Returns the redirect URI containing authentication results
     *
     * The method uses a mutex lock to ensure only one browser launch can happen at a time.
     *
     * @param url The authentication URL to launch in the browser.
     * @param redirectUri The URI to which the authentication server should redirect after completion.
     *                    If not provided, the global redirectUri property is used.
     * @return A Result containing the redirect Uri with authentication results on success,
     *         or an appropriate exception on failure.
     */
    suspend fun launch(url: URL, redirectUri: Uri = this.redirectUri): Result<Uri> = lock.withLock {
        val pending = launchIfNotPending(redirectUri)
        return launch(url, redirectUri, pending)
    }

}