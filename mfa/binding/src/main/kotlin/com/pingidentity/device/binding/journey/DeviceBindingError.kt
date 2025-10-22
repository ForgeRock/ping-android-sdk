/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.journey

import com.pingidentity.device.binding.authenticator.exception.AbortException
import com.pingidentity.device.binding.authenticator.exception.BiometricAuthenticationException
import com.pingidentity.device.binding.authenticator.exception.DeviceNotRegisteredException
import com.pingidentity.device.binding.authenticator.exception.DeviceNotSupportedException
import com.pingidentity.device.binding.authenticator.exception.InvalidClaimException
import com.pingidentity.device.binding.authenticator.exception.InvalidCredentialException
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Error code indicating that the device binding operation exceeded the configured timeout period.
 * This occurs when authentication or key generation takes longer than expected.
 */
private const val TIMEOUT = "Timeout"

/**
 * Error code indicating that the device binding operation was aborted due to user action,
 * authentication failure, invalid credentials, or other recoverable errors.
 */
private const val ABORT = "Abort"

/**
 * Error code indicating that the device does not support the required authentication method
 * or cryptographic capabilities needed for device binding.
 */
private const val UNSUPPORTED = "Unsupported"

/**
 * Error code indicating that the device has not been previously registered and cannot
 * perform operations that require existing cryptographic keys.
 */
private const val CLIENT_NOT_REGISTERED = "ClientNotRegistered"

/**
 * Converts various exception types into standardized client error codes for server communication.
 *
 * This function maps internal SDK exceptions to predefined error codes that can be safely
 * sent to the authentication server. It handles different categories of errors:
 *
 * - **Registration errors**: When the device lacks required cryptographic keys
 * - **Authentication errors**: When user authentication fails or is aborted
 * - **Timeout errors**: When operations exceed configured time limits
 * - **Support errors**: When the device lacks required capabilities
 * - **Cancellation errors**: When operations are cancelled (re-thrown to preserve cancellation semantics)
 * - **General errors**: All other exceptions are treated as abort conditions
 *
 * The function preserves coroutine cancellation semantics by re-throwing CancellationException
 * (except TimeoutCancellationException which is converted to a timeout error code).
 *
 * @param e The exception to convert to a client error code
 * @return A standardized error code string suitable for server communication
 * @throws CancellationException If the exception is a CancellationException (preserves cancellation)
 *
 * @see DeviceNotRegisteredException
 * @see AbortException
 * @see BiometricAuthenticationException
 * @see DeviceNotSupportedException
 * @see TimeoutCancellationException
 */
fun toClientError(e: Throwable): String {
    when (e) {
        is DeviceNotRegisteredException -> {
            return CLIENT_NOT_REGISTERED
        }

        is AbortException, is InvalidClaimException, is InvalidCredentialException, is BiometricAuthenticationException -> {
            return ABORT
        }

        is TimeoutCancellationException -> {
            //For Timeout, it is consider subclass of CancellationException, we want developer to ignore
            //CancellationException in case of configuration change but not TimeoutCancellationException
            return TIMEOUT
        }

        is DeviceNotSupportedException -> {
            return UNSUPPORTED
        }

        is CancellationException -> {
            throw e
        }

        else -> {
            return ABORT
        }
    }
}