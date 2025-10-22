/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.device.binding.journey

import com.nimbusds.jwt.JWTClaimNames
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.CryptoKey
import com.pingidentity.device.binding.CryptoKeyAware
import com.pingidentity.device.binding.Prompt
import com.pingidentity.device.binding.authenticator.CHALLENGE
import com.pingidentity.device.binding.authenticator.UserKeySigningParameters
import com.pingidentity.device.binding.authenticator.exception.DeviceNotRegisteredException
import com.pingidentity.device.binding.authenticator.exception.InvalidClaimException
import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.Journey
import com.pingidentity.journey.plugin.JourneyAware
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Journey callback for handling device signing verification operations in authentication flows.
 *
 * This callback enables applications to prove device possession by signing a server-provided
 * challenge using previously registered cryptographic keys. It's used for step-up authentication
 * scenarios where users need to prove they are using a trusted, bound device.
 *
 * The callback handles the complete signing verification lifecycle:
 * - User key lookup and selection when multiple keys exist
 * - User authentication based on the key's authentication type
 * - Challenge signing with the authenticated private key
 * - JWT generation with custom claims support
 * - Error handling and timeout management
 *
 * Unlike DeviceBindingCallback which creates new keys, this callback uses existing keys
 * to prove device possession through cryptographic signatures.
 *
 * @see AbstractCallback
 * @see JourneyAware
 * @see DeviceBindingConfig
 * @see DeviceBindingCallback
 */
open class DeviceSigningVerifierCallback : AbstractCallback(), JourneyAware {

    /**
     * The Journey instance providing access to configuration and logging.
     * Injected by the Journey framework during callback initialization.
     */
    override lateinit var journey: Journey

    /**
     * The unique identifier for the user whose device key should be used for signing.
     * If null, the system will either use the single available key or prompt the user
     * to select from multiple available keys. This value is set from the server
     * callback configuration.
     */
    var userId: String? = null
        private set

    /**
     * The cryptographic challenge provided by the server that must be signed
     * to prove possession of the private key. This challenge is included in
     * the JWT claims and signed with the user's device key.
     */
    lateinit var challenge: String
        private set

    /**
     * The title to display in authentication prompts (e.g., biometric or PIN dialogs).
     * Provides context to users about what they are authenticating for during signing.
     */
    lateinit var title: String
        private set

    /**
     * The subtitle to display in authentication prompts.
     * Provides additional context about the signing verification request.
     */
    lateinit var subtitle: String
        private set

    /**
     * The description to display in authentication prompts.
     * Explains why authentication is required and what signing operation will occur.
     */
    lateinit var description: String
        private set

    /**
     * The timeout in seconds for the entire signing verification operation.
     * If the operation takes longer than this time, it will be cancelled.
     * Default value is 30 seconds.
     */
    var timeout: Int = 30
        private set

    // Input fields that will be sent back to the server

    /**
     * The signed JWT containing the challenge signature and verification claims.
     * Generated during the sign operation and sent to the server for verification.
     */
    private var jws = ""

    /**
     * Error message to be sent to the server if the signing verification operation fails.
     * Can be overridden to provide custom error messages.
     */
    var clientError = ""

    /**
     * Creates the payload to be sent back to the server after signing verification completion.
     *
     * This method constructs the response payload containing the signed JWT and any error
     * messages. The server uses this information to verify the device signature and
     * complete the authentication step-up process.
     *
     * @return A map containing the input values for server processing
     */
    override fun payload() = input(jws, clientError)

    /**
     * Initializes callback properties from the server-provided JSON configuration.
     *
     * This method is called by the Journey framework to populate the callback
     * with values received from the authentication server. Each property corresponds
     * to a specific aspect of the signing verification configuration.
     *
     * Special handling:
     * - userId: Empty strings are converted to null to indicate no specific user
     * - timeout: Parsed as integer for operation timeout configuration
     *
     * @param name The name of the property to initialize
     * @param value The JSON value for the property
     */
    override fun init(name: String, value: JsonElement) {
        when (name) {
            "userId" -> userId = value.jsonPrimitive.content.takeIf { it.isNotEmpty() }
            "challenge" -> challenge = value.jsonPrimitive.content
            "title" -> title = value.jsonPrimitive.content
            "subtitle" -> subtitle = value.jsonPrimitive.content
            "description" -> description = value.jsonPrimitive.content
            "timeout" -> timeout = value.jsonPrimitive.int
        }
    }

