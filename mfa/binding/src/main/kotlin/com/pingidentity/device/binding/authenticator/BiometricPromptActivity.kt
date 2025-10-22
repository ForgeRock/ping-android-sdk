/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import android.os.Bundle
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.pingidentity.device.binding.authenticator.BiometricOperationState.continuation
import com.pingidentity.device.binding.authenticator.BiometricOperationState.mutex
import com.pingidentity.device.binding.authenticator.BiometricOperationState.privateKey
import com.pingidentity.device.binding.authenticator.BiometricOperationState.promptInfo
import com.pingidentity.device.binding.authenticator.exception.BiometricAuthenticationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.PrivateKey
import java.security.Signature
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException

/**
 * Headless Activity that manages biometric authentication prompts using AndroidX BiometricPrompt.
 *
 * This Activity provides a transparent UI container for displaying biometric authentication dialogs
 * without showing any visible content to the user. It serves as a bridge between coroutine-based
 * authentication calls and the Android BiometricPrompt APIs, which require an Activity context
 * for proper lifecycle management.
 *
 * Example usage (typically called indirectly):
 * ```kotlin
 * // Called through authenticateWithBiometric function
 * val result = authenticateWithBiometric(context, promptInfo, privateKey)
 * ```
 *
 * @see BiometricOperationState
 * @see authenticateWithBiometric
 * @see BiometricPrompt
 * @see BiometricAuthenticationException
 *
 * @since 1.0.0
 */
class BiometricPromptActivity : FragmentActivity() {

    /**
     * Activity creation and biometric prompt initialization.
     *
     * This method handles the complete lifecycle of biometric authentication within the Activity.
     * It retrieves authentication parameters from the shared state, configures the BiometricPrompt
     * with appropriate callbacks, and launches the authentication process with proper error handling.
     *
     * @param savedInstanceState Bundle containing saved state (not used due to transient nature)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val promptInfo = BiometricOperationState.promptInfo
        val privateKey = BiometricOperationState.privateKey

        if (promptInfo == null) {
            // If PromptInfo is missing, we can't proceed
            BiometricOperationState.continuation.getAndSet(null)
                ?.resumeWithException(IllegalArgumentException("PromptInfo not available in BiometricOperationState."))
            finish()
            return
        }

        // Create the BiometricPrompt with authentication callback
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt =
            BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                /**
                 * Handles biometric authentication errors and system failures.
                 *
                 * This callback is invoked when biometric authentication fails due to:
                 * - Hardware unavailability or malfunction
                 * - User cancellation or timeout
                 * - Too many failed attempts (lockout scenarios)
                 * - System-level biometric service errors
                 * - Policy restrictions or security violations
                 *
                 * @param errorCode Biometric authentication error code (from BiometricPrompt constants)
                 * @param errString Human-readable error description from the system
                 */
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    val continuation = BiometricOperationState.continuation.getAndSet(null)
                    continuation?.resumeWithException(
                        BiometricAuthenticationException(errorCode, errString.toString())
                    )
                    // Clear the holder
                    BiometricOperationState.clear()
                    finish()
                }

                /**
                 * Handles successful biometric authentication completion.
                 *
                 * This callback is invoked when biometric authentication succeeds and the user
                 * has been successfully verified. The result contains the authentication outcome
                 * and any associated CryptoObject that was used during the authentication process.
                 *
                 * Result contents:
                 * - **AuthenticationResult**: Contains authentication outcome and metadata
                 * - **CryptoObject**: Available when hardware-backed authentication was used
                 * - **Authentication type**: Indicates which biometric method was used
                 *
                 * @param result BiometricPrompt.AuthenticationResult containing success details
                 *               and any associated CryptoObject from the authentication process
                 */
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val continuation = BiometricOperationState.continuation.getAndSet(null)
                    continuation?.resumeWith(Result.success(result))
                    // Clear the holder
                    BiometricOperationState.clear()
                    finish()
                }
            })

        // Launch the biometric prompt with or without CryptoObject based on privateKey availability
        if (privateKey != null) {
            try {
                // Create a Signature object for RSA private key authentication
                val signature = Signature.getInstance("SHA512withRSA")
                signature.initSign(privateKey)
                //val signature = object : RSASSAProvider() {
                //}.signature(JWSAlgorithm.parse("RS512"), privateKey)

                val cryptoObject = BiometricPrompt.CryptoObject(signature)
                biometricPrompt.authenticate(promptInfo, cryptoObject)
            } catch (e: Exception) {
                //Failed because the key was generated with time-based authentication only
                biometricPrompt.authenticate(promptInfo)
            }
        } else {
            // Launch without CryptoObject if no private key is provided
            biometricPrompt.authenticate(promptInfo)
        }
    }
}

/**
 * Thread-safe holder for biometric operation state and parameters.
 *
 * This singleton object manages the complete state for ongoing biometric operations, including
 * input parameters and coroutine continuations, ensuring thread safety and preventing
 * concurrent operations that could interfere with each other.
 *
 * The state holder uses a combination of atomic operations and volatile fields to ensure
 * thread-safe access from multiple coroutines and Activity lifecycle events. It serves as
 * a communication bridge between the calling coroutine and the headless Activity that
 * actually performs the biometric authentication.
 *
 * @property continuation AtomicReference holding the coroutine continuation for result delivery
 * @property mutex Mutex preventing concurrent biometric operations
 * @property promptInfo Volatile reference to BiometricPrompt configuration
 * @property privateKey Volatile reference to private key for CryptoObject creation
 *
 * @see BiometricPromptActivity
 * @see authenticateWithBiometric
 *
 * @since 1.0.0
 */
