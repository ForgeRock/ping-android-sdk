/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.exception

/**
 * Base exception class for all MFA-related exceptions.
 *
 * @param message The error message.
 * @param cause The underlying cause of the exception.
 */
open class MfaException(
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when an MFA client initialization fails.
 *
 * @param message The error message.
 * @param cause The underlying cause of the exception.
 */
class MfaInitializationException(
    message: String? = null,
    cause: Throwable? = null
) : MfaException(message, cause)

/**
 * Exception thrown when an MFA operation is performed on an uninitialized client.
 *
 * @param message The error message.
 * @param cause The underlying cause of the exception.
 */
class MfaClientNotInitializedException(
    message: String? = "MFA client not initialized",
    cause: Throwable? = null
) : MfaException(message, cause)

/**
 * Exception thrown when an MFA storage operation fails.
 *
 * @param message The error message.
 * @param cause The underlying cause of the exception.
 */
class MfaStorageException(
    message: String? = null,
    cause: Throwable? = null
) : MfaException(message, cause)