    /**
     * Performs the complete device signing verification operation.
     *
     * This is the main method that orchestrates the entire signing verification process:
     * 1. Validates custom claims to ensure no reserved claim names are used
     * 2. Looks up the appropriate user key (by userId or through selection)
     * 3. Configures the device authenticator based on the key's authentication type
     * 4. Authenticates the user to access the private key
     * 5. Signs the challenge using the authenticated key to create a JWT
     * 6. Handles cleanup and error reporting on failures
     *
     * User key selection logic:
     * - If userId is specified: Finds the exact key for that user
     * - If no userId: Uses single available key or prompts user to select from multiple
     * - If no keys exist: Throws DeviceNotRegisteredException
     *
     * The operation is performed within a timeout to prevent indefinite blocking.
     * The signed JWT includes the challenge and any custom claims for server verification.
     *
     * @param config Optional configuration block to customize the signing verification behavior
     * @return A Result containing the signed JWT on success, or an error on failure
     * @throws DeviceNotRegisteredException if no user keys are found
     * @throws InvalidClaimException if custom claims contain reserved names
     * @throws kotlinx.coroutines.TimeoutCancellationException if the operation exceeds the timeout
     */
    suspend fun sign(config: DeviceBindingConfig.() -> Unit = {}): Result<String> = runCatching {
        val logger = journey.config.logger
        logger.d("DeviceSigningVerifierCallback: Starting sign process for userId=$userId")

        val context = ContextProvider.context
        val deviceBindingConfig = DeviceBindingConfig().apply { this.logger = logger }.apply(config)
        val claims = mutableMapOf<String, Any>().apply(deviceBindingConfig.claims)
        validate(claims)

        val storage = deviceBindingConfig.keyStorage()
        logger.d("DeviceSigningVerifierCallback: Looking up user key...")

        val userKey = userId?.let {
            logger.d("DeviceSigningVerifierCallback: Finding user key by userId=$it")
            storage.findByUserId(it)
        } ?: storage.findAll().let { keys ->
            logger.d("DeviceSigningVerifierCallback: Found ${keys.size} registered keys")
            when {
                keys.isEmpty() -> {
                    logger.w("DeviceSigningVerifierCallback: No registered users found")
                    throw DeviceNotRegisteredException("No user is registered")
                }

                keys.size == 1 -> {
                    keys[0]
                }

                else -> {
                    logger.d("DeviceSigningVerifierCallback: Multiple keys found, prompting user selection")
                    deviceBindingConfig.userKeySelector(keys)
                }
            }
        }

        logger.i("DeviceSigningVerifierCallback: Using user key for userId=${userKey.userId}, authType=${userKey.authType}")

        val deviceAuthenticator = deviceBindingConfig.authenticator(
            userKey.authType,
            Prompt(title, subtitle, description)
        ).also {
            if (it is CryptoKeyAware) it.cryptoKey = CryptoKey(userKey.userId)
        }

        withTimeout(timeout.toDuration(DurationUnit.SECONDS)) {
            logger.d("DeviceSigningVerifierCallback: Starting authentication for signing")
            deviceAuthenticator.authenticate().onSuccess {
                jws = deviceAuthenticator.sign(
                    UserKeySigningParameters(
                        context = context,
                        algorithm = deviceBindingConfig.signingAlgorithm,
                        userKey = userKey,
                        privateKey = it.first,
                        signature = it.second?.signature,
                        challenge = challenge,
                        issueTime = deviceBindingConfig.issueTime(),
                        notBeforeTime = deviceBindingConfig.notBeforeTime(),
                        expiration = deviceBindingConfig.expirationTime(timeout),
                        customClaims = claims
                    )
                )
                logger.i("DeviceSigningVerifierCallback: Successfully signed challenge with kid=${userKey.kid}")
            }.onFailure {
                logger.w("DeviceSigningVerifierCallback Signing Error", it)
                throw it
            }
        }
        jws
    }.onFailure {
        // Caused by: android.security.keystore.KeyPermanentlyInvalidatedException: Key permanently invalidated
        // Happens when the secure lock screen is disabled or new fingerprint is enrolled when using BiometricOnly with
        // setInvalidatedByBiometricEnrollment(true)
        journey.config.logger.w("DeviceSigningVerifierCallback Error", it)
        clientError = toClientError(it)
    }

    /**
     * Validates custom claims to ensure they don't conflict with reserved JWT claim names.
     *
     * This method checks that custom claims provided by the application don't use
     * any reserved JWT claim names that are automatically managed by the SDK.
     * Reserved names include standard JWT claims (sub, exp, iat, nbf, iss) and
     * the internal challenge claim used for signature verification.
     *
     * Using reserved claim names could interfere with proper JWT validation and
     * security, so this validation ensures the integrity of the signed token.
     *
     * @param customClaims The map of custom claims to validate
     * @throws InvalidClaimException if any custom claim uses a reserved name
     */
    private fun validate(customClaims: Map<String, Any>) {
        if (customClaims.keys.intersect(RESERVE_NAME).isNotEmpty()) {
            journey.config.logger.w(
                "DeviceSigningVerifierCallback: Custom claims contains reserved names: ${
                    customClaims.keys.intersect(
                        RESERVE_NAME
                    )
                }"
            )
            throw InvalidClaimException("Custom claims contains reserved names: $RESERVE_NAME")
        }
    }

    /**
     * Companion object containing constants and static configuration for the callback.
     */
    companion object {
        /**
         * List of reserved JWT claim names that cannot be used as custom claims.
         *
         * These claim names are automatically managed by the SDK during JWT creation
         * and signing. Custom claims using these names would conflict with the
         * standard JWT structure and security model:
         *
         * - **sub** (Subject): The user identifier for whom the JWT is issued
         * - **exp** (Expiration Time): When the JWT expires
         * - **iat** (Issued At): When the JWT was created
         * - **nbf** (Not Before): When the JWT becomes valid
         * - **iss** (Issuer): Who issued the JWT
         * - **challenge**: Internal claim containing the cryptographic challenge
         *
         * @see JWTClaimNames
         * @see CHALLENGE
         */
        val RESERVE_NAME = listOf(
            JWTClaimNames.SUBJECT,
            JWTClaimNames.EXPIRATION_TIME,
            JWTClaimNames.ISSUED_AT,
            JWTClaimNames.NOT_BEFORE,
            JWTClaimNames.ISSUER,
            CHALLENGE
        )
    }


}