internal object BiometricOperationState {
    /**
     * Atomic reference to the coroutine continuation waiting for biometric authentication results.
     *
     * This field uses AtomicReference to ensure thread-safe storage and retrieval of the
     * continuation object. The atomic compare-and-set operations prevent race conditions
     * when multiple threads attempt to set or clear the continuation simultaneously.
     *
     */
    val continuation =
        AtomicReference<CancellableContinuation<BiometricPrompt.AuthenticationResult>?>(null)

    /**
     * Mutex ensuring only one biometric authentication operation can be active at a time.
     *
     * This mutex serializes access to the biometric authentication system, preventing
     * multiple simultaneous operations that could interfere with each other or cause
     * system-level conflicts with the BiometricPrompt APIs.
     *
     */
    val mutex = Mutex()

    /**
     * Volatile reference to the BiometricPrompt configuration for the current operation.
     *
     * This field holds the prompt configuration that defines the appearance and behavior
     * of the biometric authentication dialog. The volatile modifier ensures that changes
     * made by one thread are immediately visible to other threads.
     *
     */
    @Volatile
    var promptInfo: BiometricPrompt.PromptInfo? = null

    /**
     * Volatile reference to the private key for CryptoObject-based authentication.
     *
     * This field optionally holds a private key that will be used to create a CryptoObject
     * for hardware-backed biometric authentication. When null, the authentication will
     * use time-based validation instead of CryptoObject binding.
     *
     */
    @Volatile
    var privateKey: PrivateKey? = null

    /**
     * Atomically clears all operation state to prevent memory leaks and ensure clean slate.
     *
     * This method performs complete cleanup of the biometric operation state, ensuring
     * that no references are retained after authentication completion or cancellation.
     * The cleanup is performed atomically to prevent race conditions during state clearing.
     *
     */
    fun clear() {
        continuation.set(null)
        promptInfo = null
        privateKey = null
    }
}

/**
 * Performs biometric authentication using AndroidX BiometricPrompt with thread-safe operation handling.
 *
 * This function provides a coroutine-based interface to AndroidX BiometricPrompt authentication,
 * with built-in concurrency control to prevent multiple simultaneous operations. It launches a
 * transparent Activity to handle user interaction and returns the result asynchronously through
 * Kotlin coroutines.
 *
 * **Concurrency Behavior:**
 * - Only one biometric operation can be active at a time (enforced by mutex)
 * - If another operation is already in progress, subsequent calls will suspend until completion
 * - Automatic cleanup on cancellation, completion, or error
 * - Thread-safe state management across coroutine boundaries
 *
 * **Error Handling:**
 * - Converts biometric authentication errors to custom [BiometricAuthenticationException]
 * - Handles user cancellation, timeouts, and authentication failures gracefully
 * - Provides detailed error codes and messages for different failure scenarios
 * - Ensures proper cleanup even in error conditions
 *
 * **CryptoObject Integration:**
 * - **With private key**: Creates RSA signature CryptoObject for hardware-backed authentication
 * - **Without private key**: Uses time-based authentication for broader device compatibility
 * - **Automatic fallback**: Gracefully handles CryptoObject creation failures
 *
 *
 * @param context The Android context for launching the authentication flow. Can be Activity
 *                or Application context - function handles both scenarios appropriately.
 * @param promptInfo The BiometricPrompt.PromptInfo configuration defining dialog appearance,
 *                   allowed authenticators, button text, and other UI/UX parameters.
 * @param privateKey Optional private key for CryptoObject-based authentication. When provided,
 *                   creates RSA signature CryptoObject for hardware-backed security. When null,
 *                   uses time-based authentication validation.
 * @return BiometricPrompt.AuthenticationResult containing authentication outcome and any
 *         associated CryptoObject that was used during the authentication process.
 * @throws BiometricAuthenticationException on authentication failure, user cancellation,
 *         hardware unavailability, or other biometric system errors. Exception includes
 *         detailed error code and message for specific failure diagnosis.
 * @throws IllegalStateException if another biometric operation is already in progress
 *         (should not occur due to mutex, but included for completeness).
 *
 * @see BiometricPromptActivity
 * @see BiometricOperationState
 * @see BiometricPrompt.AuthenticationResult
 * @see BiometricAuthenticationException
 *
 * @since 1.0.0
 */
suspend fun authenticateWithBiometric(
    context: android.content.Context,
    promptInfo: BiometricPrompt.PromptInfo,
    privateKey: PrivateKey?
): BiometricPrompt.AuthenticationResult = BiometricOperationState.mutex.withLock {

    // Suspend the coroutine and wait for the Activity result
    val result = suspendCancellableCoroutine { continuation ->
        // Store the continuation so the Activity can resume it later
        if (!BiometricOperationState.continuation.compareAndSet(null, continuation)) {
            continuation.resumeWithException(IllegalStateException("Another biometric operation is already in progress."))
            return@suspendCancellableCoroutine
        }

        // Store the parameters in the holder
        BiometricOperationState.promptInfo = promptInfo
        BiometricOperationState.privateKey = privateKey

        // Clean up the holder if the coroutine is cancelled
        continuation.invokeOnCancellation {
            BiometricOperationState.clear()
        }

        // Start the headless activity to launch the biometric prompt
        val intent = android.content.Intent(context, BiometricPromptActivity::class.java).apply {
            flags =
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK // Required when starting from a non-Activity context
        }
        context.startActivity(intent)
    }

    return@withLock result
}
