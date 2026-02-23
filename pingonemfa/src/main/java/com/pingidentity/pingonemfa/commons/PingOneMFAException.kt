/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons

/**
 * Root exception type for all PingOne MFA SDK errors.
 *
 * This exception optionally wraps an underlying cause while providing
 * a domain-specific error type for SDK consumers.
 *
 * @param message Human-readable description of the error.
 * @param cause   The original exception that triggered this failure, if any.
 */
class PingOneMFAException(
    message: String?=null,
    cause: Throwable? = null
) : Exception(message, cause)