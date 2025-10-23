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
 * This DSL-based configuration class provides a type-safe way to customize ReCaptcha Enterprise
 * verification parameters. All configuration is done through the lambda block passed to
 * [ReCaptchaEnterpriseCallback.verify], allowing for flexible and readable setup.
 *
 * ## Configuration Options
 *
 * - **Action Type**: Specify the user action being verified (LOGIN, SIGNUP, or custom actions)
 * - **Timeout**: Configure how long to wait for verification (default: 10 seconds)
 * - **Custom Payload**: Add metadata for risk assessment and analytics
 * - **Logging**: Control log verbosity for debugging and monitoring
 *
 * ## Usage Examples
 *
 * Basic verification with default settings:
 * ```kotlin
 * callback.verify {
 *     // Uses defaults: LOGIN action, 10s timeout, WARN logging
 * }
 * ```
 *
 * Customized verification:
 * ```kotlin
 * callback.verify {
 *     recaptchaAction = RecaptchaAction.SIGNUP
 *     timeoutInMills = 15000L
 *     logger = Logger.DEBUG
 * }
 * ```
 *
 * With custom action and payload:
 * ```kotlin
 * callback.verify {
 *     recaptchaAction = RecaptchaAction.custom("PASSWORD_RESET")
 *     timeoutInMills = 20000L
 *     customPayload = buildJsonObject {
 *         put("userId", "12345")
 *         put("attemptCount", 3)
 *     }
 * }
 * ```
 *
 * @property recaptchaAction The type of action being verified. Use predefined actions like
 *           [RecaptchaAction.LOGIN] or [RecaptchaAction.SIGNUP], or create custom actions with
 *           [RecaptchaAction.custom]. Default: [RecaptchaAction.LOGIN]
 * @property timeoutInMills Maximum time to wait for verification in milliseconds. Increase for
 *           slower networks or high-latency scenarios. Default: `10000L` (10 seconds)
 * @property customPayload Optional JSON payload containing additional metadata to send with the
 *           verification request. Useful for risk assessment, analytics, or custom business logic.
 *           Default: `null`
 * @property logger Logger instance used for tracking verification progress and debugging issues.
 *           Set to [Logger.DEBUG] for detailed logs or [Logger.ERROR] for production.
 *           Default: [Logger.WARN]
 *
 * @see ReCaptchaEnterpriseCallback.verify
 * @see RecaptchaAction
 */
@PingDsl
class ReCaptchaEnterpriseConfig {

    /**
     * The type of action being performed for ReCaptcha verification.
     *
     * Use one of the predefined actions:
     * - [RecaptchaAction.LOGIN] - User login verification
     * - [RecaptchaAction.SIGNUP] - New user registration
     *
     * Or create a custom action:
     * - [RecaptchaAction.custom]("YOUR_ACTION") - For custom verification scenarios
     *
     * Default: [RecaptchaAction.LOGIN]
     */
    var recaptchaAction: RecaptchaAction = RecaptchaAction.LOGIN

    /**
     * Timeout duration for ReCaptcha verification in milliseconds.
     *
     * This controls how long the verification process will wait before timing out.
     * Consider increasing this value for:
     * - Slow network connections
     * - High-latency scenarios
     * - Complex verification requirements
     *
     * Default: `10000L` (10 seconds)
     *
     * Example:
     * ```kotlin
     * timeoutInMills = 20000L // 20 seconds for slower networks
     * ```
     */
    var timeoutInMills: Long = 10000L

    /**
     * Optional custom JSON payload to send with the verification request.
     *
     * Use this to include additional context or metadata that can help with:
     * - Risk assessment and scoring
     * - Analytics and monitoring
     * - Custom business logic on the server
     *
     * The payload should be a valid [JsonObject] and will be submitted alongside
     * the verification token.
     *
     * Default: `null` (no custom payload)
     *
     * Example:
     * ```kotlin
     * customPayload = buildJsonObject {
     *     put("userId", currentUser.id)
     *     put("deviceId", deviceInfo.id)
     *     put("previousAttempts", loginAttemptCount)
     * }
     * ```
     */
    var customPayload: JsonObject? = null

