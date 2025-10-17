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
import com.pingidentity.utils.PingDsl
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Configuration class for ReCaptcha Enterprise verification settings.
 *
 * This DSL configuration class allows customization of ReCaptcha Enterprise
 * verification parameters including site key, action type, timeout, and
 * the client provider implementation.
 *
 * Example usage:
 * ```kotlin
 * callback.verify {
 *     recaptchaAction = RecaptchaAction.LOGIN
 *     timeoutInMills = 15000L
 *     customPayload = buildJsonObject {
 *         put("metadata", "value")
 *     }
 * }
 * ```
 *
 * @property recaptchaAction The type of action being performed (LOGIN, SIGNUP, etc.). Default is [RecaptchaAction.LOGIN]
 * @property timeoutInMills Timeout for ReCaptcha verification in milliseconds. Default is 10000L
 * @property customPayload Optional custom JSON payload to be sent with the verification request
 * @property logger Logger instance for logging verification process. Default is [Logger.Companion.WARN]
 */
@PingDsl
class ReCaptchaEnterpriseConfig {

    /** The type of action being performed (LOGIN, SIGNUP, etc.) */
    var recaptchaAction: RecaptchaAction = RecaptchaAction.LOGIN

    /** Timeout for ReCaptcha verification in milliseconds */
    var timeoutInMills: Long = 10000L

    /** Custom payload to be sent with the verification request */
    var customPayload: JsonObject? = null

    /** Logger instance for logging verification process */
    var logger: Logger = Logger.WARN
}

/**
 * Journey callback for handling ReCaptcha Enterprise verification challenges.
 *
 * This callback integrates Google's ReCaptcha Enterprise service into Journey
 * authentication flows. It handles the verification process by:
 * - Receiving site key configuration from the server
 * - Creating ReCaptcha clients using the configured provider
 * - Executing verification actions with proper timeout handling
 * - Returning verification tokens for server validation
 *
 * The callback supports customizable actions, timeouts, and client providers
 * to accommodate different verification requirements and testing scenarios.
 *
 * Example usage:
 * ```kotlin
 * val callback = journey.next() as ReCaptchaEnterpriseCallback
 * val result = callback.verify {
 *     recaptchaAction = RecaptchaAction.LOGIN
 *     timeoutInMills = 15000L
 * }
 * ```
 *
 * @property reCaptchaSiteKey The ReCaptcha site key received from the server configuration
 * @property customPayload Optional custom JSON payload to be sent with the verification request
 * @property logger Logger instance for logging verification process
 */
class ReCaptchaEnterpriseCallback : AbstractCallback() {
    /**
     * The ReCaptcha site key received from the server configuration.
     * This key is automatically populated during callback initialization.
     */
    var reCaptchaSiteKey: String = ""
        private set

    var customPayload: JsonObject? = null
        private set

    var logger: Logger = Logger.WARN
        private set


    /**
     * Initializes callback properties from server configuration.
     *
     * @param name The property name from server configuration
     * @param value The property value as JsonElement
     */
    override fun init(name: String, value: JsonElement) {
        if ("recaptchaSiteKey" == name) {
            this.reCaptchaSiteKey = value.jsonPrimitive.content
        }
    }

    /**
     * Internal method for testing purposes to initialize properties.
     *
     * @param name The property name
     * @param value The property value as JsonElement
     */
    internal fun initForTest(name: String, value: JsonElement) {
        init(name, value)
    }

    /**
     * Verifies the ReCaptcha challenge using the provided configuration.
     *
     * This method performs the following steps:
     * 1. Applies the configuration block to customize verification settings
     * 2. Fetches a ReCaptcha client using the site key from the server
     * 3. Executes the verification action with the configured timeout
     * 4. Validates the returned token
     * 5. Submits the token (and optional payload) back to the server
     *
     * The method handles errors gracefully and provides detailed error messages
     * through logging. All exceptions are caught and wrapped in a Result.failure.
     *
     * Error scenarios:
     * - **INVALID_CAPTCHA_TOKEN**: Returned when verification fails or produces an empty token
     * - **UNKNOWN_ERROR**: Returned for unexpected errors during client setup or execution
     *
     * Example usage:
     * ```kotlin
     * // Basic verification
     * val result = callback.verify()
     *
     * // Customized verification
     * val result = callback.verify {
     *     recaptchaAction = RecaptchaAction.SIGNUP
     *     timeoutInMills = 15000L
     *     customPayload = buildJsonObject {
     *         put("userId", "12345")
     *     }
     * }
     *
     * result.onSuccess { token ->
     *     println("Verification successful: $token")
     * }.onFailure { error ->
     *     println("Verification failed: ${error.message}")
     * }
     * ```
     *
     * @param block Optional DSL configuration block to customize verification settings.
     *              See [ReCaptchaEnterpriseConfig] for available options.
     * @return [Result]<[String]> containing the verification token on success,
     *         or an exception with error details on failure.
     */
    suspend fun verify(block: ReCaptchaEnterpriseConfig.() -> Unit = {}): Result<String> {
        val config = ReCaptchaEnterpriseConfig()
        config.customPayload = customPayload
        config.logger = logger
        config.apply(block)

        // Use runCatching to handle exceptions from the entire block gracefully.
        return runCatching {
            val client = Recaptcha.fetchClient(
                application = ContextProvider.context as Application,
                siteKey = reCaptchaSiteKey,
            )
            // The execute function returns a Result, so we can chain its handling.
            client.execute(
                recaptchaAction = config.recaptchaAction,
                timeout = config.timeoutInMills,
            )
        }.fold(
            // onSuccess block: This is executed if the 'runCatching' and 'execute' were successful.
            onSuccess = { recaptchaResult ->
                val token = recaptchaResult.getOrNull()

                // 1. Check for a null or empty token, which indicates a failure.
                if (token.isNullOrEmpty()) {
                    val error = recaptchaResult.exceptionOrNull()
                    logger.w(
                        "reCAPTCHA execution failed or returned empty token.",
                        error
                    )
                    Result.failure(Exception(INVALID_CAPTCHA_TOKEN, error))
                } else {
                    // 2. On success, call the super method with or without the payload.
                    config.customPayload?.let { payload ->
                        super.input(token, payload)
                    } ?: super.input(token)

                    // Return the original successful result.
                    recaptchaResult
                }
            },
            // onFailure block: This is executed if 'fetchClient' or another part of 'runCatching' throws an exception.
            onFailure = { exception ->
                logger.e(
                    "An unexpected error occurred during reCAPTCHA setup or execution.",
                    exception
                )
                Result.failure(Exception(UNKNOWN_ERROR, exception))
            }
        )
    }

    companion object {
        /** Error code indicating the ReCaptcha token is invalid or empty */
        private const val INVALID_CAPTCHA_TOKEN = "INVALID_CAPTCHA_TOKEN"

        /** Error code indicating an unexpected error occurred during verification */
        private const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
    }
}