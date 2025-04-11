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
import androidx.browser.customtabs.CustomTabsIntent
import com.pingidentity.android.ContextProvider
import com.pingidentity.browser.CustomTabActivity.Companion.URL
import com.pingidentity.logger.Logger
import com.pingidentity.logger.NONE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URL

/**
 * The BrowserLauncher object is responsible for managing the [CustomTabActivity]
 */
object BrowserLauncher {

    // The logger for the BrowserLauncher
    var logger: Logger = Logger.NONE
    // A state flow to track if the launcher is initialized.
    private val isInitialized = MutableStateFlow(false)
    private var intentLauncher: IntentLauncher? = null

    //Mutex lock to control the lifecycle of the BrowserLauncherActivity
    //This is used to ensure that the BrowserLauncherActivity is processed in a synchronous manner,
    //it is locked when the BrowserLauncherActivity is launched and
    //unlocked when the BrowserLauncherActivity is destroyed.
    private val lock = Mutex()

    /**
     * A customizer for [CustomTabsIntent.Builder]
     */
    var customTabsCustomizer: CustomTabsIntent.Builder.() -> Unit = {}

    /**
     * A customizer for [Intent], the [CustomTabsIntent] that will be
     * launched by [CustomTabsIntent.launchUrl]
     */
    var intentCustomizer: Intent.() -> Unit = {}

    /**
     * Initializes the launcher. This method is called by the [BrowserLauncherActivity.onCreate]
     * @param intentLauncher The launcher to initialize.
     */
    internal fun onLauncherCreated(intentLauncher: IntentLauncher) {
        this.intentLauncher = intentLauncher
        isInitialized.value = true
    }

    /**
     * Resets the launcher.
     */
    internal fun reset() {
        intentLauncher?.activityResultLauncher?.unregister()
        isInitialized.value = false
        intentLauncher = null
    }

    /**
     * Launch a URL using the custom tab activity.
     * @param url The URL to authorize.
     * @param pending A flag indicating if the authorization is pending.
     * @return A Result containing the authorized Uri.
     * @throws IllegalStateException if the CustomTabActivity is not initialized.
     */
    private suspend fun launch(
        url: URL,
        pending: Boolean = false,
    ): Result<Uri> {
        // Wait until the launcher is initialized
        // The launcher is initialized in BrowserLauncherActivity
        return isInitialized.first { it }.let {
            logger.d("BrowserLauncherActivity is initialized")
            val intent = Intent(
                ContextProvider.context,
                CustomTabActivity::class.java
            ).apply {
                putExtra(URL, url.toString())
            }
            intentLauncher?.launch(intent, pending)
                ?: throw IllegalStateException("CustomTabActivity not initialized")
        }
    }

    /**
     * Launches the activity if it is not already pending.
     * @return A boolean indicating whether the activity is pending.
     */
    private fun launchIfNotPending(): Boolean {
        // If launcher is not null, it means the Activity is resumed from backstack
        // We don't need to launch the Activity again
        return if (intentLauncher == null) {
            logger.d("Launching the BrowserLauncherActivity")
            val intent = Intent()
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK)
            intent.setClass(ContextProvider.context, BrowserLauncherActivity::class.java)
            ContextProvider.context.startActivity(intent)
            false
        } else {
            true
        }
    }

    /**
     * Launch the provided URL with Browser, get the response from browser through redirect
     *
     * @param url The URL to launch the browser.
     * @return The redirect uri
     */
    suspend fun launch(url: URL): Result<Uri> = lock.withLock {
        val pending = launchIfNotPending()
        return launch(url, pending)
    }

}