/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.browser

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.auth.AuthTabIntent
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Auth Tab implementation of [TabIntentLauncher] for launching authentication URLs using
 * Chrome Custom Tabs with Authentication support.
 *
 * @property activityResultLauncher The ActivityResultLauncher used to launch the Auth Tab intent.
 * @property state The MutableStateFlow used to track the state and result of the authentication process.
 *
 * @see <a href="https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab">Auth Tab Documentation</a>
 */
internal class AuthTabIntentLauncher(
    override val activityResultLauncher: ActivityResultLauncher<Intent>,
    override val state: MutableStateFlow<ActivityResult?>
) : TabIntentLauncher {
    /**
     * Launches a URL in an Auth Tab.
     *
     * @param url The authentication URL to launch in the Auth Tab.
     * @param redirectUri The URI to which the authentication server should redirect after completion.
     *                    This URI scheme must not be empty and should typically be a custom scheme
     *                    (e.g., "app://callback") or "https".
     * @param pending A flag indicating if the authorization is already pending.
     *                When true, the launch logic is skipped but the function still waits for result.
     * @return A Result containing the redirect Uri on success, or an appropriate exception on failure.
     * @throws IllegalArgumentException if the redirect URI scheme is empty or if using https scheme
     *                                 without a host.
     */
    override suspend fun launch(
        url: String,
        redirectUri: Uri,
        pending: Boolean,
    ): Result<Uri> {
        // Check if redirect scheme is empty and return failure if so
        val scheme = redirectUri.scheme
        if (scheme.isNullOrEmpty()) {
            return Result.failure(IllegalArgumentException("Redirect URI scheme cannot be empty"))
        }

        var host = ""
        var path = ""
        if (scheme == "https") {
            host = redirectUri.host ?: throw IllegalArgumentException("Redirect URI host cannot be null for https scheme")
            path = redirectUri.path ?: ""
        }

        if (!pending) {
            val builder = AuthTabIntent.Builder()
            // Apply any customizations defined in the global BrowserLauncher
            BrowserLauncher.authTabCustomizer(builder)
            val authTabIntent = builder.build()
            // Apply any intent customizations
            BrowserLauncher.intentCustomizer(authTabIntent.intent)

            // Launch with appropriate parameters based on scheme type
            if (scheme == "https") {
                // For https schemes, we need to specify host and path
                authTabIntent.launch(activityResultLauncher, url.toUri(), host, path)
            } else {
                // For custom schemes, we just pass the scheme
                authTabIntent.launch(activityResultLauncher, url.toUri(), scheme)
            }
        }

        // Wait for response, an event will update the state under [BrowserLauncherActivity]
        val activityResult = state.drop(1).filterNotNull().first()

        // Process the result based on result code
        return when (activityResult.resultCode) {
            AuthTabIntent.RESULT_OK -> {
                val uri = activityResult.data?.data
                uri?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("No Uri found in response"))
            }

            AuthTabIntent.RESULT_CANCELED -> {
                Result.failure(BrowserCanceledException())
            }

            else -> Result.failure(IllegalStateException("Launch Browser failed: ${activityResult.resultCode}"))
        }
    }
}