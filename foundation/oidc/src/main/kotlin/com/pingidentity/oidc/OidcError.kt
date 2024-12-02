/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.utils.Result
import com.pingidentity.utils.Result.Failure
import com.pingidentity.exception.ApiException
import com.pingidentity.oidc.OidcError.ApiError
import com.pingidentity.oidc.OidcError.AuthorizeError
import com.pingidentity.oidc.OidcError.NetworkError
import com.pingidentity.oidc.OidcError.Unknown
import com.pingidentity.oidc.exception.AuthorizeException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Sealed class for OIDC errors.
 */
sealed class OidcError {
    /**
     * Data class for authorization errors.
     * @property cause The cause of the error.
     */
    data class AuthorizeError(val cause: Throwable) : OidcError()

    /**
     * Data class for network errors.
     * @property cause The cause of the error.
     */
    data class NetworkError(val cause: Throwable) : OidcError()

    /**
     * Data class for API errors.
     * @property code The error code.
     * @property message The error message.
     */
    data class ApiError(val code: Int, val message: String) : OidcError()

    /**
     * Data class for unknown errors.
     * @property cause The cause of the error.
     */
    data class Unknown(val cause: Throwable) : OidcError()
}

/**
 * Function to catch and handle exceptions.
 * @param block The block of code to execute.
 * @return The result of the block execution, or an error.
 */
inline fun <R> catch(block: () -> R): Result<R, OidcError> {
    return try {
        Result.Success(block())
    } catch (e: Throwable) {
        when (e) {
            is CancellationException -> throw e
            is ApiException -> Failure(ApiError(e.status, e.message ?: ""))
            is AuthorizeException -> Failure(AuthorizeError(e))
            is IOException -> Failure(NetworkError(e))
            else -> Failure(Unknown(e))
        }
    }
}