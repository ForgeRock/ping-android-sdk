/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import com.pingidentity.recognize.EnrollConfig
import com.pingidentity.recognize.Keyless
import io.keyless.sdk.biom.liveness.LivenessSettings
import io.keyless.sdk.configurations.ClientStateType
import io.keyless.sdk.configurations.OperationInfo
import io.keyless.sdk.core.actions.model.JwtSigningInfo
import io.keyless.sdk.errorshandling.EnrollmentSuccess
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Journey callback that handles PingOne Recognize **enrollment** operations.
 *
 * This callback is returned by [RecognizeCallback] when the server output contains
 * `operationType = "ENROLL"`. All output fields are parsed by [AbstractRecognizeCallback];
 * this class only adds the [enroll] operation.
 *
 * On success, the signed JWT, client state, and keyless ID are submitted to the Journey
 * via the five input fields: `IDToken1signedJwt`, `IDToken1clientState`,
 * `IDToken1keylessId`, `IDToken1clientError`, `IDToken1clientErrorCode`.
 *
 * @see RecognizeCallback
 * @see PingOneRecognizeAuthenticateCallback
 */
class PingOneRecognizeEnrollCallback : AbstractRecognizeCallback() {

    /**
     * Performs the PingOne Recognize enrollment ceremony.
     *
     * Automatically maps every server-supplied output field to the corresponding
     * [EnrollConfig] parameter:
     *
     * | Callback field / mobileSDKOptions key         | EnrollConfig property              |
     * |------------------------------------------------|------------------------------------|
     * | `transactionData`                              | `jwtSigningInfo.claimTransactionData` |
     * | `clientState`                                  | `clientState`                      |
     * | `generateClientState`                          | `generatingClientState`            |
     * | `mobileSDKOptions.operationInfoId`             | `operationInfo.operationId`        |
     * | `mobileSDKOptions.operationInfoPayload`        | `operationInfo.payload`            |
     * | `mobileSDKOptions.operationInfoExternalUserId` | `operationInfo.externalUserId`     |
     * | `mobileSDKOptions.livenessConfiguration`       | `livenessConfiguration`            |
     * | `mobileSDKOptions.livenessEnvironmentAware`    | `livenessEnvironmentAware`         |
     * | `mobileSDKOptions.cameraDelaySeconds`          | `cameraDelaySeconds`               |
     * | `mobileSDKOptions.customSecret`                | `customSecret`                     |
     * | `mobileSDKOptions.shouldRetrieveEnrollmentFrame` | `shouldRetrieveEnrollmentFrame`  |
     * | `mobileSDKOptions.showSuccessFeedback`         | `showSuccessFeedback`              |
     * | `mobileSDKOptions.showFailureFeedback`         | `showFailureFeedback`              |
     * | `mobileSDKOptions.showInstructionsScreen`      | `showInstructionsScreen`           |
     *
     * An optional [block] is applied **after** this mapping, so any property can be
     * overridden by the caller if needed.
     *
     * @param block Optional DSL block to override or extend the [EnrollConfig].
     * @return [Result] containing [EnrollmentSuccess] on success, or a [Throwable] on failure.
     */
    suspend fun enroll(block: EnrollConfig.() -> Unit = {}): Result<EnrollmentSuccess> {
        val configResult = Keyless.config {
            apiKey = this@PingOneRecognizeEnrollCallback.apiKey
            host = listOf(this@PingOneRecognizeEnrollCallback.host)
        }
        configResult.onFailure { return Result.failure(it) }

        // Capture callback values as locals — the DSL lambda changes `this` to EnrollConfig
        val opts = mobileSDKOptions
        val storedTransactionData = transactionData
        val storedClientState = clientState
        val storedGenerateClientState = generateClientState

        val result = Keyless.enroll {
            // ── Top-level output fields ─────────────────────────────────────────
            jwtSigningInfo = JwtSigningInfo(claimTransactionData = storedTransactionData)
            clientState = storedClientState

            storedGenerateClientState.takeIf { it.isNotEmpty() }?.let {
                runCatching { generatingClientState = ClientStateType.valueOf(it) }
            }

            // ── mobileSDKOptions fields ─────────────────────────────────────────
            opts["livenessConfiguration"]?.jsonPrimitive?.contentOrNull?.let {
                runCatching {
                    livenessConfiguration = LivenessSettings.LivenessConfiguration.valueOf(it)
                }
            }

            opts["livenessEnvironmentAware"]?.jsonPrimitive?.contentOrNull?.let {
                livenessEnvironmentAware = it.toBoolean()
            }

            // Build OperationInfo from the three constituent keys
            val opId = opts["operationInfoId"]?.jsonPrimitive?.contentOrNull
            val opPayload = opts["operationInfoPayload"]?.jsonPrimitive?.contentOrNull
            val opExternalUserId = opts["operationInfoExternalUserId"]?.jsonPrimitive?.contentOrNull
            if (opId != null || opPayload != null || opExternalUserId != null) {
                operationInfo = OperationInfo(
                    opId ?: "",
                    opPayload ?: "",
                    opExternalUserId ?: ""
                )
            }

            opts["cameraDelaySeconds"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.let {
                cameraDelaySeconds = it
            }

            opts["customSecret"]?.jsonPrimitive?.contentOrNull?.let {
                customSecret = it
            }

            opts["shouldRetrieveEnrollmentFrame"]?.jsonPrimitive?.contentOrNull?.let {
                shouldRetrieveEnrollmentFrame = it.toBoolean()
            }

            opts["showSuccessFeedback"]?.jsonPrimitive?.contentOrNull?.let {
                showSuccessFeedback = it.toBoolean()
            }

            opts["showFailureFeedback"]?.jsonPrimitive?.contentOrNull?.let {
                showFailureFeedback = it.toBoolean()
            }

            opts["showInstructionsScreen"]?.jsonPrimitive?.contentOrNull?.let {
                showInstructionsScreen = it.toBoolean()
            }

            // Allow the caller to override any of the above
            apply(block)
        }

        result.onSuccess { success ->
            // IDToken1signedJwt, IDToken1clientState, IDToken1keylessId,
            // IDToken1clientError, IDToken1clientErrorCode
            input(
                success.signedJwt ?: "",
                success.clientState ?: "",
                success.keylessId,
                "",
                ""
            )
        }.onFailure { error ->
            input("", "", "", error.message ?: "UNKNOWN_ERROR", "")
        }
        return result
    }
}
