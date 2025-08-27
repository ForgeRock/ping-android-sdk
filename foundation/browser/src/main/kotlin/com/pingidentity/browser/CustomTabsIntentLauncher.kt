/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.browser

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import com.pingidentity.android.ContextProvider
import com.pingidentity.browser.CustomTabActivity.Companion.URL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Chrome Custom Tabs implementation of [TabIntentLauncher] for launching authentication URLs.
 *
 * This launcher uses standard Chrome Custom Tabs, which are suitable for:
 * - HTTPS redirect URIs
 * - Devices that don't support Auth Tabs
 * - Use cases where Auth Tabs are not required
 *
 * @property activityResultLauncher The ActivityResultLauncher used to launch the Custom Tab intent.
 * @property state The MutableStateFlow used to track the state and result of the authentication process.
 *
 * @see <a href="https://developer.chrome.com/docs/android/custom-tabs/overview">Custom Tabs Overview</a>
 */
internal class CustomTabsIntentLauncher(
    override val activityResultLauncher: ActivityResultLauncher<Intent>,
    override val state: MutableStateFlow<ActivityResult?>
): TabIntentLauncher {

    /**
     * Launches a URL in a Chrome Custom Tab.
     *
     * @param url The authentication URL to launch in the Custom Tab.
     * @param redirectUri The URI to which the authentication server should redirect after completion.
     *                    Note: For Custom Tabs, this parameter is not directly used in the launch
     *                    but should match the redirect URI configured on the server.
     * @param pending A flag indicating if the authorization is already pending.
     *                When true, the launch logic is skipped but the function still waits for result.
     * @return A Result containing the redirect Uri on success, or an appropriate exception on failure.
     */
    override suspend fun launch(
        url: String,
        redirectUri: Uri,
        pending: Boolean,
    ): Result<Uri> {
        if (!pending) {
            // Create and launch the CustomTabActivity with the URL
            val intent = Intent(
                ContextProvider.context,
                CustomTabActivity::class.java
            ).apply {
                putExtra(URL, url)
            }
            activityResultLauncher.launch(intent)
        }

        // Wait for response, an event will update the state under BrowserLauncherActivity
        val activityResult = state.drop(1).filterNotNull().first()

        // Process the result based on result code
        return when (activityResult.resultCode) {
            RESULT_OK -> {
                val uri = activityResult.data?.data
                uri?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("No Uri found in response"))
            }

            RESULT_CANCELED -> {
                val resultData = activityResult.data
                resultData?.let {
                    val error = it.getIntExtra(CustomTabActivity.ERROR, CustomTabActivity.ERROR_OTHER)
                    val errorMessage = it.getStringExtra(CustomTabActivity.ERROR_MESSAGE)

                    // Return different exception types based on the error code
                    return Result.failure(
                        when (error) {
                            CustomTabActivity.ERROR_CANCELED -> BrowserCanceledException()
                            CustomTabActivity.ERROR_ACTIVITY_NOT_FOUND ->
                                ActivityNotFoundException(errorMessage)
                            else -> IllegalStateException("Launch Browser failed: $error: $errorMessage")
                        }
                    )
                }
                // Default cancellation without specific error data
                return Result.failure(BrowserCanceledException())
            }

            else -> Result.failure(IllegalStateException("Launch Browser failed: ${activityResult.resultCode}"))
        }
    }
}