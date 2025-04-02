/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.exception

/**
 * Exception class for authorization errors.
 */
open class AuthorizeException : Exception {
    /**
     * Constructs a new AuthorizeException with the specified detail message.
     * @param message The detail message.
     */
    constructor(message: String) : super(message)

    /**
     * Constructs a new AuthorizeException with the specified detail message and cause.
     * @param message The detail message.
     * @param cause The cause of the exception.
     */
    constructor(message: String, cause: Throwable) : super(message, cause)
}