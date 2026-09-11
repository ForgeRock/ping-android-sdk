/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

/**
 * OAuth 2.0 Device Authorization Grant flow implementation (RFC 8628).
 *
 * Contains [OidcDeviceClient] and the factory function of the same name, along with the
 * private types used to execute device-code token polling.
 */
package com.pingidentity.oidc

import com.pingidentity.browser.BrowserCanceledException
import com.pingidentity.browser.BrowserLauncher.launch
import com.pingidentity.browser.BrowserLauncher.redirectUri
import com.pingidentity.exception.ApiException
import com.pingidentity.network.isSuccess
import com.pingidentity.oidc.Constants.CLIENT_ID
import com.pingidentity.oidc.Constants.DEVICE_CODE
import com.pingidentity.oidc.Constants.GRANT_TYPE
import com.pingidentity.oidc.Constants.SCOPE
import com.pingidentity.oidc.Constants.URN_DEVICE_CODE_GRANT_TYPE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import kotlin.time.Duration.Companion.milliseconds

// Error codes defined by RFC 8628 §3.5
private const val ERROR_AUTHORIZATION_PENDING = "authorization_pending"
private const val ERROR_SLOW_DOWN = "slow_down"
private const val ERROR_EXPIRED_TOKEN = "expired_token"
private const val ERROR_ACCESS_DENIED = "access_denied"
private const val SLOW_DOWN_INCREMENT_SECONDS = 5
private const val INITIAL_TRANSPORT_RETRY_DELAY_SECONDS = 1
private const val MAX_TRANSPORT_RETRY_DELAY_SECONDS = 20
private const val KTOR_CONNECT_FAILURE = "io.ktor.client.engine.cio.FailToConnectException"

/**
 * Factory function to create an [OidcDeviceClient] with the provided configuration block.
 *
 * Example:
 * ```kotlin
 * val client = OidcDeviceClient {
 *     discoveryEndpoint = "https://auth.example.com/.well-known/openid-configuration"
 *     clientId = "my-client-id"
 *     scopes = mutableSetOf("openid", "profile")
 * }
 * ```
 *
 * @param block Configuration block applied to [OidcClientConfig].
 * @return A configured [OidcDeviceClient] instance.
 */
fun OidcDeviceClient(block: OidcClientConfig.() -> Unit = {}): OidcDeviceClient {
    val config = OidcClientConfig().apply(block)
    return OidcDeviceClient(config)
}

/**
 * Factory function to create an [OidcDeviceClient] from a JSON configuration object.
 *
 * Required OIDC fields are nested under the `oidc` key. The `deviceAuthorizationEndpoint`
 * can be overridden via the `openId` sub-object inside `oidc`. Example:
 * ```json
 * {
 *   "log": "STANDARD",
 *   "oidc": {
 *     "clientId": "my-client-id",
 *     "discoveryEndpoint": "https://auth.example.com/.well-known/openid-configuration",
 *     "scopes": ["openid", "profile"],
 *     "redirectUri": "myapp://oauth2redirect",
 *     "acrValues": "urn:mace:incommon:iap:silver",
 *     "par": true,
 *     "additionalParameters": { "max_age": "3600" },
 *     "openId": {
 *       "deviceAuthorizationEndpoint": "https://auth.example.com/device/code",
 *       "authorizationEndpoint": "https://auth.example.com/authorize",
 *       "tokenEndpoint": "https://auth.example.com/token",
 *       "userinfoEndpoint": "https://auth.example.com/userinfo",
 *       "endSessionEndpoint": "https://auth.example.com/logout",
 *       "revocationEndpoint": "https://auth.example.com/revoke"
 *     }
 *   }
 * }
 * ```
 *
 * @param json The JSON configuration object.
 * @return A [Result] containing the configured [OidcDeviceClient] or an exception if the configuration is invalid.
 */
