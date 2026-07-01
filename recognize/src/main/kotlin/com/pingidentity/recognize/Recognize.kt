/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize

import io.keyless.sdk.Keyless
import io.keyless.sdk.configurations.SetupConfig
import io.keyless.sdk.configurations.auth.BiomAuthConfig
import io.keyless.sdk.configurations.enroll.BiomEnrollConfig
import io.keyless.sdk.errorshandling.AuthenticationSuccess
import io.keyless.sdk.errorshandling.EnrollmentSuccess
import io.keyless.sdk.errorshandling.KeylessSdkError
import kotlinx.coroutines.suspendCancellableCoroutine

object Recognize {
    internal suspend fun validateUserAndDeviceActive(): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            Keyless.validateUserAndDeviceActive { result ->
                when (result) {
                    is Keyless.KeylessResult.Success -> {
                        cont.resume(Result.success(Unit)) { cause, _, _ ->
                            cont.cancel(cause)
                        }
                    }
                    is Keyless.KeylessResult.Failure -> {
                        cont.resume(Result.failure(RecognizeException.from(result.error))) { cause, _, _ ->
                            cont.cancel(cause)
                        }
                    }
                }
            }
        }

    internal suspend fun setup(
        setupConfig: SetupConfig
    ) = suspendCancellableCoroutine { continuation ->
        Keyless.configure(setupConfig) { result ->
            when (result) {
                is Keyless.KeylessResult.Success -> {
                    continuation.resume(Result.success(result.value)) { cause, _, _ ->
                        // The Keyless SDK has no cancellation API, so the underlying call
                        // completes regardless. We cancel the continuation so the coroutine
                        // reports cancellation to its caller.
                        continuation.cancel(cause)
                    }
                }

                is Keyless.KeylessResult.Failure -> {
                    continuation.resume(Result.failure(RecognizeException.from(result.error))) { cause, _, _ ->
                        continuation.cancel(cause)
                    }
                }
            }
        }
    }

    suspend fun enroll(
        biomEnrollConfig: BiomEnrollConfig
    ): Result<EnrollmentSuccess> = suspendCancellableCoroutine { cont ->
        Keyless.enroll(configuration = biomEnrollConfig) { result ->
            when(result) {
                is Keyless.KeylessResult.Success -> {
                    cont.resume(Result.success(result.value)) { cause, _, _ ->
                        cont.cancel(cause)
                    }
                }
                is Keyless.KeylessResult.Failure -> {
                    cont.resume(Result.failure(RecognizeException.from(result.error as KeylessSdkError))) { cause, _, _ ->
                        cont.cancel(cause)
                    }
                }
            }
        }
    }

    suspend fun authenticate(
        config: BiomAuthConfig
    ): Result<AuthenticationSuccess> = suspendCancellableCoroutine { cont ->
        Keyless.authenticate(configuration = config) { result ->
            when(result) {
                is Keyless.KeylessResult.Success -> {
                    cont.resume(Result.success(result.value)) { cause, _, _ ->
                        cont.cancel(cause)
                    }
                }

                is Keyless.KeylessResult.Failure -> {
                    cont.resume(Result.failure(RecognizeException.from(result.error as KeylessSdkError))) { cause, _, _ ->
                        cont.cancel(cause)
                    }
                }
            }
        }
    }
}
