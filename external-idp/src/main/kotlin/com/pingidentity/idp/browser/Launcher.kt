/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.browser

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.os.OperationCanceledException
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * The Launcher class is responsible for handling the authorization process
 * using an ActivityResultLauncher and a MutableStateFlow to track the state.
 *
 * @property launcher The ActivityResultLauncher used to launch the authorization intent.
 * @property state The MutableStateFlow used to track the state of the authorization process.
 */
internal class Launcher(
    val launcher: ActivityResultLauncher<Intent>,
    val state: MutableStateFlow<ActivityResult?>
) {
    /**
     * Authorizes a request using the provided intent.
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
            launcher.launch(request)
        }

        // Wait for response, an event will update the state under [BrowserLauncherActivity]
        val result = state.drop(1).filterNotNull().first()

        when (result.resultCode) {
            RESULT_OK -> {
                val uri = result.data?.data
                uri?.let {
                    return Result.success(it)
                } ?: return Result.failure(IllegalStateException("No Uri found in response"))
            }

            RESULT_CANCELED -> return Result.failure(OperationCanceledException("CustomTab closed"))
            else -> return Result.failure(UnknownError("Launch CustomTab failed: ${result.resultCode}"))
        }
    }
}