fun OidcDeviceClient(json: JsonObject): Result<OidcDeviceClient> {
    return runCatching {
        val configParser = JsonConfigParser(json)
        val oidcConfigParser = JsonConfigParser(configParser.required<JsonObject>(JsonConfigKey.OIDC))
        OidcDeviceClient {
            logger = configParser.logLevel()
            discoveryEndpoint = oidcConfigParser.required<String>(JsonConfigKey.DISCOVERY_ENDPOINT)
            clientId = oidcConfigParser.required<String>(JsonConfigKey.CLIENT_ID)
            scopes = oidcConfigParser.scopeSet(JsonConfigKey.SCOPES)
            redirectUri = oidcConfigParser.optional<String>(JsonConfigKey.REDIRECT_URI, "")
            update(oidcConfigParser)
        }
    }
}

/**
 * OAuth 2.0 Device Authorization Grant client (RFC 8628).
 *
 * This client handles the requesting-device side of the Device Authorization flow:
 * 1. Requests a device code from the authorization server.
 * 2. Polls the token endpoint until the user approves, the code expires, or an error occurs.
 * 3. Optionally opens the verification URI in a browser tab for the user's convenience.
 *
 * @property config The configuration for this client.
 */
class OidcDeviceClient(internal val config: OidcClientConfig) {

    internal var clock: () -> Long = System::currentTimeMillis
    internal var retryDelay: suspend (Long) -> Unit = { delay(it.milliseconds) }

