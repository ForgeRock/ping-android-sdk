/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recaptcha.enterprise

import android.app.Application
import com.google.android.recaptcha.RecaptchaAction
import com.pingidentity.android.ContextProvider
import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.Journey
import com.pingidentity.journey.plugin.JourneyAware
import com.pingidentity.utils.PingDsl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject

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
 *     setProvider(DefaultRecaptchaClientProvider(this))
 * }
 * ```
 */
@PingDsl
class ReCaptchaEnterpriseConfig {
    /** The ReCaptcha Enterprise site key */
    var reCaptchaSiteKey: String = ""

    /** The type of action being performed (LOGIN, SIGNUP, etc.) */
    var recaptchaAction: RecaptchaAction = RecaptchaAction.LOGIN

    /** Timeout for ReCaptcha verification in milliseconds */
    var timeoutInMills: Long = 10000L

    /** Custom provider for ReCaptcha client operations */
    var recaptchaClientProvider: RecaptchaClientProvider? = null

    /**
     * Sets the RecaptchaClientProvider for custom ReCaptcha handling.
     *
     * @param provider The custom RecaptchaClientProvider implementation
     */
    fun setProvider(provider: RecaptchaClientProvider) {
        recaptchaClientProvider = provider
    }
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
 *     setProvider(myCustomProvider)
 * }
 * ```
 */
class ReCaptchaEnterpriseCallback : AbstractCallback(), JourneyAware {
    /**
     * The ReCaptcha site key received from the server configuration.
     * This key is automatically populated during callback initialization.
     */
    var reCaptchaSiteKey: String = ""
        private set

    override lateinit var journey: Journey

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
     * Performs ReCaptcha Enterprise verification with the specified configuration.
     *
     * This method orchestrates the entire verification process:
     * 1. Applies the provided configuration block
     * 2. Creates a ReCaptcha client using the configured provider
     * 3. Executes the specified action with timeout
     * 4. Returns the verification token for server validation
     *
     * @param block DSL configuration block for customizing verification parameters
     * @return Result containing the verification token as JsonObject, or failure with exception
     * @throws IllegalStateException if RecaptchaClientProvider is not configured
     * @throws IllegalStateException if token verification fails
     */
    suspend fun verify(block: ReCaptchaEnterpriseConfig.() -> Unit = {}): Result<JsonObject> {
        val config = ReCaptchaEnterpriseConfig()
        config.reCaptchaSiteKey = reCaptchaSiteKey
        config.apply(block)

        var tokenResultJson: JsonObject
        val provider = config.recaptchaClientProvider ?: throw IllegalStateException("RecaptchaClientProvider is not set")
        return runCatching {
            val client = provider.fetchClient(
                ContextProvider.context as Application,
                reCaptchaSiteKey
            )

            val tokenResult = provider.execute(
                client,
                config.recaptchaAction,
                config.timeoutInMills,
            ) ?: throw IllegalStateException("Invalid captcha token")


            tokenResultJson = super.input(tokenResult)
            tokenResultJson
        }
    }
}