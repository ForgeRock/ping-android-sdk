/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator.exception

/**
 * Exception thrown when credentials are invalid or cannot be used.
 *
 * This exception indicates that the provided credentials are invalid, expired, or cannot
 * be used for authentication. Common scenarios include:
 * - Invalid PIN or password
 *
 * @param message The error message describing why the credential is invalid
 * @param cause The underlying cause of the exception, if any
 */
class InvalidCredentialException(
    message: String = "Invalid credential",
    cause: Throwable? = null
) : Exception(message, cause)