    /**
     * Starts the device authorization flow and polls for the token.
     *
     * Emits [DeviceFlowStatus.Started] immediately after obtaining the device code, then emits
     * [DeviceFlowStatus.Polling] while waiting for authorization or retrying a transient transport
     * failure. Transient token-endpoint failures are retried with bounded backoff while the device
     * code remains valid. The flow continues until one of the terminal states is reached:
     * - [DeviceFlowStatus.Success] — access token received and stored.
     * - [DeviceFlowStatus.Expired] — device code expired.
     * - [DeviceFlowStatus.AccessDenied] — user denied the authorization request.
     * - [DeviceFlowStatus.Failure] — unrecoverable error occurred.
     *
     * The flow closes automatically after emitting any terminal state.
     */
    fun deviceAuthorization(): Flow<DeviceFlowStatus> = flow {
        val logger = config.logger
        try {
            config.init()
            logger.i("Starting device authorization flow")

            val deviceAuthEndpoint = config.openId.deviceAuthorizationEndpoint
            if (deviceAuthEndpoint.isEmpty()) {
                emit(DeviceFlowStatus.Failure(IllegalStateException("device_authorization_endpoint is not configured")))
                return@flow
            }

            val deviceAuthResponse = requestDeviceAuthorization(deviceAuthEndpoint)
            emit(DeviceFlowStatus.Started(deviceAuthResponse))

            val expiresAt = clock() + (deviceAuthResponse.expiresIn * 1000L)
            var pollInterval = deviceAuthResponse.interval
            var pollCount = 0

            var transportRetryDelaySeconds = INITIAL_TRANSPORT_RETRY_DELAY_SECONDS
            var nextPollDelayMillis = pollInterval * 1000L
            while (clock() < expiresAt) {
                currentCoroutineContext().ensureActive()
                val delayMillis = nextPollDelayMillis.coerceAtMost(
                    (expiresAt - clock()).coerceAtLeast(0L)
                )
                retryDelay(delayMillis)
                currentCoroutineContext().ensureActive()

                if (clock() >= expiresAt) {
                    logger.i("Device code expired (wall-clock)")
                    emit(DeviceFlowStatus.Expired)
                    return@flow
                }

                pollCount++
                logger.i("Polling token endpoint (attempt $pollCount)")

                val tokenResponse = try {
                    currentCoroutineContext().ensureActive()
                    pollTokenEndpoint(deviceAuthResponse.deviceCode, config.openId.tokenEndpoint)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!e.isTransientTransportFailure()) {
                        throw e
                    }

                    val now = clock()
                    val remainingSeconds = ((expiresAt - now).coerceAtLeast(0L) / 1000L).toInt()
                    val retryDelaySeconds = transportRetryDelaySeconds.coerceAtMost(remainingSeconds)
                    if (retryDelaySeconds <= 0) {
                        logger.i("Device code expired (retry window closed)")
                        emit(DeviceFlowStatus.Expired)
                        return@flow
                    }

                    val retryDelayMillis = retryDelaySeconds * 1000L
                    val nextPollAt = now + retryDelayMillis
                    logger.w("Token poll network failure; retrying in ${retryDelaySeconds}s")
                    emit(DeviceFlowStatus.Polling(pollCount, retryDelaySeconds, nextPollAt))
                    transportRetryDelaySeconds =
                        (transportRetryDelaySeconds * 2).coerceAtMost(MAX_TRANSPORT_RETRY_DELAY_SECONDS)
                    nextPollDelayMillis = retryDelayMillis
                    continue
                }

                // A response, including an RFC 8628 error response, means the transport recovered.
                transportRetryDelaySeconds = INITIAL_TRANSPORT_RETRY_DELAY_SECONDS
                when {
                    tokenResponse.isSuccess -> {
                        val token = json.decodeFromString<Token>(tokenResponse.body)
                        config.tokenStorage.save(token)
                        logger.i("Device flow succeeded")
                        emit(DeviceFlowStatus.Success(OidcUser(config)))
                        return@flow
                    }

                    tokenResponse.error == ERROR_AUTHORIZATION_PENDING -> {
                        val now = clock()
                        val effectiveDelayMillis = (pollInterval * 1000L).coerceAtMost(
                            (expiresAt - now).coerceAtLeast(0L)
                        )
                        if (now >= expiresAt) {
                            logger.i("Device code expired (polling interval elapsed)")
                            emit(DeviceFlowStatus.Expired)
                            return@flow
                        }
                        nextPollDelayMillis = effectiveDelayMillis
                        val nextPollAt = now + effectiveDelayMillis
                        val effectiveDelaySeconds = (effectiveDelayMillis / 1000L).toInt()
                        emit(DeviceFlowStatus.Polling(pollCount, effectiveDelaySeconds, nextPollAt))
                    }

                    tokenResponse.error == ERROR_SLOW_DOWN -> {
                        // RFC 8628 §3.5: increase interval by 5 s on each slow_down response.
                        pollInterval += SLOW_DOWN_INCREMENT_SECONDS
                        logger.i("Slow down received; new interval: $pollInterval s")
                        val now = clock()
                        val effectiveDelayMillis = (pollInterval * 1000L).coerceAtMost(
                            (expiresAt - now).coerceAtLeast(0L)
                        )
                        if (now >= expiresAt) {
                            logger.i("Device code expired (polling interval elapsed)")
                            emit(DeviceFlowStatus.Expired)
                            return@flow
                        }
                        nextPollDelayMillis = effectiveDelayMillis
                        val nextPollAt = now + effectiveDelayMillis
                        val effectiveDelaySeconds = (effectiveDelayMillis / 1000L).toInt()
                        emit(DeviceFlowStatus.Polling(pollCount, effectiveDelaySeconds, nextPollAt))
                    }

                    tokenResponse.error == ERROR_EXPIRED_TOKEN -> {
                        logger.i("Device code expired")
                        emit(DeviceFlowStatus.Expired)
                        return@flow
                    }

                    tokenResponse.error == ERROR_ACCESS_DENIED -> {
                        logger.i("Device flow access denied")
                        emit(DeviceFlowStatus.AccessDenied)
                        return@flow
                    }

                    else -> {
                        val message = tokenResponse.error ?: "Unknown token endpoint error"
                        logger.w("Device flow failed: $message")
                        emit(DeviceFlowStatus.Failure(IllegalStateException(message)))
                        return@flow
                    }
                }
            }

            logger.i("Device code expired (polling loop exit)")
            emit(DeviceFlowStatus.Expired)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w("Device flow failed with an unexpected exception")
            currentCoroutineContext().ensureActive()
            emit(DeviceFlowStatus.Failure(e))
        }
    }

    /**
     * Opens [verificationUriComplete] in a browser tab (Custom Tab / Auth Tab) so the user can
     * approve the device authorization request.
     *
     * This call is non-blocking with respect to the polling [Flow] — the caller should launch it
     * in a separate coroutine. A [BrowserCanceledException] is silently
     * swallowed because tab dismissal is an expected user action. Any other exception is
     * propagated to the caller.
     *
     * @param verificationUriComplete The verification URI (typically contains the user code as a
     *        query parameter for pre-filled approval).
     */
    suspend fun authorize(verificationUriComplete: String) {
        try {
            // The redirectUri is not actually used in the device flow
            launch(URL(verificationUriComplete), redirectUri)
        } catch (_: BrowserCanceledException) {
            // Tab dismissal is an expected user action — don't surface it as an error.
            config.logger.d("Browser tab dismissed (expected for device flow)")
        }
    }

    /**
     * Returns the authenticated [User] if a valid token is present in storage, or null otherwise.
     */
    suspend fun user(): User? {
        config.init()
        return config.tokenStorage.get()?.let { OidcUser(config) }
    }


    private suspend fun requestDeviceAuthorization(endpoint: String): DeviceAuthorizationResponse {
        val response = config.httpClient.request {
            url = endpoint
            form {
                put(CLIENT_ID, config.clientId)
                put(SCOPE, config.scopes.joinToString(" "))
            }
        }
        if (response.status.isSuccess()) {
            return json.decodeFromString<DeviceAuthorizationResponse>(response.body())
        }
        throw ApiException(
            response.status,
            "Device authorization request failed: Response ${response.body()}"
        )
    }

    /**
     * Outcome of a single token-endpoint poll attempt.
     *
     * @property isSuccess `true` when the HTTP response status is 2xx and the body contains a token.
     * @property body Raw response body; parsed as a [Token] on success or a [TokenErrorResponse] on failure.
     * @property error The `error` field from an OAuth error response, or `null` on success.
     */
    private data class TokenPollResult(
        val isSuccess: Boolean,
        val body: String,
        val error: String?,
    )

    /**
     * Returns whether this exception or one of its causes represents a transient transport failure.
     */
    private fun Throwable.isTransientTransportFailure(): Boolean =
        generateSequence(this) { it.cause }.any { cause ->
            cause is ConnectException ||
                cause is SocketException ||
                cause is SocketTimeoutException ||
                cause is UnknownHostException ||
                cause is UnresolvedAddressException ||
                cause::class.qualifiedName == KTOR_CONNECT_FAILURE
        }

    private suspend fun pollTokenEndpoint(
        deviceCode: String,
        tokenEndpoint: String
    ): TokenPollResult {
        val response = config.httpClient.request {
            url = tokenEndpoint
            form {
                put(GRANT_TYPE, URN_DEVICE_CODE_GRANT_TYPE)
                put(DEVICE_CODE, deviceCode)
                put(CLIENT_ID, config.clientId)
            }
        }
        val body = response.body()
        return if (response.status.isSuccess()) {
            TokenPollResult(isSuccess = true, body = body, error = null)
        } else {
            // Best-effort parse — if the body is not a valid error JSON the error field is null
            // and the caller's else branch will surface it as an unknown error.
            val error = runCatching {
                json.decodeFromString<TokenErrorResponse>(body).error
            }.getOrNull()
            TokenPollResult(isSuccess = false, body = body, error = error)
        }
    }
}

/**
 * Minimal error response to extract the `error` field from a failed token endpoint response.
 */
@kotlinx.serialization.Serializable
private data class TokenErrorResponse(
    @kotlinx.serialization.SerialName("error")
    val error: String? = null,
)
