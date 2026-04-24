/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize

import com.pingidentity.logger.Logger
import com.pingidentity.utils.PingDsl
import io.keyless.sdk.Keyless
import io.keyless.sdk.biom.liveness.LivenessSettings
import io.keyless.sdk.configurations.ClientStateType
import io.keyless.sdk.configurations.DynamicLinkingInfo
import io.keyless.sdk.configurations.LogsConfiguration
import io.keyless.sdk.configurations.OperationInfo
import io.keyless.sdk.configurations.PresentationStyle
import io.keyless.sdk.configurations.SetupConfig
import io.keyless.sdk.configurations.auth.BiomAuthConfig
import io.keyless.sdk.configurations.enroll.BiomEnrollConfig
import io.keyless.sdk.core.actions.model.JwtSigningInfo
import io.keyless.sdk.errorshandling.AuthenticationSuccess
import io.keyless.sdk.errorshandling.EnrollmentSuccess
import io.keyless.sdk.logger.LogLevels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import io.keyless.sdk.Keyless as _Keyless

/**
 * Kotlin facade over the Keyless biometric SDK.
 *
 * Wraps the callback-based [io.keyless.sdk.Keyless] API in suspending coroutine functions,
 * serialises concurrent calls via a [Mutex] to prevent race conditions, and forwards SDK log
 * events to the Ping [Logger].
 *
 * Typical usage within a Journey callback:
 * ```kotlin
 * val configResult = Keyless.config {
 *     apiKey = "YOUR_API_KEY"
 *     host   = listOf("https://your-keyless-host.example.com")
 * }
 * configResult.onFailure { return Result.failure(it) }
 *
 * val enrollResult = Keyless.enroll {
 *     jwtSigningInfo = JwtSigningInfo(claimTransactionData = "server-tx-data")
 * }
 * ```
 *
 * @see EnrollConfig
 * @see AuthConfig
 * @see KeylessConfig
 */
object Keyless {

    private lateinit var keylessConfig: KeylessConfig
    private var isInitialized: Boolean = false
    private var lock = Mutex()

    // Collect logs without blocking config() using a dedicated scope
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Initialises and configures the underlying Keyless SDK.
     *
     * Must be called once before [enroll] or [authenticate]. Successive calls are safe — each
     * call reconfigures the SDK with the latest [KeylessConfig] values.
     *
     * The call is serialised by an internal [Mutex] so it is safe to invoke concurrently.
     *
     * @param config DSL block that populates a [KeylessConfig] (required: [KeylessConfig.apiKey]
     *   and [KeylessConfig.host]).
     * @return [Result.success] if the Keyless SDK accepted the configuration;
     *   [Result.failure] with the SDK error otherwise.
     */
    suspend fun config(config: KeylessConfig.() -> Unit): Result<Unit> = lock.withLock {
        keylessConfig = KeylessConfig().apply(config)
        // Collect custom logs asynchronously
        Keyless.customLogs
            .onEach { logEvent -> Logger.logger.e(logEvent.eventBody.toString()) }
            .launchIn(logScope)
        // Return the result of Keyless.configure without early return to avoid unreachable code warnings
        return suspendCancellableCoroutine { cont ->
            _Keyless.configure(
                SetupConfig(
                    keylessConfig.apiKey,
                    keylessConfig.host,
                    keylessLogsConfiguration = LogsConfiguration(
                        enabled = true,
                        logLevel = LogLevels.TRACE
                    ),
                    customLogsConfiguration = LogsConfiguration(
                        enabled = true
                    )
                )
            ) { result ->
                when (result) {
                    is Keyless.KeylessResult.Success -> {
                        cont.resume(Result.success(result.value))
                    }

                    is Keyless.KeylessResult.Failure -> {
                        cont.resume(Result.failure(result.error))
                    }
                }
            }
        }
    }

