/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.domerrors.AbortError
import androidx.credentials.exceptions.domerrors.ConstraintError
import androidx.credentials.exceptions.domerrors.DataError
import androidx.credentials.exceptions.domerrors.EncodingError
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.NetworkError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.domerrors.NotSupportedError
import androidx.credentials.exceptions.domerrors.SecurityError
import androidx.credentials.exceptions.domerrors.TimeoutError
import androidx.credentials.exceptions.domerrors.UnknownError
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.api.common.AuthenticatorErrorResponse
import com.google.android.gms.fido.fido2.api.common.ErrorCode
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException

/**
 * Transparent activity for handling Google Play Services FIDO2 authentication flow.
 *
 * This activity provides a headless interface for launching FIDO2 authentication prompts
 * using the Google Play Services FIDO2 API. It manages the PendingIntent lifecycle and
 * provides thread-safe result handling for concurrent FIDO2 operations.
 *
 * **Usage Pattern:**
 * 1. Created automatically by `getPublicKeyCredential()` function
 * 2. Receives PendingIntent from Google Play Services FIDO2 API
 * 3. Launches authentication prompt and handles user interaction
 * 4. Returns result to the suspended coroutine
 * 5. Self-terminates after completing the operation
 *
 * **Thread Safety:**
 * - Uses atomic operations and mutex for concurrent operation prevention
 * - Ensures only one FIDO2 operation can be active at a time
 * - Properly handles cancellation and cleanup
 */
class Fido2NonDiscoverableActivity : Activity() {
    companion object {
        const val FIDO_SIGN_IN_REQUEST_CODE = 99
        const val EXTRA_PENDING_INTENT = "EXTRA_PENDING_INTENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PENDING_INTENT, PendingIntent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_PENDING_INTENT)
        }
        if (pendingIntent == null) {
            // If there's no intent, we can't proceed.
            FidoResultHolder.continuation.getAndSet(null)
                ?.resumeWithException(IllegalArgumentException("PendingIntent missing from intent extras."))
            finish()
            return
        }

        // Launch the FIDO2 prompt. The result will be delivered to onActivityResult.
        startIntentSenderForResult(
            pendingIntent.intentSender,
            FIDO_SIGN_IN_REQUEST_CODE,
            null, 0, 0, 0
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Get the continuation that was stored before this Activity was started.
        val continuation = FidoResultHolder.continuation.getAndSet(null) ?: run {
            // If continuation is null, it was likely already handled or timed out.
            finish()
            return
        }

        if (requestCode == FIDO_SIGN_IN_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    if (data?.hasExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA) == true) {
                        // Success! Deserialize and check for errors
                        val credentialBytes =
                            data.getByteArrayExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA)!!
                        try {
                            val credential =
                                PublicKeyCredential.deserializeFromBytes(credentialBytes)
                            val response = credential.response
                            if (response is AuthenticatorErrorResponse) {
                                // Transform the response to Exception that matches Fido2Callback.handleError
                                val exception = when (response.errorCode) {
                                    ErrorCode.NOT_ALLOWED_ERR ->
                                        GetCredentialCancellationException()

                                    ErrorCode.NOT_SUPPORTED_ERR ->
                                        GetCredentialUnsupportedException()

                                    else ->
                                        handleFidoError(response)
                                }
                                continuation.resumeWithException(exception)
                            } else {
                                continuation.resumeWith(Result.success(credential))
                            }
                        } catch (e: Exception) {
                            continuation.resumeWithException(
                                IllegalArgumentException(
                                    "Failed to deserialize credential",
                                    e
                                )
                            )
                        }
                    } else {
                        continuation.resumeWithException(IllegalArgumentException("FIDO2 result intent was invalid."))
                    }
                }

                RESULT_CANCELED -> {
                    // User cancelled the operation.
                    continuation.resumeWithException(GetCredentialCancellationException())
                }

                else -> {
                    // Handle other potential FIDO2 API errors.
                    continuation.resumeWithException(IllegalStateException("Unexpected Response Code"))
                }
            }
        }

        // Ensure this transparent activity is closed.
        finish()
    }
}

/**
 * Maps Google Play Services FIDO2 error responses to Credential Manager compatible exceptions.
 *
 * This function converts AuthenticatorErrorResponse objects from Google Play Services
 * into GetPublicKeyCredentialDomException objects that are compatible with the
 * Android Credential Manager exception hierarchy.
 *
 * **Error Code Mapping:**
 * - NOT_SUPPORTED_ERR → NotSupportedError
 * - INVALID_STATE_ERR → InvalidStateError
 * - SECURITY_ERR → SecurityError
 * - NETWORK_ERR → NetworkError
 * - ABORT_ERR → AbortError
 * - TIMEOUT_ERR → TimeoutError
 * - CONSTRAINT_ERR → ConstraintError
 * - DATA_ERR → DataError
 * - NOT_ALLOWED_ERR → NotAllowedError
 * - ENCODING_ERR → EncodingError
 * - UNKNOWN_ERR → UnknownError
 *
 * @param errorResponse The AuthenticatorErrorResponse from Google Play Services
 * @return A GetPublicKeyCredentialDomException compatible with Credential Manager
 */
