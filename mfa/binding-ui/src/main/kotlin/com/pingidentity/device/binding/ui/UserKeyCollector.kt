/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.ui

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Data class representing a user key option that can be selected for device binding authentication.
 *
 * This class encapsulates the information about a registered user key that is displayed
 * to the user when multiple device bindings exist. It provides the necessary details for
 * the user to identify and select the appropriate key for authentication.
 *
 * @property id The unique identifier of the user key. This corresponds to the [com.pingidentity.device.binding.UserKey.id]
 *              and is used internally to identify the selected key.
 * @property username The friendly username or display name associated with this key.
 *                    This is shown in the selection UI to help users identify their keys.
 *                    Corresponds to [com.pingidentity.device.binding.UserKey.username].
 * @property authenticationType The type of authentication used by this key
 *                             (e.g., "BIOMETRIC_ONLY", "BIOMETRIC_OR_DEVICE_CREDENTIAL", "APPLICATION_PIN").
 *                             Displayed to users to help them understand how authentication will work.
 *                             Corresponds to [com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType].
 */
data class UserKeyOption(
    val id: String,
    val username: String,
    val authenticationType: String
)

/**
 * Collects a user key selection from the user via a Jetpack Compose dialog.
 *
 * This suspending function displays a modal dialog that prompts the user to select one
 * of their registered device binding keys when multiple keys exist for the same application.
 * This scenario occurs when:
 * - Multiple users have registered device bindings on the same device
 * - A single user has multiple device bindings with different authentication types
 *
 * The dialog is displayed as an overlay on top of the provided activity using a [ComposeView],
 * ensuring a consistent Material Design UI experience.
 *
 * The function suspends until the user either:
 * - Selects a user key and confirms it (returns the selected UserKeyOption)
 * - Cancels the dialog (throws a RuntimeException)
 *
 * ## Usage Scenarios
 *
 * ### Multi-User Device
 * When multiple users have registered their credentials on a shared device, this dialog
 * allows each user to select their own key for authentication:
 *
 * ## UI Lifecycle
 *
 * The dialog is implemented using Jetpack Compose and is added to the activity's content
 * view hierarchy. When the user completes or cancels key selection, the dialog is
 * automatically removed from the view hierarchy. If the coroutine is cancelled externally,
 * the cleanup is handled automatically via [kotlinx.coroutines.CancellableContinuation.invokeOnCancellation].
 *
 * ## Integration with Device Binding
 *
 * This function is automatically invoked by the device binding framework when:
 * 1. A signing operation requires user key selection
 * 2. Multiple keys are available in storage
 * 3. No custom user key collector has been configured
 *
 * Custom implementations can be provided via [com.pingidentity.device.binding.authenticator.UserKeyAuthenticator]
 * configuration.
 *
 * @param activity The Android Activity that will host the user key selection dialog.
 *                 This activity's UI thread is used for view operations.
 * @param userKeys The list of available user keys to display for selection.
 *                 Defaults to an empty list. If empty, the dialog will show no options.
 *
 * @return The [UserKeyOption] selected by the user.
 *
 * @throws RuntimeException if the user cancels the key selection dialog by pressing
 *                          Cancel, back button, or dismissing the dialog.
 *
 * @see UserKeyOption
 * @see UserKeyDialog
 * @see com.pingidentity.device.binding.authenticator.UserKeyAuthenticator
 * @see com.pingidentity.device.binding.UserKey
 */
suspend fun collectUserKey(
    activity: Activity,
    userKeys: List<UserKeyOption> = emptyList()
): UserKeyOption {
    return suspendCancellableCoroutine { continuation ->
        // Create a ComposeView to host our dialog
        val composeView = ComposeView(activity).apply {
            setContent {
                var showDialog by remember { mutableStateOf(true) }

                if (showDialog) {
                    UserKeyDialog(
                        userKeys = userKeys,
                        onUserKeySelected = { selectedKey ->
                            showDialog = false
                            continuation.resume(selectedKey)
                        },
                        onCancelled = {
                            showDialog = false
                            continuation.resumeWithException(RuntimeException("User key selection cancelled by user"))
                        }
                    )
                }
            }
        }

        // Add the ComposeView to the activity's content
        activity.addContentView(
            composeView,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Clean up when the coroutine is cancelled
        continuation.invokeOnCancellation {
            activity.runOnUiThread {
                (composeView.parent as? android.view.ViewGroup)?.removeView(composeView)
            }
        }
    }
}
