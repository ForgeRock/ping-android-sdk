/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize

import io.keyless.sdk.errorshandling.KeylessSdkError

/**
 * Stable public error type for all Recognize SDK failures.
 *
 * Wraps [KeylessSdkError] and re-exposes the fields callers need without
 * requiring a direct dependency on the Keyless SDK types.
 *
 * @property code Numeric error code from the shared cross-platform error model
 *   (see [SharedErrorCode]).
 * @property name Shared error name (e.g. `CORE_USER_NOT_ENROLLED`) matching the numeric
 *   [code]; `null` when the exception was constructed without one.
 * @property message Human-readable description from the native error.
 * @property sdkCode Original native Keyless SDK error code; `null` when the failure never
 *   reached the Keyless SDK.
 * @property debuggingInfo Diagnostic map from [KeylessSdkError.debuggingInfo]. When the error
 *   came from the Keyless SDK, the native code is copied in under the `sdkCode` key
 *   (mirroring iOS).
 */
class RecognizeException(
    val code: Int,
    val name: String? = null,
    override val message: String,
    val sdkCode: Int? = null,
    val debuggingInfo: Map<String, String>,
    cause: Throwable? = null,
) : Exception(message, cause) {

    companion object {
        /**
         * Converts a [KeylessSdkError] into a [RecognizeException].
         *
         * The native code is mapped to the shared error model via [SharedErrorCode.resolve]
         * and copied into `debuggingInfo["sdkCode"]`.
         *
         * @param error The raw Keyless SDK error.
         * @return A [RecognizeException] populated from [error].
         */
        fun from(error: KeylessSdkError): RecognizeException {
            val shared = resolve(error.code)
            return RecognizeException(
                code = shared.code,
                name = shared.name,
                message = error.message ?: "UNKNOWN_ERROR",
                sdkCode = error.code,
                debuggingInfo = error.debuggingInfo + ("sdkCode" to error.code.toString()),
                cause = error,
            )
        }
    }
}

/**
 * Returns this throwable as a [RecognizeException], casting if already one or wrapping otherwise.
 *
 * A wrapped non-[RecognizeException] maps to [SharedErrorCode.SDK_ERROR]: its `name` is set and
 * `sdkCode` is `null` since the failure never reached the Keyless SDK.
 */
internal fun Throwable.asRecognizeException(): RecognizeException =
    this as? RecognizeException
        ?: RecognizeException(
            code = SharedErrorCode.SDK_ERROR.code,
            name = SharedErrorCode.SDK_ERROR.name,
            message = message ?: "UNKNOWN_ERROR",
            debuggingInfo = emptyMap(),
            cause = this,
        )

/**
 * The shared error name and code submitted to the server in the `clientError` and
 * `clientErrorCode` input slots, mirroring iOS `report()`.
 *
 * The Journey filters error outcomes on the shared cross-platform error name, as the web
 * SDK does, so an error without a shared name (an error that is not a
 * [RecognizeException], or one created without a name) is unknown to the shared model and
 * is reported as [SharedErrorCode.SDK_ERROR] / 1000, like on the web SDK. A cooperative
 * `CancellationException` propagates to the caller without populating the error inputs
 * (unlike iOS, whose cancellation checkpoints report `SDK_ERROR`); only a cancel raised
 * inside the Keyless SDK (native 30003) maps to `SDK_USER_CANCELLED`. The human-readable
 * message is not submitted: it stays in [RecognizeException.message].
 */
internal fun RecognizeException.clientErrorReport(): Pair<String, String> =
    if (name == null) {
        SharedErrorCode.SDK_ERROR.name to SharedErrorCode.SDK_ERROR.code.toString()
    } else {
        name to code.toString()
    }
