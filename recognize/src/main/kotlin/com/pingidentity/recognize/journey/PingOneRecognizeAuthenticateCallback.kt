/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import com.pingidentity.recognize.AuthConfig
import com.pingidentity.recognize.Keyless
import io.keyless.sdk.biom.liveness.LivenessSettings
import io.keyless.sdk.configurations.ClientStateType
import io.keyless.sdk.configurations.OperationInfo
import io.keyless.sdk.configurations.PresentationStyle
import io.keyless.sdk.core.actions.model.JwtSigningInfo
import io.keyless.sdk.errorshandling.AuthenticationSuccess
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Journey callback that handles PingOne Recognize **authentication** operations.
 *
 * This callback is returned by [RecognizeCallback] when the server output contains
 * `operationType = "AUTHENTICATE"`. All output fields are parsed by [AbstractRecognizeCallback];
 * this class only adds the [authenticate] operation.
 *
 * On success, the signed JWT and client state are submitted to the Journey via the five
 * input fields: `IDToken1signedJwt`, `IDToken1clientState`, `IDToken1keylessId` (empty),
 * `IDToken1clientError`, `IDToken1clientErrorCode`.
 *
 * @see RecognizeCallback
 * @see PingOneRecognizeEnrollCallback
 */
class PingOneRecognizeAuthenticateCallback : AbstractRecognizeCallback() {

    /**
     * Performs the PingOne Recognize authentication ceremony.
     *
     * Automatically maps every server-supplied output field to the corresponding
     * [AuthConfig] parameter:
     *
     * | Callback field / mobileSDKOptions key          | AuthConfig property                  |
     * |------------------------------------------------|--------------------------------------|
     * | `transactionData`                              | `jwtSigningInfo.claimTransactionData` |
     * | `generateClientState`                          | `generatingClientState`              |
     * | `mobileSDKOptions.operationInfoId`             | `operationInfo.operationId`          |
     * | `mobileSDKOptions.operationInfoPayload`        | `operationInfo.payload`              |
     * | `mobileSDKOptions.operationInfoExternalUserId` | `operationInfo.externalUserId`       |
     * | `mobileSDKOptions.livenessConfiguration`       | `livenessConfiguration`              |
     * | `mobileSDKOptions.livenessEnvironmentAware`    | `livenessEnvironmentAware`           |
     * | `mobileSDKOptions.cameraDelaySeconds`          | `cameraDelaySeconds`                 |
     * | `mobileSDKOptions.showSuccessFeedback`         | `showSuccessFeedback`                |
     * | `mobileSDKOptions.presentation`                | `presentationStyle`                  |
     *
     * An optional [block] is applied **after** this mapping, so any property can be
     * overridden by the caller if needed.
     *
     * @param block Optional DSL block to override or extend the [AuthConfig].
     * @return [Result] containing [AuthenticationSuccess] on success, or a [Throwable] on failure.
     */
    suspend fun authenticate(block: AuthConfig.() -> Unit = {}): Result<AuthenticationSuccess> {
        val configResult = Keyless.config {
            apiKey = this@PingOneRecognizeAuthenticateCallback.apiKey
            host = listOf(this@PingOneRecognizeAuthenticateCallback.host)
        }
        configResult.onFailure { return Result.failure(it) }

        // Capture callback values as locals — the DSL lambda changes `this` to AuthConfig
        val opts = mobileSDKOptions
        val storedTransactionData = transactionData
        val storedGenerateClientState = generateClientState

        val result = Keyless.authenticate {
            // ── Top-level output fields ─────────────────────────────────────────
            jwtSigningInfo = JwtSigningInfo(claimTransactionData = storedTransactionData)

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

            opts["showSuccessFeedback"]?.jsonPrimitive?.contentOrNull?.let {
                showSuccessFeedback = it.toBoolean()
            }

            opts["presentation"]?.jsonPrimitive?.contentOrNull?.let {
                runCatching { presentationStyle = PresentationStyle.valueOf(it) }
            }

            // Allow the caller to override any of the above
            apply(block)
        }

        result.onSuccess { success ->
            // IDToken1signedJwt, IDToken1clientState, IDToken1keylessId (empty for auth),
            // IDToken1clientError, IDToken1clientErrorCode
            input(
                success.signedJwt ?: "",
                success.clientState ?: "",
                "",
                "",
                ""
            )
        }.onFailure { error ->
            input("", "", "", error.message ?: "UNKNOWN_ERROR", "")
        }
        return result
    }
}
