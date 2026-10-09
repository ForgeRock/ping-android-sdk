/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import com.pingidentity.recognize.Recognize
import com.pingidentity.recognize.asRecognizeException
import com.pingidentity.recognize.RecognizeSuccess

/**
 * Journey callback that handles PingOne Recognize **enrollment** operations.
 *
 * This callback is returned by [RecognizeCallback] when the server output contains
 * `operationType = "ENROLL"`. All output fields are parsed by [AbstractRecognizeCallback];
 * this class only adds the [enroll] operation.
 *
 * On success, the signed JWT, client state, recognize ID, and freshly retrieved device public
 * signing key are submitted to the Journey via the six input fields: `IDToken1signedJwt`,
 * `IDToken1clientState`, `IDToken1recognizeId`, `IDToken1devicePublicSigningKey`,
 * `IDToken1clientError`, `IDToken1clientErrorCode`. The signing key is retrieved best-effort
 * after a successful enrollment: if retrieval fails, the enrollment still succeeds and the
 * `devicePublicSigningKey` input is left untouched, mirroring the authentication callback.
 *
 * @see RecognizeCallback
 * @see PingOneRecognizeAuthenticateCallback
 */
class PingOneRecognizeEnrollCallback : AbstractRecognizeCallback() {

    /**
     * Performs the PingOne Recognize enrollment ceremony.
     *
     * | Callback field / mobileSDKOptions key           | EnrollConfig property              |
     * |-------------------------------------------------|------------------------------------|
     * | `transactionData`                               | `jwtSigningInfo.claimTransactionData` |
     * | `clientState`                                   | `clientState`                      |
     * | `generateClientState` (`"true"` → BACKUP)       | `generatingClientState`            |
     * | `mobileSDKOptions.operationInfoId`              | `operationInfo.operationId`        |
     * | `mobileSDKOptions.operationInfoPayload`         | `operationInfo.payload`            |
     * | `mobileSDKOptions.operationInfoExternalUserId`  | `operationInfo.externalUserId`     |
     * | `mobileSDKOptions.livenessConfiguration`        | `livenessConfiguration`            |
     * | `mobileSDKOptions.livenessEnvironmentAware`     | `livenessEnvironmentAware`         |
     * | `mobileSDKOptions.cameraDelaySeconds`           | `cameraDelaySeconds`               |
     * | `mobileSDKOptions.showSuccessFeedback`          | `showSuccessFeedback`              |
     * | `mobileSDKOptions.showFailureFeedback`          | `showFailureFeedback`              |
     * | `mobileSDKOptions.showInstructionsScreen`       | `showInstructionsScreen`           |
     * | `mobileSDKOptions.presentation`                 | `presentationStyle`                |
     * | `mobileSDKOptions.numberOfEnrollmentCircuits`   | `setupConfig.numberOfEnrollmentCircuits` |
     *
     * @return [Result] containing [RecognizeSuccess] on success, or a [Throwable] on failure.
     */
    suspend fun enroll(config: RecognizeEnrollConfig.() -> Unit = {}): Result<RecognizeSuccess> {
        val resolvedConfig = RecognizeEnrollConfig().apply(config)
        val result = Recognize.setup(buildSetupConfig()).fold(
            onSuccess = {
                Recognize.enroll(buildEnrollConfig(retrieveSelfie = resolvedConfig.retrieveSelfie)).fold(
                    onSuccess = { success ->
                        // Best-effort key retrieval (mirrors iOS `try?`): a failed lookup
                        // succeeds the operation and leaves the input slot untouched.
                        Result.success(
                            RecognizeSuccess(
                                selfie = success.enrollmentFrame,
                                signedJwt = success.signedJwt,
                                clientState = success.clientState,
                                recognizeId = success.keylessId,
                                devicePublicSigningKey = Recognize.getDevicePublicSigningKey().getOrNull(),
                            )
                        )
                    },
                    onFailure = { Result.failure(it) },
                )
            },
            onFailure = { Result.failure(it) },
        )

        result.onSuccess { success ->
            submitResult(
                signedJwt = success.signedJwt,
                clientState = success.clientState,
                recognizeId = success.recognizeId,
                devicePublicSigningKey = success.devicePublicSigningKey,
                clientError = "",
                clientErrorCode = "",
            )
        }.onFailure { error ->
            val ex = error.asRecognizeException()
            submitResult(
                signedJwt = "",
                clientState = "",
                recognizeId = "",
                devicePublicSigningKey = "",
                clientError = ex.message,
                clientErrorCode = ex.code.toString(),
            )
        }
        return result
    }

    /**
     * Writes a completed operation's values into the input slots matched by suffix.
     *
     * Mirrors iOS `populateResultInputs`: a `null` result value leaves its input slot
     * untouched, while `clientError` and `clientErrorCode` are always submitted.
     */
    private fun submitResult(
        signedJwt: String?,
        clientState: String?,
        recognizeId: String?,
        devicePublicSigningKey: String?,
        clientError: String,
        clientErrorCode: String,
    ) {
        inputBySuffix(
            buildMap {
                signedJwt?.let { put("signedJwt", it) }
                clientState?.let { put("clientState", it) }
                recognizeId?.let { put("recognizeId", it) }
                devicePublicSigningKey?.let { put("devicePublicSigningKey", it) }
                put("clientError", clientError)
                put("clientErrorCode", clientErrorCode)
            }
        )
    }
}
