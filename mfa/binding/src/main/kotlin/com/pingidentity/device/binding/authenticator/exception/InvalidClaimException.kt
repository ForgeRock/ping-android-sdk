/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator.exception

/**
 * Exception thrown when JWT claims are invalid or missing.
 *
 * This exception is thrown during JWT signing or verification when custom claims
 * contains reserve name.
 *
 * @param message The error message describing which claim is invalid
 * @param cause The underlying cause of the exception, if any
 */
class InvalidClaimException(
    message: String = "Invalid claim",
    cause: Throwable? = null
) : Exception(message, cause)