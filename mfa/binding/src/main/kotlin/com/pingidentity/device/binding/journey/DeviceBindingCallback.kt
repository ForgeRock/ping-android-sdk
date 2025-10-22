/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.journey

import androidx.biometric.BiometricPrompt
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.CryptoKey
import com.pingidentity.device.binding.CryptoKeyAware
import com.pingidentity.device.binding.Prompt
import com.pingidentity.device.binding.UserKey
import com.pingidentity.device.binding.UserKeysStorage
import com.pingidentity.device.binding.authenticator.AppPinAuthenticator
import com.pingidentity.device.binding.authenticator.Attestation
import com.pingidentity.device.binding.authenticator.BiometricAuthenticator
import com.pingidentity.device.binding.authenticator.DeviceAuthenticator
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType
import com.pingidentity.device.binding.authenticator.KeyPair
import com.pingidentity.device.binding.authenticator.None
import com.pingidentity.device.binding.authenticator.SigningParameters
import com.pingidentity.device.binding.authenticator.exception.DeviceNotSupportedException
import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.Journey
import com.pingidentity.journey.plugin.JourneyAware
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.security.PrivateKey
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Journey callback for handling device binding operations in authentication flows.
 *
 * This callback enables applications to bind a device to a user's identity by creating
 * and registering cryptographic keys with various authentication methods. It supports
 * multiple authentication types including biometric, PIN, and no authentication.
 *
 * The callback handles the complete device binding lifecycle:
 * - Key pair generation with optional attestation
 * - User authentication based on the specified authentication type
 * - JWT signing with the generated keys
 * - Storage of user key metadata
 * - Error handling and cleanup on failures
 *
 * @see AbstractCallback
 * @see JourneyAware
 * @see DeviceBindingConfig
 */
open class DeviceBindingCallback : AbstractCallback(), JourneyAware {

    /**
     * The Journey instance providing access to configuration and logging.
     * Injected by the Journey framework during callback initialization.
     */
    override lateinit var journey: Journey

    /**
     * The unique identifier for the user performing device binding.
     * This value is set from the server callback configuration and used
     * as the subject in the generated JWT and for key storage identification.
     */
    lateinit var userId: String
        private set

    /**
     * The username associated with the user performing device binding.
     * This can be overridden if needed and is used for display purposes
     * and stored in the user key metadata.
     */
    lateinit var userName: String

    /**
     * The cryptographic challenge provided by the server that must be
     * included in the signed JWT to prove possession of the private key.
     */
    lateinit var challenge: String
        private set

    /**
     * The type of authentication required to access the cryptographic keys.
     * Determines whether biometric, PIN, or no authentication is required.
     */
    lateinit var deviceBindingAuthenticationType: DeviceBindingAuthenticationType
        private set

    /**
     * The title to display in authentication prompts (e.g., biometric or PIN dialogs).
     * Provides context to users about what they are authenticating for.
     */
    lateinit var title: String
        private set

    /**
     * The subtitle to display in authentication prompts.
     * Provides additional context about the authentication request.
     */
    lateinit var subtitle: String
        private set

    /**
     * The description to display in authentication prompts.
     * Explains why authentication is required and what will happen.
     */
    lateinit var description: String
        private set

    /**
     * The timeout in seconds for the entire device binding operation.
     * If the operation takes longer than this time, it will be cancelled.
     * Default value is 60 seconds.
     */
    var timeout: Int = 60
        private set

    /**
     * The attestation configuration specifying what type of key attestation
     * should be included when generating the key pair. Can be None for no
     * attestation or contain a challenge for hardware attestation.
     */
    lateinit var attestation: Attestation
        private set

    // Input fields that will be sent back to the server

    /**
     * The signed JWT containing the device binding proof.
     * Generated during the bind operation and sent to the server for verification.
     */
    private var jws: String = ""

