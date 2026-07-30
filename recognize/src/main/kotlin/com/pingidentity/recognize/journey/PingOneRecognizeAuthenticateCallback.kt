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
import io.keyless.sdk.biom.liveness.LivenessSettings
import io.keyless.sdk.configurations.PresentationStyle
import io.keyless.sdk.configurations.auth.BiomAuthConfig
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Journey callback that handles PingOne Recognize **authentication** operations.
 *
 * This callback is returned by [RecognizeCallback] when the server output contains
 * `operationType = "AUTHENTICATE"`. All output fields are parsed by [AbstractRecognizeCallback];
 * this class only adds the [authenticate] operation.
 *
 * On success, the signed JWT and client state are submitted to the Journey via the six
 * input fields: `IDToken1signedJwt`, `IDToken1clientState`, `IDToken1recognizeId` (empty),
 * `IDToken1devicePublicSigningKey`, `IDToken1clientError`, `IDToken1clientErrorCode`.
 *
 * @see RecognizeCallback
 * @see PingOneRecognizeEnrollCallback
 */
class PingOneRecognizeAuthenticateCallback : AbstractRecognizeCallback() {

    /**
     * Resolves which Keyless operation to run and executes it.
     *
     * When `clientState` is non-empty, [Recognize.setup] runs first, then
     * [Recognize.validateUserAndDeviceActive] determines the operation:
     * - Not enrolled → [Recognize.enroll] with the received `clientState` as input.
     * - Enrolled (or no `clientState`) → [Recognize.authenticate] as normal.
     *
     * Auth config field mapping ([BiomAuthConfig] only — enroll fields are handled by [buildEnrollConfig]):
     *
     * | Callback field / mobileSDKOptions key               | BiomAuthConfig property              |
     * |-----------------------------------------------------|--------------------------------------|
     * | `transactionData`                                   | `jwtSigningInfo.claimTransactionData` |
     * | `generateClientState` (`"true"` → BACKUP)           | `generatingClientState`              |
     * | `mobileSDKOptions.operationInfoId`                  | `operationInfo.operationId`          |
     * | `mobileSDKOptions.operationInfoPayload`             | `operationInfo.payload`              |
     * | `mobileSDKOptions.operationInfoExternalUserId`      | `operationInfo.externalUserId`       |
     * | `mobileSDKOptions.livenessConfiguration`            | `livenessConfiguration`              |
     * | `mobileSDKOptions.livenessEnvironmentAware`         | `livenessEnvironmentAware`           |
     * | `mobileSDKOptions.cameraDelaySeconds`               | `cameraDelaySeconds`                 |
     * | `mobileSDKOptions.showSuccessFeedback`              | `showSuccessFeedback`                |
     * | `mobileSDKOptions.presentationStyle`                | `presentationStyle`                  |
     * | `mobileSDKOptions.shouldRemovePin`                  | `shouldRemovePin`                    |
     * | `mobileSDKOptions.numberOfEnrollmentCircuits`       | `setupConfig.numberOfEnrollmentCircuits` |
     *
     * @return [Result] containing [RecognizeSuccess] on success, or a [Throwable] on failure.
     */
    suspend fun authenticate(config: RecognizeAuthenticateConfig.() -> Unit = {}): Result<RecognizeSuccess> {
        val resolvedConfig = RecognizeAuthenticateConfig().apply(config)
        val storedClientState = clientState

        return Recognize.setup(buildSetupConfig())
            .fold(
                onSuccess = {
                    // validateUserAndDeviceActive requires the SDK to be configured first.
                    val shouldEnroll = storedClientState.isNotEmpty() &&
                        !Recognize.validateUserAndDeviceActive().isSuccess
                    if (shouldEnroll) {
                        Recognize.enroll(buildEnrollConfig(clientStateOverride = storedClientState, retrieveSelfie = resolvedConfig.retrieveSelfie))
                            .map { success -> RecognizeSuccess(selfie = success.enrollmentFrame, signedJwt = success.signedJwt, clientState = success.clientState, keylessId = success.keylessId) }
                    } else {
                        Recognize.authenticate(buildAuthConfig(resolvedConfig.retrieveSelfie))
                            .map { success -> RecognizeSuccess(selfie = success.authenticationFrame, signedJwt = success.signedJwt, clientState = success.clientState, keylessId = "") }
                    }
                },
                onFailure = { Result.failure(it) }
            )
            .onSuccess { result ->
                submitResult(
                    signedJwt = result.signedJwt ?: "",
                    clientState = result.clientState ?: "",
                    devicePublicSigningKey = "",
                    clientError = "",
                    clientErrorCode = "",
                )
            }
            .onFailure { error ->
                val ex = error.asRecognizeException()
                submitResult(
                    signedJwt = "",
                    clientState = "",
                    devicePublicSigningKey = "",
                    clientError = ex.message,
                    clientErrorCode = ex.code.toString(),
                )
            }
    }

    private fun buildAuthConfig(retrieveSelfie: Boolean): BiomAuthConfig {
        val opts = mobileSDKOptions
        val base = BiomAuthConfig()
        return BiomAuthConfig(
            shouldRemovePin = opts["shouldRemovePin"]?.jsonPrimitive?.contentOrNull
                ?.toBoolean() ?: base.shouldRemovePin,
            operationInfo = buildOperationInfo(),
            jwtSigningInfo = buildJwtSigningInfo(),
            dynamicLinkingInfo = base.dynamicLinkingInfo,
            livenessConfiguration = opts["livenessConfiguration"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { LivenessSettings.LivenessConfiguration.valueOf(it) }.getOrNull() }
                ?: base.livenessConfiguration,
            livenessEnvironmentAware = opts["livenessEnvironmentAware"]?.jsonPrimitive?.contentOrNull
                ?.toBoolean() ?: base.livenessEnvironmentAware,
            cameraDelaySeconds = opts["cameraDelaySeconds"]?.jsonPrimitive?.contentOrNull
                ?.toIntOrNull() ?: base.cameraDelaySeconds,
            showSuccessFeedback = opts["showSuccessFeedback"]?.jsonPrimitive?.contentOrNull
                ?.toBoolean() ?: base.showSuccessFeedback,
            deviceToRevoke = base.deviceToRevoke,
            // Auth uses key "presentationStyle"; enroll uses "presentation"
            presentationStyle = opts["presentationStyle"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { PresentationStyle.valueOf(it) }.getOrNull() }
                ?: base.presentationStyle,
            generatingClientState = buildGeneratingClientState(),
            shouldRetrieveAuthenticationFrame = retrieveSelfie,
        )
    }

    private fun submitResult(
        signedJwt: String,
        clientState: String,
        devicePublicSigningKey: String,
        clientError: String,
        clientErrorCode: String,
    ) {
        if (derivedCallback) {
            setValueCallback(SIGNED_JWT_SUFFIX, signedJwt)
            setValueCallback(CLIENT_STATE_SUFFIX, clientState)
            // recognizeId is not returned for authentication
            setValueCallback(DEVICE_PUBLIC_SIGNING_KEY_SUFFIX, devicePublicSigningKey)
            setValueCallback(CLIENT_ERROR_SUFFIX, clientError)
            setValueCallback(CLIENT_ERROR_CODE_SUFFIX, clientErrorCode)
        } else {
            // Auth input order: signedJwt, clientState, recognizeId (empty), devicePublicSigningKey,
            // clientError, clientErrorCode
            input(signedJwt, clientState, "", devicePublicSigningKey, clientError, clientErrorCode)
        }
    }
}