    /**
     * Logger instance for tracking verification process and debugging.
     *
     * Controls the verbosity of logging output during verification:
     * - [Logger.Companion.WARN] - Warnings about potential issues (default)
     *
     *
     * Example:
     * ```kotlin
     * logger = if (BuildConfig.DEBUG) Logger.DEBUG else Logger.WARN
     * ```
     */
    var logger: Logger = Logger.WARN
}

/**
 * Journey callback for handling Google ReCaptcha Enterprise verification challenges.
 *
 * This callback seamlessly integrates Google's ReCaptcha Enterprise service into Ping Identity's
 * Journey authentication flows, providing advanced bot protection and abuse detection. The callback
 * is part of the Journey plugin architecture and is automatically instantiated when the server
 * includes a ReCaptcha Enterprise verification step in the authentication flow.
 *
 * ## Overview
 *
 * The callback manages the complete ReCaptcha verification lifecycle:
 * 1. Receives the ReCaptcha site key from the server during callback initialization
 * 2. Creates a ReCaptcha client using Google's ReCaptcha SDK
 * 3. Executes the verification action with configurable timeout
 * 4. Validates the returned verification token
 * 5. Submits the token (and optional metadata) back to the server via Journey
 *
 * ## Architecture
 *
 * - **Asynchronous**: Uses Kotlin coroutines (`suspend` function) for non-blocking operations
 * - **Type-Safe**: DSL-based configuration with compile-time safety
 * - **Extensible**: Supports custom actions and metadata payloads
 * - **Robust**: Comprehensive error handling with detailed logging
 *
 * ## Configuration
 *
 * All configuration is done through the [verify] method's DSL block. The callback itself only
 * receives the site key from the server - all other settings are configured at verification time:
 *
 * - **Site Key**: Automatically set by the server (read-only)
 * - **Action Type**: Configurable via [ReCaptchaEnterpriseConfig.recaptchaAction]
 * - **Timeout**: Configurable via [ReCaptchaEnterpriseConfig.timeoutInMills]
 * - **Custom Payload**: Configurable via [ReCaptchaEnterpriseConfig.customPayload]
 * - **Logging**: Configurable via [ReCaptchaEnterpriseConfig.logger]
 *
 * ## Usage Examples
 *
 * ### Basic Verification
 * ```kotlin
 * val node = journey.next() as ContinueNode
 * val callback = node.callbacks
 *     .filterIsInstance<ReCaptchaEnterpriseCallback>()
 *     .first()
 *
 * // Verify with defaults (LOGIN action, 10s timeout)
 * val result = callback.verify()
 *
 * result.onSuccess { token ->
 *     // Continue with Journey flow
 *     node.next()
 * }
 * ```
 *
 * ### Custom Configuration
 * ```kotlin
 * callback.verify {
 *     recaptchaAction = RecaptchaAction.SIGNUP
 *     timeoutInMills = 15000L
 *     logger = Logger.DEBUG
 * }.onSuccess { token ->
 *     println("Verification successful: $token")
 * }.onFailure { error ->
 *     println("Verification failed: ${error.message}")
 * }
 * ```
 *
 * ### With Custom Payload
 * ```kotlin
 * callback.verify {
 *     recaptchaAction = RecaptchaAction.custom("PASSWORD_RESET")
 *     customPayload = buildJsonObject {
 *         put("userId", user.id)
 *         put("attemptCount", resetAttempts)
 *         put("deviceId", deviceInfo.id)
 *     }
 * }
 * ```
 *
 * ## Error Handling
 *
 * The [verify] method returns a [Result] type, providing type-safe error handling:
 *
 * - **Success**: Contains the verification token string
 * - **Failure**: Contains an exception with error code [UNKNOWN_ERROR] and detailed cause
 *
 * All errors are wrapped with the [UNKNOWN_ERROR] code, which covers:
 * - Network connectivity issues
 * - ReCaptcha service unavailability
 * - Invalid configuration
 * - Client initialization failures
 * - Verification timeouts
 *
 * Example error handling:
 * ```kotlin
 * callback.verify().fold(
 *     onSuccess = { token -> handleSuccess(token) },
 *     onFailure = { error ->
 *         when {
 *             error.message?.contains("UNKNOWN_ERROR") == true -> {
 *                 logger.e("Verification failed", error.cause)
 *                 showUserError("Please try again")
 *             }
 *         }
 *     }
 * )
 * ```
 *
 * ## Threading
 *
 * The [verify] method is a `suspend` function and must be called from a coroutine context:
 *
 * ```kotlin
 * // In a ViewModel
 * viewModelScope.launch {
 *     callback.verify()
 * }
 *
 * // In Compose
 * LaunchedEffect(callback) {
 *     callback.verify()
 * }
 * ```
 *
 * ## Testing
 *
 * For unit testing, mock the callback to avoid native ReCaptcha SDK dependencies:
 *
 * ```kotlin
 * val mockCallback = mockk<ReCaptchaEnterpriseCallback>()
 * coEvery { mockCallback.verify(any()) } returns Result.success("test-token")
 * ```
 *
 * @property reCaptchaSiteKey The ReCaptcha Enterprise site key received from the server.
 *           This is automatically populated during callback initialization and cannot be modified.
 * @property logger Logger instance used for tracking the verification process. This provides
 *           the default logger that can be overridden in the [verify] configuration block.
 *           Default: [Logger.Companion.WARN]
 *
 * @see ReCaptchaEnterpriseConfig
 * @see verify
 * @see AbstractCallback
 */
