/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons

import com.pingidentity.pingidsdkv2.PingOneSDKError
import com.pingidentity.pingonemfa.util.ErrorParser

/**
 * Exception thrown by suspend variants of the PingOne MFA APIs when an operation fails.
 *
 * The native [PingOneSDKError] type is intentionally not exposed, so hosting apps do not need
 * a direct dependency on the native AAR. All available error information is surfaced
 * through this class.
 *
 * @property internalErrorsList A structured list of [Error] objects parsed from the native SDK
 *   error(s), or `null` when the failure did not originate from the native SDK (e.g. a network
 *   timeout or unexpected exception). Each [Error] contains the numeric error code, message, and
 *   any diagnostic `userInfo` key/value pairs returned by the server — intended for logging and
 *   debugging.
 *
 * ### Usage
 * ```kotlin
 * result.onFailure { e ->
 *     // Quick check — use message for user-facing display
 *     Log.e("MFA", e.message)
 *
 *     // Detailed diagnostics — log each error's code and server-provided userInfo
 *     // Returned error codes are defined in the native SDK; see PingOneSDKError documentation for details:
 *     // https://pingidentity.github.io/pingone-mobile-sdk-android/-ping-one%20-m-f-a%20-android%20-s-d-k/com.pingidentity.pingidsdkv2.error/-ping-one-s-d-k-error-type/index.html
 *     e.internalErrorsList?.forEach { err ->
 *         Log.e("MFA", "code=${err.code} userInfo=${err.userInfo}")
 *     }
 * }
 * ```
 */
class PingOneMFAException private constructor(
    message: String,
    cause: Throwable?,
    val internalErrorsList: List<Error>? = null,
) : Exception(message, cause) {

    /** Creates an exception from a plain message string with no SDK error code or cause. */
    internal constructor(message: String?) : this(
        message = message ?: "Unknown error",
        cause = null,
    )

    /**
     * Wraps an unexpected [Exception], preserving the original cause in the stack trace so
     * it is visible in crash reports and log output.
     */
    internal constructor(cause: Exception) : this(
        message = cause.message ?: "Unknown error",
        cause = cause
    )

    /*
     * Creates an exception from a single [PingOneSDKError] instance, extracting the error code and
     * message and parsing any additional error information into a list of [Error] objects.
     */
    internal constructor(error: PingOneSDKError) : this(
        message = error.message,
        cause = null,
        internalErrorsList = ErrorParser.fromPingOneSDKError(error)
    )

    // Internal factory constructors — accept native SDK type but keep it off the public API surface.
    internal constructor(errors: Array<PingOneSDKError>) : this(
        // errors[0] can be null when the native SDK includes null sentinels in the array;
        // find the first non-null entry and use its message as the summary.
        message = errors.firstOrNull { it != null }?.message ?: "Unknown error",
        cause = null,
        internalErrorsList = ErrorParser.fromPingOneSDKErrors(errors)
    )
}
