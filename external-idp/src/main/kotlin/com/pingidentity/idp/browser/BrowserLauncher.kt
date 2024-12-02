/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.browser

import android.content.Intent
import android.net.Uri
import com.pingidentity.android.ContextProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.lang.IllegalStateException
import java.net.URL

/**
 * The BrowserLauncher object is responsible for managing the [CustomTabActivity]
 */
internal object BrowserLauncher {

    // A state flow to track if the launcher is initialized.
    private val isInitialized = MutableStateFlow(false)
    internal var launcher: Launcher? = null

    /**
     * Initializes the launcher. This method is called by the [BrowserLauncherActivity.onCreate]
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
        launcher?.launcher?.unregister()
        isInitialized.value = false
        launcher = null
    }

    /**
     * Authorizes a URL using the custom tab activity.
     * @param url The URL to authorize.
     * @param pending A flag indicating if the authorization is pending.
     * @return A Result containing the authorized Uri.
     * @throws IllegalStateException if the CustomTabActivity is not initialized.
     */
    suspend fun launch(
        url: URL,
        pending: Boolean = false,
    ): Result<Uri> {
        // Wait until the launcher is initialized
        // The launcher is initialized in BrowserLauncherActivity
        return isInitialized.first { it }.let {
            val intent = Intent(
                ContextProvider.context,
                CustomTabActivity::class.java
            ).apply {
                putExtra(URL, url.toString())
            }
            launcher?.launch(intent, pending)
                ?: throw IllegalStateException("CustomTabActivity not initialized")
        }
    }

}