    /**
     * The unique device identifier for this device.
     * Generated during the bind operation and sent to the server.
     */
    private var deviceId: String = ""

    /**
     * The human-readable name for this device.
     * Set during the bind operation and sent to the server.
     */
    private var deviceName: String = ""

    /**
     * Error message to be sent to the server if the device binding operation fails.
     * Can be overridden to provide custom error messages.
     */
    var clientError: String = ""

    /**
     * Initializes the attestation property with a default value if not set.
     * Ensures that the attestation property is always initialized to prevent
     * lateinit property access exceptions.
     */
    init {
        if (!::attestation.isInitialized) {
            attestation = Attestation.None
        }
    }

    /**
     * Initializes callback properties from the server-provided JSON configuration.
     *
     * This method is called by the Journey framework to populate the callback
     * with values received from the authentication server. Each property corresponds
     * to a specific aspect of the device binding configuration.
     *
     * @param name The name of the property to initialize
     * @param value The JSON value for the property
     */
    override fun init(name: String, value: JsonElement) {
        when (name) {
            "userId" -> userId = value.jsonPrimitive.content
            "username" -> userName = value.jsonPrimitive.content
            "challenge" -> challenge = value.jsonPrimitive.content
            "authenticationType" -> deviceBindingAuthenticationType =
                DeviceBindingAuthenticationType.valueOf(value.jsonPrimitive.content)
            "title" -> title = value.jsonPrimitive.content
            "subtitle" -> subtitle = value.jsonPrimitive.content
            "description" -> description = value.jsonPrimitive.content
            "timeout" -> timeout = value.jsonPrimitive.int
            "attestation" -> attestation = Attestation
                .fromBoolean(value.jsonPrimitive.boolean, Base64.decode(challenge))
        }
    }

    /**
     * Creates the payload to be sent back to the server after device binding completion.
     *
     * This method constructs the response payload containing the signed JWT, device
     * information, and any error messages. The server uses this information to
     * complete the device binding process and verify the device's cryptographic proof.
     *
     * @return A map containing the input values for server processing
     */
    override fun payload() = input(jws, deviceName, deviceId, clientError)

