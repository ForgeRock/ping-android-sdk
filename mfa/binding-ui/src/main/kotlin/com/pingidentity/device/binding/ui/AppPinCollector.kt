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
 * Data class representing the prompt information displayed in the PIN collection dialog.
 *
 * This class encapsulates the text content shown to users when they are prompted to
 * enter their application PIN for device binding authentication.
 *
 * @property title The main title text displayed at the top of the PIN dialog.
 *                 Typically describes the action being performed (e.g., "Enter PIN").
 * @property subtitle A secondary line of text providing additional context about the PIN request
 *                    (e.g., "Authentication Required").
 * @property description Detailed explanatory text that provides more information about why
 *                       the PIN is being requested. This can be updated dynamically to show
 *                       error messages or additional instructions.
 */
data class PinPrompt(val title: String = "", val subtitle: String = "", var description: String = "")

/**
 * Collects an application PIN from the user via a Jetpack Compose dialog.
 *
 * This suspending function displays a modal dialog that prompts the user to enter their
 * application PIN. The dialog is displayed as an overlay on top of the provided activity
 * using a [ComposeView], ensuring a consistent Material Design UI experience.
 *
 * The function suspends until the user either:
 * - Enters a PIN and confirms it (returns the PIN as a CharArray)
 * - Cancels the dialog (throws a RuntimeException)
 *
 * ## Usage
 *
 * This function is typically used by the Application PIN authenticator when signing
 * operations require user authentication:
 *
 * ## Security Considerations
 *
 * The PIN is returned as a [CharArray] rather than a String to allow the caller to
 * clear the sensitive data from memory after use by calling `charArray.fill('0')`.
 * This reduces the window of opportunity for sensitive data to be compromised.
 *
 * ## UI Lifecycle
 *
 * The dialog is implemented using Jetpack Compose and is added to the activity's content
 * view hierarchy. When the user completes or cancels PIN entry, the dialog is automatically
 * removed from the view hierarchy. If the coroutine is cancelled externally, the cleanup
 * is handled automatically via [kotlinx.coroutines.CancellableContinuation.invokeOnCancellation].
 *
 * @param activity The Android Activity that will host the PIN collection dialog.
 *                 This activity's UI thread is used for view operations.
 * @param title The main title text to display in the dialog.
 * @param subtitle Secondary text providing context for the PIN request.
 * @param description Detailed explanatory text about the PIN request.
 *
 * @return A [CharArray] containing the PIN entered by the user. The caller is responsible
 *         for clearing this array after use to ensure security.
 *
 * @throws RuntimeException if the user cancels the PIN entry dialog.
 *
 * @see PinPrompt
 * @see PinCollectorDialog
 * @see com.pingidentity.device.binding.authenticator.AppPinAuthenticator
 */
suspend fun collectPin(activity: Activity, title: String, subtitle: String, description: String): CharArray {
    val prompt = PinPrompt(title, subtitle, description)
    return suspendCancellableCoroutine { continuation ->
        // Create a ComposeView to host our dialog
        val composeView = ComposeView(activity).apply {
            setContent {
                var showDialog by remember { mutableStateOf(true) }

                if (showDialog) {
                    PinCollectorDialog(
                        prompt = prompt,
                        onPinEntered = { pin ->
                            showDialog = false
                            continuation.resume(pin.toCharArray())
                        },
                        onCancelled = {
                            showDialog = false
                            continuation.resumeWithException(RuntimeException("PIN collection cancelled by user"))
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