class ReCaptchaEnterpriseCallback : AbstractCallback() {
    /**
     * The ReCaptcha Enterprise site key received from the server configuration.
     *
     * This key is automatically populated during callback initialization via the [init] method
     * when the server sends the callback configuration. It uniquely identifies your ReCaptcha
     * Enterprise configuration in Google Cloud Console and is required for all verification requests.
     *
     * **Note**: This property is read-only and cannot be modified after initialization.
     */
    var reCaptchaSiteKey: String = ""
        private set

    /**
     * Default logger instance for the callback.
     *
     * This logger is used as the default for verification operations and can be overridden
     * on a per-verification basis through the [ReCaptchaEnterpriseConfig.logger] property.
     *
     * Default: [Logger.Companion.WARN]
     */
    var logger: Logger = Logger.WARN
        private set


    /**
     * Initializes callback properties from server configuration.
     *
     * This method is called automatically by the Journey framework during callback initialization
     * when the server sends the callback configuration. It extracts and stores the ReCaptcha
     * Enterprise site key from the server response.
     *
     * The method only processes the `recaptchaSiteKey` property - all other property names are
     * ignored. This ensures that only the expected configuration is applied.
     *
     * **Note**: This method is part of the internal callback lifecycle and should not be called
     * directly by application code. The Journey framework handles callback initialization
     * automatically.
     *
     * @param name The property name from the server configuration. Only "recaptchaSiteKey" is
     *             processed; all other names are ignored.
     * @param value The property value as a [JsonElement]. For "recaptchaSiteKey", this should
     *              be a JSON primitive containing the site key string.
     *
     * @see AbstractCallback.init
     */
    override fun init(name: String, value: JsonElement) {
        if ("recaptchaSiteKey" == name) {
            this.reCaptchaSiteKey = value.jsonPrimitive.content
        }
    }