internal fun handleFidoError(errorResponse: AuthenticatorErrorResponse): GetPublicKeyCredentialDomException {
    val fidoErrorCode = errorResponse.errorCode

    // Map the FIDO ErrorCode to the standard DOMException name
    val domExceptionName = when (fidoErrorCode) {
        ErrorCode.NOT_SUPPORTED_ERR -> NotSupportedError()
        ErrorCode.INVALID_STATE_ERR -> InvalidStateError()
        ErrorCode.SECURITY_ERR -> SecurityError()
        ErrorCode.NETWORK_ERR -> NetworkError()
        ErrorCode.ABORT_ERR -> AbortError()
        ErrorCode.TIMEOUT_ERR -> TimeoutError()
        ErrorCode.CONSTRAINT_ERR -> ConstraintError()
        ErrorCode.DATA_ERR -> DataError()
        ErrorCode.NOT_ALLOWED_ERR -> NotAllowedError()
        ErrorCode.ENCODING_ERR -> EncodingError() // Map encoding error to data error
        ErrorCode.UNKNOWN_ERR -> UnknownError()
        else -> UnknownError() // Fallback for any unmapped error codes
    }

    // Construct the exception required by Credential Manager
    return GetPublicKeyCredentialDomException(domExceptionName, errorResponse.errorMessage)
}

/**
 * Thread-safe holder for FIDO2 operation state.
 *
 * This object manages the state for ongoing FIDO2 operations, ensuring thread safety
 * and preventing concurrent operations that could interfere with each other.
 *
 * **Concurrency Control:**
 * - Uses AtomicReference for thread-safe continuation storage
 * - Employs Mutex to serialize access to the FIDO2 operation
 * - Prevents multiple simultaneous FIDO2 operations
 */
internal object FidoResultHolder {
    val continuation = AtomicReference<CancellableContinuation<PublicKeyCredential>?>(null)
    val mutex = Mutex()
}

/**
 * Performs FIDO2 authentication using Google Play Services with thread-safe operation handling.
 *
 * This function provides a coroutine-based interface to Google Play Services FIDO2 authentication,
 * with built-in concurrency control to prevent multiple simultaneous operations. It launches a
 * transparent activity to handle user interaction and returns the result asynchronously.
 *
 * **Concurrency Behavior:**
 * - Only one FIDO2 operation can be active at a time (enforced by mutex)
 * - If another operation is already in progress, subsequent calls will wait
 * - Automatic cleanup on cancellation or completion
 *
 * **Error Handling:**
 * - Converts Google Play Services exceptions to Credential Manager compatible format
 * - Handles user cancellation, timeouts, and authenticator errors
 * - Provides detailed error mapping for different failure scenarios
 *
 * @param context The Android context for launching the authentication flow
 * @param requestOptions The FIDO2 authentication request options
 * @return The authenticated PublicKeyCredential on success
 * @throws Exception Various credential-related exceptions on failure
 */
internal suspend fun getPublicKeyCredential(
    context: Context,
    requestOptions: PublicKeyCredentialRequestOptions
): PublicKeyCredential = FidoResultHolder.mutex.withLock {
    // 1. Get the Fido2ApiClient
    val fido2ApiClient = Fido.getFido2ApiClient(context)

    // 2. Await the PendingIntent from the FIDO2 API
    val pendingIntent =
        fido2ApiClient.getSignPendingIntent(requestOptions).await()

    // 3. Suspend the coroutine and wait for the Activity result
    val credential = suspendCancellableCoroutine { continuation ->
        // Store the continuation so the Activity can resume it later
        if (!FidoResultHolder.continuation.compareAndSet(null, continuation)) {
            continuation.resumeWithException(IllegalStateException("Another FIDO2 operation is already in progress."))
            return@suspendCancellableCoroutine
        }

        // Clean up the holder if the coroutine is cancelled
        continuation.invokeOnCancellation {
            FidoResultHolder.continuation.set(null)
        }

        // Start the headless activity to launch the FIDO prompt
        val intent = Intent(context, Fido2NonDiscoverableActivity::class.java).apply {
            putExtra(Fido2NonDiscoverableActivity.EXTRA_PENDING_INTENT, pendingIntent)
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK // Required when starting from a non-Activity context
        }
        context.startActivity(intent)
    }

    // 4. Return the PublicKeyCredential object directly
    return@withLock credential
}