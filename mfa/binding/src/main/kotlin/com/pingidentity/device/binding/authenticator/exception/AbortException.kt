/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator.exception

/**
 * Exception thrown when the user aborts an authentication operation.
 *
 * This exception is typically thrown when the user cancels a biometric prompt
 * or explicitly aborts the authentication process.
 *
 * @param message The error message describing the abort reason
 * @param cause The underlying cause of the exception, if any
 */
class AbortException(
    message: String = "Authentication aborted by user",
    cause: Throwable? = null
) : Exception(message, cause)