    /**
     * Internal method for testing purposes to initialize callback properties.
     *
     * This method provides a way to initialize the callback during unit testing without going
     * through the full Journey framework initialization. It simply delegates to the [init] method.
     *
     * **Important**: This method is for internal testing use only and should not be used in
     * production code. It's marked as `internal` to restrict access to the module only.
     *
     * ## Testing Example
     * ```kotlin
     * @Test
     * fun testVerification() = runTest {
     *     val callback = ReCaptchaEnterpriseCallback()
     *     callback.initForTest("recaptchaSiteKey", JsonPrimitive("test-site-key"))
     *
     *     // Now the callback is ready for testing
     *     assertEquals("test-site-key", callback.reCaptchaSiteKey)
     * }
     * ```
     *
     * @param name The property name to initialize (e.g., "recaptchaSiteKey")
     * @param value The property value as a [JsonElement]
     */
    internal fun initForTest(name: String, value: JsonElement) {
        init(name, value)
    }

    /**
     * Performs ReCaptcha Enterprise verification with optional configuration.
     *
     * This suspend function executes the complete ReCaptcha verification flow, from client
     * initialization to token submission. It provides a type-safe, coroutine-based API for
     * seamless integration into modern Android applications.
     *
     * ## Verification Flow
     *
     * 1. **Configuration**: Applies the DSL configuration block to customize verification settings
     * 2. **Client Setup**: Fetches a ReCaptcha client from Google using the site key
     * 3. **Execution**: Executes the verification action with the configured timeout
     * 4. **Validation**: Validates the returned verification token
     * 5. **Submission**: Submits the token (and optional payload) back to the Journey server
     *
     * ## Configuration Options
     *
     * All configuration is optional and done through the DSL block. If no configuration is
     * provided, sensible defaults are used:
     *
     * - **Action**: [RecaptchaAction.LOGIN] (default)
     * - **Timeout**: 10 seconds
     * - **Payload**: None
     * - **Logger**: [Logger.Companion.WARN]
     *
     * ## Error Handling
     *
     * The method uses Kotlin's [Result] type for robust error handling. All errors are wrapped
     * with the [UNKNOWN_ERROR] error code and include the original cause for debugging.
     *
     * ### Error Scenarios
     *
     * - **Network Errors**: Connection issues, timeouts, or DNS failures
     * - **Configuration Errors**: Invalid site key or misconfigured ReCaptcha Enterprise
     * - **Service Errors**: Google ReCaptcha service unavailability
     * - **Client Errors**: Failed to initialize ReCaptcha client
     * - **Validation Errors**: Empty or invalid verification token
     *
     * All errors include detailed logging at the ERROR level with the original exception cause
     * for troubleshooting.
     *
     * ## Usage Examples
     *
     * ### Basic Verification
     * ```kotlin
     * // Use defaults: LOGIN action, 10s timeout, no custom payload
     * val result = callback.verify()
     *
     * result.onSuccess { token ->
     *     println("Verification token: $token")
     *     // Continue with Journey flow
     * }
     * ```
     *
     * ### Custom Action and Timeout
     * ```kotlin
     * val result = callback.verify {
     *     recaptchaAction = RecaptchaAction.SIGNUP
     *     timeoutInMills = 15000L  // 15 seconds
     * }
     * ```
     *
     * ### With Custom Payload for Risk Assessment
     * ```kotlin
     * callback.verify {
     *     recaptchaAction = RecaptchaAction.LOGIN
     *     customPayload = buildJsonObject {
     *         put("userId", currentUser.id)
     *         put("loginAttempts", attemptCount)
     *         put("deviceFingerprint", deviceInfo.fingerprint)
     *     }
     * }.fold(
     *     onSuccess = { token ->
     *         // Proceed with authentication
     *         continueJourneyFlow()
     *     },
     *     onFailure = { error ->
     *         // Handle verification failure
     *         showErrorMessage("Verification failed: ${error.cause?.message}")
     *         logger.e("ReCaptcha verification failed", error.cause)
     *     }
     * )
     * ```
     *
     * ### With Debug Logging
     * ```kotlin
     * callback.verify {
     *     logger = if (BuildConfig.DEBUG) Logger.DEBUG else Logger.WARN
     *     recaptchaAction = RecaptchaAction.custom("PASSWORD_RESET")
     * }
     * ```
     *
     * ## Threading Requirements
     *
     * This is a `suspend` function and must be called from a coroutine context:
     *
     * ```kotlin
     * // In a ViewModel
     * viewModelScope.launch {
     *     val result = callback.verify()
     * }
     *
     * // In a Composable
     * LaunchedEffect(callback) {
     *     callback.verify()
     * }
     *
     * // In a Fragment/Activity
     * lifecycleScope.launch {
     *     callback.verify()
     * }
     * ```
     *
     * @param block Optional DSL configuration block to customize verification settings.
     *              If not provided, default settings are used. See [ReCaptchaEnterpriseConfig]
     *              for all available configuration options.
     *
     * @return [Result]<[String]> A [Result] containing either:
     *         - **Success**: The verification token as a [String]
     *         - **Failure**: An [Exception] with error code [UNKNOWN_ERROR] and the original
     *           cause attached for debugging
     *
     * @see ReCaptchaEnterpriseConfig
     * @see RecaptchaAction
     * @see UNKNOWN_ERROR
     */
    suspend fun verify(block: ReCaptchaEnterpriseConfig.() -> Unit = {}): Result<String> {
        val config = ReCaptchaEnterpriseConfig()
        config.logger = logger
        config.apply(block)

        return try {
            Recaptcha.fetchClient(
                application = ContextProvider.context as Application,
                siteKey = reCaptchaSiteKey,
            ).execute(
                recaptchaAction = config.recaptchaAction,
                timeout = config.timeoutInMills,
            ).onSuccess { token ->
                config.customPayload?.let { payload ->
                    super.input(token, payload)
                } ?: super.input(token)

                // Return the original successful result.
                Result.success(token)

            }.onFailure { exception ->
                logger.e(
                    "An error occurred during reCAPTCHA setup or execution.",
                    exception
                )
                return Result.failure(Exception(UNKNOWN_ERROR, exception))
            }
        } catch (exception: Exception) {
            logger.e(
                "An unexpected error occurred during reCAPTCHA setup or execution.",
                exception
            )
            Result.failure(Exception(UNKNOWN_ERROR, exception))
        }
    }