    /**
     * Performs the complete device binding operation.
     *
     * This is the main method that orchestrates the entire device binding process:
     * 1. Validates device support for the specified authentication type
     * 2. Clears any existing keys for the user
     * 3. Generates a new cryptographic key pair with optional attestation
     * 4. Authenticates the user based on the configured authentication type
     * 5. Signs a JWT with the generated key to prove possession
     * 6. Stores the user key metadata for future use
     * 7. Handles cleanup on failures
     *
     * The operation is performed within a timeout to prevent indefinite blocking.
     * If authentication fails or any step encounters an error, all created keys
     * are automatically cleaned up.
     *
     * @param config Optional configuration block to customize the device binding behavior
     * @return A Result containing the signed JWT on success, or an error on failure
     * @throws DeviceNotSupportedException if the device doesn't support the authentication type
     * @throws kotlinx.coroutines.TimeoutCancellationException if the operation exceeds the timeout
     */
    suspend fun bind(config: DeviceBindingConfig.() -> Unit = {}): Result<String> = runCatching {
        val logger = journey.config.logger
        logger.i("DeviceBindingCallback: Starting bind process for userId=$userId, authType=$deviceBindingAuthenticationType")

        val context = ContextProvider.context
        val deviceBindingConfig = DeviceBindingConfig().apply { this.logger = logger }.apply(config)
        val deviceAuthenticator = deviceBindingConfig.authenticator(
            deviceBindingAuthenticationType,
            Prompt(title, subtitle, description)
        ).also {
            if (it is CryptoKeyAware) it.cryptoKey = CryptoKey(userId)
        }
        val userKeyStorage = deviceBindingConfig.keyStorage()

        if (!deviceAuthenticator.isSupported(context, attestation)) {
            throw DeviceNotSupportedException("Device does not support ${deviceBindingAuthenticationType.name}")
        }

        var keyPair: KeyPair?
        var userKey: UserKey? = null
        val result: Result<Pair<PrivateKey, BiometricPrompt.CryptoObject?>>

        logger.d("DeviceBindingCallback: Clearing existing keys for userId=$userId")
        clearKeys(deviceAuthenticator, userKeyStorage)

        withTimeout(timeout.toDuration(DurationUnit.SECONDS)) {
            logger.d("DeviceBindingCallback: Generating new key pair for ${deviceAuthenticator.type}")
            keyPair = deviceAuthenticator.register(context, attestation).getOrThrow()
            logger.d("DeviceBindingCallback: Starting authentication process")
            result = deviceAuthenticator.authenticate(context)
        }

        result.onSuccess { (_, cryptoObject) ->
            logger.d("DeviceBindingCallback: Authentication successful, signing JWT")
            keyPair?.let { kp ->
                userKey = UserKey(
                    kp.keyAlias,
                    userId, userName, kid = UUID.randomUUID().toString(),
                    deviceBindingAuthenticationType
                ).apply {
                    logger.d("DeviceBindingCallback: Saving user key with kid=${this.kid}")
                    userKeyStorage.save(this)
                    logger.d("DeviceBindingCallback: Signing JWT with generated key")
                    jws = deviceAuthenticator.sign(
                        SigningParameters(
                            context = context,
                            algorithm = deviceBindingConfig.signingAlgorithm,
                            keyPair = kp,
                            signature = cryptoObject?.signature,
                            kid = this.kid,
                            userId = userId,
                            challenge = challenge,
                            issueTime = deviceBindingConfig.issueTime(),
                            notBeforeTime = deviceBindingConfig.notBeforeTime(),
                            expiration = deviceBindingConfig.expirationTime(timeout),
                            attestation = attestation
                        )
                    )
                    deviceId = deviceBindingConfig.deviceIdentifier.id()
                    deviceName = deviceBindingConfig.deviceName
                    logger.i("DeviceBindingCallback: Successfully bound device with deviceId=$deviceId")
                }
            }
        }.onFailure {
            logger.w("DeviceBindingCallback: Authentication Failed", it)
            logger.d("DeviceBindingCallback: Cleaning up created keys due to failure")
            userKey?.let {
                userKeyStorage.delete(it)
            }
            deviceAuthenticator.deleteKeys()
            throw it
        }
        jws
    }.onFailure {
        journey.config.logger.w("DeviceBindingCallback Error", it)
        clientError = toClientError(it)
    }

    /**
     * Clears all existing cryptographic keys and user key metadata for the specified user.
     *
     * This method ensures a clean state before device binding by removing:
     * - All cryptographic keys from the Android KeyStore for the user
     * - User key metadata from the storage system
     * - Keys from different authenticator types (AppPin, Biometric, None)
     *
     * The method handles cross-authenticator cleanup to ensure that switching between
     * authentication types doesn't leave orphaned keys. For example, if switching from
     * AppPin to Biometric authentication, any existing AppPin keys are properly removed.
     *
     * @param deviceAuthenticator The current device authenticator being used
     * @param userKeyStorage The storage system for user key metadata
     */
    private suspend fun clearKeys(deviceAuthenticator: DeviceAuthenticator, userKeyStorage: UserKeysStorage) {
        val logger = journey.config.logger
        logger.d("DeviceBindingCallback: Clearing keys for userId=$userId")
        val cryptoKey = CryptoKey(userId)
        when (deviceAuthenticator) {
            is AppPinAuthenticator -> {
                cryptoKey.delete()
            }
            is BiometricAuthenticator, is None -> {
                AppPinAuthenticator().apply { this.cryptoKey = cryptoKey }.deleteKeys()
            }
        }
        deviceAuthenticator.deleteKeys()
        userKeyStorage.deleteByUserId(userId)
    }

}