    /**
     * Launches the biometric **enrollment** ceremony.
     *
     * Applies [block] to a default [EnrollConfig], builds a [BiomEnrollConfig], resets any
     * previous Keyless session, and calls the SDK's enroll API. The call is serialised by the
     * internal [Mutex].
     *
     * Prefer calling the Journey callback's `enroll()` function instead of this function
     * directly — the callback variant automatically maps all server-supplied output fields to
     * [EnrollConfig] before handing control to the optional [block].
     *
     * @param block Optional DSL block to override [EnrollConfig] defaults.
     * @return [Result.success] wrapping [EnrollmentSuccess] (contains `signedJwt`, `clientState`,
     *   and `keylessId`); [Result.failure] with the SDK error otherwise.
     */
    suspend fun enroll(block: EnrollConfig.() -> Unit = {}): Result<EnrollmentSuccess> = lock.withLock {
        val c = EnrollConfig().apply(block)
        // Return the result of enroll as the last expression
        return suspendCancellableCoroutine { cont ->
            _Keyless.reset {
                val enrollConfig = BiomEnrollConfig(
                    operationInfo = c.operationInfo,
                    jwtSigningInfo = c.jwtSigningInfo,
                    livenessConfiguration = c.livenessConfiguration,
                    livenessEnvironmentAware = c.livenessEnvironmentAware,
                    cameraDelaySeconds = c.cameraDelaySeconds,
                    shouldRetrieveEnrollmentFrame = c.shouldRetrieveEnrollmentFrame,
                    shouldRetrieveTemporaryState = c.shouldRetrieveTemporaryState,
                    temporaryState = c.temporaryState,
                    customSecret = c.customSecret,
                    iamToken = c.iamToken,
                    showSuccessFeedback = c.showSuccessFeedback,
                    showFailureFeedback = c.showFailureFeedback,
                    showInstructionsScreen = c.showInstructionsScreen,
                    generatingClientState = c.generatingClientState,
                    clientState = c.clientState,
                )
                _Keyless.enroll(enrollConfig) { result ->
                    when (result) {
                        is Keyless.KeylessResult.Success -> {
                            cont.resume(Result.success(result.value))
                        }

                        is Keyless.KeylessResult.Failure -> {
                            cont.resume(Result.failure(result.error))
                        }
                    }
                }
            }
        }
    }

    /**
     * Launches the biometric **authentication** ceremony.
     *
     * Applies [block] to a default [AuthConfig], builds a [BiomAuthConfig], and calls the SDK's
     * authenticate API. The call is serialised by the internal [Mutex].
     *
     * Prefer calling the Journey callback's `authenticate()` function instead of this function
     * directly — the callback variant automatically maps all server-supplied output fields to
     * [AuthConfig] before handing control to the optional [block].
     *
     * @param block Optional DSL block to override [AuthConfig] defaults.
     * @return [Result.success] wrapping [AuthenticationSuccess] (contains `signedJwt` and
     *   `clientState`); [Result.failure] with the SDK error otherwise.
     */
    suspend fun authenticate(block: AuthConfig.() -> Unit = {}): Result<AuthenticationSuccess> = lock.withLock {
        val c = AuthConfig().apply(block)
        return suspendCancellableCoroutine { cont ->
            val authConfig = BiomAuthConfig(
                shouldRemovePin = c.shouldRemovePin,
                operationInfo = c.operationInfo,
                jwtSigningInfo = c.jwtSigningInfo,
                dynamicLinkingInfo = c.dynamicLinkingInfo,
                shouldRetrieveTemporaryState = c.shouldRetrieveTemporaryState,
                livenessConfiguration = c.livenessConfiguration,
                livenessEnvironmentAware = c.livenessEnvironmentAware,
                cameraDelaySeconds = c.cameraDelaySeconds,
                showSuccessFeedback = c.showSuccessFeedback,
                deviceToRevoke = c.deviceToRevoke,
                shouldRetrieveSecret = c.shouldRetrieveSecret,
                shouldDeleteSecret = c.shouldDeleteSecret,
                presentationStyle = c.presentationStyle,
                generatingClientState = c.generatingClientState,
                shouldRetrieveAuthenticationFrame = c.shouldRetrieveAuthenticationFrame,
            )
            _Keyless.authenticate(authConfig) { result ->
                when (result) {
                    is Keyless.KeylessResult.Success -> {
                        cont.resume(Result.success(result.value))
                    }

                    is Keyless.KeylessResult.Failure -> {
                        cont.resume(Result.failure(result.error))
                    }
                }
            }
        }
    }
}

/**
 * DSL configuration for [Keyless.enroll].
 *
 * Maps 1-to-1 onto [BiomEnrollConfig]. All properties have the same defaults
 * as the Keyless SDK's no-arg `BiomEnrollConfig()` constructor.
 *
 * Usage:
 * ```kotlin
 * Keyless.enroll {
 *     jwtSigningInfo = JwtSigningInfo(claimTransactionData = "my-data")
 *     operationInfo = OperationInfo(operationId = "op-123", payload = "...", externalUserId = "u1")
 *     livenessConfiguration = LivenessSettings.LivenessConfiguration.LEVEL_1
 *     livenessEnvironmentAware = true
 *     cameraDelaySeconds = 2
 *     showSuccessFeedback = true
 *     showFailureFeedback = true
 *     showInstructionsScreen = true
 *     generatingClientState = ClientStateType.BACKUP
 *     customSecret = "my-secret"
 * }
 * ```
 */
@PingDsl
class EnrollConfig {
    /** JWT signing info passed to the enrollment ceremony. */
    var jwtSigningInfo: JwtSigningInfo = JwtSigningInfo(claimTransactionData = "")

    /** Operation info containing operationId, payload, and externalUserId. */
    var operationInfo: OperationInfo = OperationInfo("", "", "")

    /** Liveness security level configuration. */
    var livenessConfiguration: LivenessSettings.LivenessConfiguration =
        LivenessSettings.LivenessConfiguration.LEVEL_1

