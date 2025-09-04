/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.browser

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import com.pingidentity.browser.BrowserLauncher.EXTRA_REDIRECT_URI
import com.pingidentity.browser.BrowserLauncher.logger
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Activity responsible for launching browser-based authentication flows.
 *
 * This activity serves as a bridge between your app and the browser, handling:
 * 1. Registration of ActivityResultContracts during onCreate
 * 2. Selection of the appropriate browser technology (Auth Tab vs Custom Tab)
 * 3. Creation of the appropriate launcher for the selected browser technology
 * 4. Processing of authentication results when received from the browser
 *
 * The activity automatically determines whether to use Auth Tabs or Custom Tabs based on:
 * - Device support for Auth Tabs
 * - The redirect URI scheme type
 *
 * Auth Tabs are used when:
 * - Auth Tab is supported on the device
 * - The redirect scheme is not HTTP
 * - The redirect scheme is not empty
 *
 * Otherwise, Custom Tabs are used.
 *
 * Note: This activity maintains screen orientation during authentication to prevent
 * potential issues with configuration changes during the auth flow.
 */
internal class BrowserLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lock screen orientation during auth flow to prevent issues with configuration changes
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        // Get redirectUri from intent extras or use default from BrowserLauncher
        val redirectUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_REDIRECT_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_REDIRECT_URI)
        } ?: BrowserLauncher.redirectUri

        val state: MutableStateFlow<ActivityResult?> = MutableStateFlow(null)

        // Check if Auth Tab should be used - we need valid conditions:
        // 1. Auth Tab is supported on the device
        // 2. The redirect scheme is not http (custom scheme recommended for Auth Tabs)
        // 3. The redirect scheme is not empty
        val packageName = CustomTabsClient.getPackageName(this.applicationContext, null)
        val isAuthTabSupported = packageName?.let { CustomTabsClient.isAuthTabSupported(this, it) } == true
        val isValidScheme = redirectUri.scheme?.let { it.isNotEmpty() && !it.equals("http", ignoreCase = true) } ?: false

        if (isAuthTabSupported && isValidScheme) {
            logger.d("Using AuthTab for browser launch with redirectUri: $redirectUri")
            val activityResultLauncher = AuthTabIntent.registerActivityResultLauncher(this) {
                logger.d("Result from AuthTab, resultCode: ${it.resultCode}, redirectUri: ${it.resultUri}")
                val intent = Intent().apply {
                    data = it.resultUri
                }
                state.value = ActivityResult(it.resultCode, intent)
                finish()
            }
            BrowserLauncher.onLauncherCreated(AuthTabIntentLauncher(activityResultLauncher, state))
        } else {
            // Fall back to Custom Tabs when Auth Tabs aren't appropriate
            if (!isAuthTabSupported) {
                logger.d("Using CustomTab because AuthTab is not supported")
            } else {
                logger.d("Using CustomTab because redirect scheme is not valid: ${redirectUri.scheme}")
            }

            val activityResultLauncher =
                registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    logger.d("Result from CustomTabActivity: $it")
                    state.value = it
                    finish()
                }
            BrowserLauncher.onLauncherCreated(CustomTabsIntentLauncher(activityResultLauncher, state))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources when the activity is destroyed
        BrowserLauncher.reset()
    }

}