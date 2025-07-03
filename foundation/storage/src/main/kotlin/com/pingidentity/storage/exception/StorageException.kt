/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.exception

/**
 * Exception thrown when there is an error with storage operations.
 *
 * @param message The error message.
 * @param cause The underlying cause of the exception.
 */
class StorageException(
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)