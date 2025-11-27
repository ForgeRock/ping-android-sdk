/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recaptcha.enterprise

import android.app.Application
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.pingidentity.android.ContextProvider
import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.logger.Logger
import com.pingidentity.logger.WARN
import com.pingidentity.recaptcha.enterprise.ReCaptchaEnterpriseCallback.Companion.UNKNOWN_ERROR
import com.pingidentity.utils.PingDsl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Configuration for ReCaptcha Enterprise verification.
 *
 * DSL-based configuration used with [ReCaptchaEnterpriseCallback.verify].
 *
 * @property recaptchaAction The verification action type. Default: [RecaptchaAction.LOGIN]
 * @property timeoutInMills Verification timeout in milliseconds. Default: 10000L (10 seconds)
 * @property customPayload Optional JSON metadata for risk assessment. Default: null
 * @property logger Logger for tracking verification. Default: [Logger.Companion.WARN]
 */
@PingDsl
class ReCaptchaEnterpriseConfig {

    /**
     * Verification action type (LOGIN, SIGNUP, or custom).
     */
    var recaptchaAction: RecaptchaAction = RecaptchaAction.LOGIN

    /**
     * Verification timeout in milliseconds.
     */
    var timeoutInMills: Long = 10000L

    /**
     * Optional custom metadata to send with verification.
     */
    var customPayload: JsonObject? = null

    /**
     * Generates error message from exception during verification.
     */
    var customError: (throwable: Throwable) -> String = { exception -> exception.message ?: UNKNOWN_ERROR }

    /**
     * Logger for verification process.
     */
    var logger: Logger = Logger.WARN
}

/**
 * Journey callback for Google ReCaptcha Enterprise verification.
 *
 * Integrates ReCaptcha Enterprise into Journey authentication flows for bot protection.
 * Automatically instantiated by the server when verification is required.
 *
 * @property reCaptchaSiteKey The site key from server configuration (read-only)
 */
class ReCaptchaEnterpriseCallback : AbstractCallback() {
    /**
     * ReCaptcha Enterprise site key from server.
     */
    var reCaptchaSiteKey: String = ""
        private set

    /**
     * Initializes callback from server configuration.
     *
     * Called automatically by Journey framework during callback initialization.
     */
    override fun init(name: String, value: JsonElement) {
        if ("recaptchaSiteKey" == name) {
            this.reCaptchaSiteKey = value.jsonPrimitive.content
            println("VIBS - Recaptcha Site Key: $reCaptchaSiteKey")
        }
    }

    /**
     * Performs ReCaptcha Enterprise verification.
     *
     * Executes verification flow: client setup, action execution, token validation,
     * and submission to Journey server.
     *
     * @param block Optional DSL configuration. Uses defaults if not provided.
     * @return [Result] containing verification token on success or exception on failure
     */
    suspend fun verify(block: ReCaptchaEnterpriseConfig.() -> Unit): Result<String> {
        val config = ReCaptchaEnterpriseConfig()
        config.apply(block)

        return try {
            withContext(context = Dispatchers.IO) {
                ensureActive() // Check cancellation before starting
                Recaptcha.fetchClient(
                    application = ContextProvider.context as Application,
                    siteKey = reCaptchaSiteKey,
                ).execute(
                    recaptchaAction = config.recaptchaAction,
                    timeout = config.timeoutInMills,
                ).onSuccess { token ->
                    val payload = config.customPayload ?: JsonObject(emptyMap())
                    super.input(token, config.recaptchaAction.action, "", payload.toString())
                }.onFailure { exception ->
                    config.logger.e(
                        "An error occurred during reCAPTCHA setup or execution.",
                        exception
                    )
                    super.input("", config.recaptchaAction.action, config.customError(exception), "")
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation // Re-throw to propagate cancellation
        } catch (exception: Exception) {
            config.logger.e(
                "An unexpected error occurred during reCAPTCHA setup or execution.",
                exception
            )
            super.input("", config.recaptchaAction.action, config.customError(exception), "")
            Result.failure(exception)
        }
    }

    companion object {
        /**
         * Error code for verification failures (network, config, service, or client errors).
         */
        internal const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
    }
}