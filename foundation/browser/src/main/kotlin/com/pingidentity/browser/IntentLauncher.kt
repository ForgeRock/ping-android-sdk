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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * The Launcher class is responsible for launching the intent and getting the uri result
 * using an ActivityResultLauncher and a MutableStateFlow to track the state.
 *
 * @property activityResultLauncher The ActivityResultLauncher used to launch the intent.
 * @property state The MutableStateFlow used to track the state of the authorization process.
 */
internal class IntentLauncher(
    val activityResultLauncher: ActivityResultLauncher<Intent>,
    private val state: MutableStateFlow<ActivityResult?>
) {
    /**
     * Launch a Intent and wait for the result.
     *
     * @param request The intent to launch for authorization.
     * @param pending A flag indicating if the authorization is pending.
     * @return A Result containing the authorized Uri or an error.
     */
    suspend fun launch(
        request: Intent,
        pending: Boolean = false,
    ): Result<Uri> {
        if (!pending) {
            // Launch the CustomTabActivity
            activityResultLauncher.launch(request)
        }

        // Wait for response, an event will update the state under [BrowserLauncherActivity]
        val activityResult = state.drop(1).filterNotNull().first()

        when (activityResult.resultCode) {
            RESULT_OK -> {
                val uri = activityResult.data?.data
                uri?.let {
                    return Result.success(it)
                } ?: return Result.failure(IllegalStateException("No Uri found in response"))
            }

            RESULT_CANCELED -> {
                val resultData = activityResult.data
                resultData?.let {
                    val error =
                        it.getIntExtra(CustomTabActivity.ERROR, CustomTabActivity.ERROR_OTHER)
                    val errorMessage = it.getStringExtra(CustomTabActivity.ERROR_MESSAGE)
                    return Result.failure(
                        when (error) {
                            CustomTabActivity.ERROR_CANCELED -> BrowserCanceledException()
                            CustomTabActivity.ERROR_ACTIVITY_NOT_FOUND -> ActivityNotFoundException(
                                errorMessage
                            )

                            else -> IllegalStateException("Launch Browser failed: $error: $errorMessage")
                        }
                    )
                }
                return Result.failure(BrowserCanceledException())
            }

            else -> return Result.failure(IllegalStateException("Launch Browser failed: ${activityResult.resultCode}"))
        }
    }
}