    companion object {
        /**
         * Error code indicating that an error occurred during ReCaptcha verification.
         *
         * This error code is used for all verification failures, including but not limited to:
         *
         * - **Network Errors**: Connection timeouts, DNS failures, no internet connectivity
         * - **Configuration Errors**: Invalid or missing site key, misconfigured ReCaptcha Enterprise
         * - **Service Errors**: Google ReCaptcha service unavailable or unreachable
         * - **Client Errors**: Failed to initialize or fetch the ReCaptcha client
         * - **Verification Errors**: Verification timeout, invalid token, or verification failure
         *
         * ## Usage
         *
         * When handling verification failures, check for this error code in the exception message:
         *
         * ```kotlin
         * callback.verify().onFailure { error ->
         *     when {
         *         error.message?.contains(UNKNOWN_ERROR) == true -> {
         *             // Handle verification failure
         *             val cause = error.cause
         *             logger.e("Verification failed", cause)
         *             showUserMessage("Unable to verify. Please try again.")
         *         }
         *     }
         * }
         * ```
         *
         * ## Debugging
         *
         * The original exception cause is always preserved and can be accessed via
         * [Throwable.cause] for detailed debugging information. Enable debug logging
         * to see the full error details:
         *
         * ```kotlin
         * callback.verify {
         *     logger = Logger.DEBUG
         * }
         * ```
         *
         * @see verify
         */
        internal const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
    }
}