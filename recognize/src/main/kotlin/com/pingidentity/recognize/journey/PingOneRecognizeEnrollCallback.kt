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
 * On success, the signed JWT, client state, and recognize ID are submitted to the Journey
 * via the five input fields: `IDToken1signedJwt`, `IDToken1clientState`,
 * `IDToken1recognizeId`, `IDToken1clientError`, `IDToken1clientErrorCode`.
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
        return Recognize.setup(buildSetupConfig())
            .fold(
                onSuccess = { Recognize.enroll(buildEnrollConfig(retrieveSelfie = resolvedConfig.retrieveSelfie)) },
                onFailure = { Result.failure(it) }
            )
            .onSuccess { success ->
                submitResult(
                    signedJwt = success.signedJwt ?: "",
                    clientState = success.clientState ?: "",
                    recognizeId = success.keylessId,
                    clientError = "",
                    clientErrorCode = "",
                )
            }
            .onFailure { error ->
                val ex = error.asRecognizeException()
                submitResult(
                    signedJwt = "",
                    clientState = "",
                    recognizeId = "",
                    clientError = ex.message,
                    clientErrorCode = ex.code.toString(),
                )
            }
            .map { success ->
                RecognizeSuccess(
                    selfie = success.enrollmentFrame,
                    signedJwt = success.signedJwt,
                    clientState = success.clientState,
                    keylessId = success.keylessId,
                )
            }
    }

    private fun submitResult(
        signedJwt: String,
        clientState: String,
        recognizeId: String,
        clientError: String,
        clientErrorCode: String,
    ) {
        if (derivedCallback) {
            setValueCallback(SIGNED_JWT_SUFFIX, signedJwt)
            setValueCallback(CLIENT_STATE_SUFFIX, clientState)
            setValueCallback(RECOGNIZE_ID_SUFFIX, recognizeId)
            setValueCallback(CLIENT_ERROR_SUFFIX, clientError)
            setValueCallback(CLIENT_ERROR_CODE_SUFFIX, clientErrorCode)
        } else {
            input(signedJwt, clientState, recognizeId, clientError, clientErrorCode)
        }
    }
}