    /** Whether the liveness check should be environment-aware. */
    var livenessEnvironmentAware: Boolean = false

    /** Delay in seconds before the camera capture begins. */
    var cameraDelaySeconds: Int = 0

    /** Whether the enrollment frame should be retrieved from the SDK. */
    var shouldRetrieveEnrollmentFrame: Boolean = false

    /**
     * Whether to retrieve a temporary state token from the SDK.
     * @see temporaryState
     */
    var shouldRetrieveTemporaryState: Boolean = false

    /** Temporary state token to supply to the SDK. */
    var temporaryState: String = ""

    /** Custom secret injected into the enrollment flow. */
    var customSecret: String = ""

    /** IAM token used for server-side authorisation of the enrollment. */
    var iamToken: String = ""

    /** Whether the SDK should display a success animation after enrollment. */
    var showSuccessFeedback: Boolean = true

    /** Whether the SDK should display a failure animation after enrollment. */
    var showFailureFeedback: Boolean = true

    /** Whether the SDK should present an instructions screen before enrollment. */
    var showInstructionsScreen: Boolean = true

    /** Determines how the client state is generated during enrollment. Defaults to [ClientStateType.BACKUP]. */
    var generatingClientState: ClientStateType = ClientStateType.BACKUP

    /** Pre-existing client state to restore before the enrollment ceremony. */
    var clientState: String = ""
}

/**
 * DSL configuration for [Keyless.authenticate].
 *
 * Maps 1-to-1 onto [BiomAuthConfig]. All properties have the same defaults
 * as the Keyless SDK's no-arg `BiomAuthConfig()` constructor.
 *
 * Usage:
 * ```kotlin
 * Keyless.authenticate {
 *     jwtSigningInfo = JwtSigningInfo(claimTransactionData = "my-data")
 *     operationInfo = OperationInfo(operationId = "op-123", payload = "...", externalUserId = "u1")
 *     livenessConfiguration = LivenessSettings.LivenessConfiguration.LEVEL_1
 *     livenessEnvironmentAware = true
 *     presentationStyle = PresentationStyle.CAMERA_PREVIEW
 * }
 * ```
 */
@PingDsl
class AuthConfig {
    /** JWT signing info passed to the authentication ceremony. */
    var jwtSigningInfo: JwtSigningInfo = JwtSigningInfo(claimTransactionData = "")

    /** Whether to remove a previously stored PIN as part of this authentication. */
    var shouldRemovePin: Boolean = false

    /** Operation info containing operationId, payload, and externalUserId. */
    var operationInfo: OperationInfo = OperationInfo("", "", "")

    /** Dynamic linking info containing transaction data to be signed. */
    var dynamicLinkingInfo: DynamicLinkingInfo = DynamicLinkingInfo("")

    /**
     * Whether to retrieve a temporary state token from the SDK.
     */
    var shouldRetrieveTemporaryState: Boolean = false

    /** Liveness security level configuration. */
    var livenessConfiguration: LivenessSettings.LivenessConfiguration =
        LivenessSettings.LivenessConfiguration.LEVEL_1

    /** Whether the liveness check should be environment-aware. */
    var livenessEnvironmentAware: Boolean = false

    /** Delay in seconds before the camera capture begins. */
    var cameraDelaySeconds: Int = 0

    /** Whether the SDK should display a success animation after authentication. */
    var showSuccessFeedback: Boolean = true

    /** Device ID to revoke as part of this authentication flow (leave empty to skip). */
    var deviceToRevoke: String = ""

    /** Whether the SDK should retrieve the stored secret after authentication. */
    var shouldRetrieveSecret: Boolean = false

    /** Whether the SDK should delete the stored secret after authentication. */
    var shouldDeleteSecret: Boolean = false

    /** Controls how the camera UI is presented to the user. */
    var presentationStyle: PresentationStyle = PresentationStyle.CAMERA_PREVIEW

    /** Determines how the client state is generated. Defaults to [ClientStateType.BACKUP]. */
    var generatingClientState: ClientStateType = ClientStateType.BACKUP

    /** Whether the authentication frame should be retrieved from the SDK. */
    var shouldRetrieveAuthenticationFrame: Boolean = false
}

/**
 * DSL configuration for the [Keyless] `config` function.
 *
 * Provides the connection parameters required by the Keyless SDK to reach the
 * PingOne Recognize service.
 *
 * Usage:
 * ```kotlin
 * Keyless.config {
 *     apiKey = "YOUR_API_KEY"
 *     host   = listOf("https://recognize.example.com")
 * }
 * ```
 */
@PingDsl
open class KeylessConfig {
    /** API key that authenticates this application with the Keyless / Recognize service. */
    var apiKey: String = ""

    /** Ordered list of Keyless service host URLs. The SDK will attempt them in order. */
    var host: List<String> = emptyList()
}