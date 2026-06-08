/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.exception.ApiException
import com.pingidentity.network.isSuccess
import com.pingidentity.oidc.Constants.AUTHORIZATION_CODE
import com.pingidentity.oidc.Constants.CLIENT_ID
import com.pingidentity.oidc.Constants.CODE
import com.pingidentity.oidc.Constants.CODE_VERIFIER
import com.pingidentity.oidc.Constants.GRANT_TYPE
import com.pingidentity.oidc.Constants.REDIRECT_URI
import com.pingidentity.oidc.Constants.REFRESH_TOKEN
import com.pingidentity.oidc.Constants.RESPONSE_TYPE
import com.pingidentity.oidc.Constants.TOKEN
import com.pingidentity.utils.Result
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.coroutineContext

/**
 * Factory function to create an instance of OidcClient with a given configuration.
 *
 * @param block A lambda with receiver on OidcClientConfig to configure the client.
 * @return An instance of OidcClient.
 */
inline fun OidcClient(block: OidcClientConfig.() -> Unit = {}): OidcClient {
    val oidcClientConfig = OidcClientConfig().apply(block)
    return OidcClient(oidcClientConfig)
}

/**
 * Factory function to create an instance of [OidcClient] from a JSON configuration.
 *
 * Required fields are nested under the `oidc` key. Example:
 * ```json
 * {
 *   "log": "STANDARD",
 *   "oidc": {
 *     "clientId": "my-client-id",
 *     "discoveryEndpoint": "https://auth.example.com/.well-known/openid-configuration",
 *     "scopes": ["openid", "profile"],
 *     "redirectUri": "myapp://oauth2redirect",
 *     "signOutRedirectUri": "myapp://logout",
 *     "refreshThreshold": 60,
 *     "loginHint": "user@example.com",
 *     "state": "custom-state",
 *     "nonce": "custom-nonce",
 *     "display": "page",
 *     "prompt": "login",
 *     "uiLocales": "en-US",
 *     "acrValues": "Level3",
 *     "par": true,
 *     "additionalParameters": { "max_age": "3600" },
 *     "openId": {
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
 * @return A [Result] containing the [OidcClient] instance or an error if the configuration is invalid.
 */
fun OidcClient(json: JsonObject): kotlin.Result<OidcClient> {
    return runCatching {
        val jsonConfigParser = JsonConfigParser(json)
        val oidcJsonConfig = JsonConfigParser(jsonConfigParser.required<JsonObject>(JsonConfigKey.OIDC))
        OidcClient {
            logger = jsonConfigParser.logLevel()
            clientId = oidcJsonConfig.required<String>(JsonConfigKey.CLIENT_ID)
            discoveryEndpoint = oidcJsonConfig.required<String>(JsonConfigKey.DISCOVERY_ENDPOINT)
            scopes = oidcJsonConfig.scopeSet(JsonConfigKey.SCOPES)
            redirectUri = oidcJsonConfig.required<String>(JsonConfigKey.REDIRECT_URI)
            update(oidcJsonConfig)
        }
    }
}

/**
 * Applies all optional OIDC fields from [oidcJsonConfig] to this [OidcClientConfig].
 *
 * Mandatory fields (`clientId`, `discoveryEndpoint`, `scopes`, `redirectUri`) are set by each
 * JSON factory independently. This function handles every optional field:
 * `display`, `par`, `loginHint`, `state`, `nonce`, `prompt`, `uiLocales`, `acrValues`,
 * `signOutRedirectUri`, `refreshThreshold`, `additionalParameters`, `storageFilename`,
 * and the `openId` endpoint-override sub-object.
 *
 * @param oidcJsonConfig Parser wrapping the `oidc` sub-object of the top-level JSON config.
 */
fun OidcClientConfig.update(oidcJsonConfig: JsonConfigParser) {
    display = oidcJsonConfig.optional<String?>(JsonConfigKey.DISPLAY, null)
    par = oidcJsonConfig.optional<Boolean>(JsonConfigKey.PAR, false)
    loginHint = oidcJsonConfig.optional<String?>(JsonConfigKey.LOGIN_HINT, null)
    state = oidcJsonConfig.optional<String?>(JsonConfigKey.STATE, null)
    nonce = oidcJsonConfig.optional<String?>(JsonConfigKey.NONCE, null)
    prompt = oidcJsonConfig.optional<String?>(JsonConfigKey.PROMPT, null)
    uiLocales = oidcJsonConfig.optional<String?>(JsonConfigKey.UI_LOCALES, null)
    acrValues = oidcJsonConfig.optional<String?>(JsonConfigKey.ACR_VALUES, null)
    signOutRedirectUri = oidcJsonConfig.optional<String?>(JsonConfigKey.SIGN_OUT_REDIRECT_URI, null)
    refreshThreshold = oidcJsonConfig.optional<Long>(JsonConfigKey.REFRESH_THRESHOLD, 0L)
    val additionalParams = oidcJsonConfig.additionalParameters(JsonConfigKey.ADDITIONAL_PARAMETERS)
    if (additionalParams != null) {
        additionalParameters = additionalParams
    }
    val storageFilename = oidcJsonConfig.optional<String>(JsonConfigKey.STORAGE_FILENAME, "")
    if (storageFilename.isNotEmpty()) {
        storage {
            fileName = storageFilename
        }
    }
    val openIdJson = oidcJsonConfig.optional<JsonObject?>(JsonConfigKey.OPEN_ID, null)
    if (openIdJson != null) {
        val openIdParser = JsonConfigParser(openIdJson)
        openIdOverride = {
            openIdParser.optional<String?>(JsonConfigKey.AUTHORIZATION_ENDPOINT, null)?.let { authorizationEndpoint = it }
            openIdParser.optional<String?>(JsonConfigKey.TOKEN_ENDPOINT, null)?.let { tokenEndpoint = it }
            openIdParser.optional<String?>(JsonConfigKey.USER_INFO_ENDPOINT, null)?.let { userinfoEndpoint = it }
            openIdParser.optional<String?>(JsonConfigKey.END_SESSION_ENDPOINT, null)?.let { endSessionEndpoint = it }
            openIdParser.optional<String?>(JsonConfigKey.REVOCATION_ENDPOINT, null)?.let { revocationEndpoint = it }
            openIdParser.optional<String?>(JsonConfigKey.DEVICE_AUTHORIZATION_ENDPOINT, null)?.let { deviceAuthorizationEndpoint = it }
        }
    }
}

internal typealias IdToken = String

/**
 * Class representing an OpenID Connect client.
 *
 * @property config The configuration for this client.
 */
open class OidcClient(private val config: OidcClientConfig) {
    private val logger = config.logger
    private val lock = Mutex()

    /**
     * Retrieves an access token. If a cached token is available and not expired, it is returned.
     * Otherwise, a new token is fetched with refresh token if refresh grant is available.
     *
     * @return A Result containing the access token or an error.
     */
    suspend fun token(): Result<Token, OidcError> = lock.withLock {
        return catch {
            config.init()
            logger.i("Getting access token")
            val cached = config.tokenStorage.get()
            cached?.let {
                if (!it.isExpired(config.refreshThreshold)) {
                    logger.i("Token is not expired. Returning cached token.")
                    return@catch it
                }
                try {
                    // Try to refresh the token if it has expired
                    logger.i("Token is expired. Attempting to refresh.")
                    it.refreshToken?.let { refreshToken ->
                        return@catch refreshToken(refreshToken)
                    }
                } catch (e: Exception) {
                    coroutineContext.ensureActive()
                    logger.w("Failed to refresh token. Revoking token and re-authenticating.", e)
                    revoke(it)
                }
            }
            // authenticate the user
            val code = config.agent.authenticate()
            val token = exchangeToken(code)
            config.tokenStorage.save(token)
            return@catch token
        }
    }

    /**
     * Refreshes the access token.
     * @return A Result containing the refreshed access token or an error.
     */
    suspend fun refresh(): Result<Token, OidcError> = lock.withLock {
        return catch {
            config.init()
            logger.i("Refreshing access token")
            val cached = config.tokenStorage.get()
            config.tokenStorage.delete()
            cached?.let {
                if (!it.isExpired(config.refreshThreshold)) {
                    logger.i("Token is not expired. Revoke the AccessToken.")
                    revoke(cached.accessToken)
                }
                it.refreshToken?.let { refreshToken ->
                    return@catch refreshToken(refreshToken)
                } ?: throw IllegalStateException(
                    "No Refresh token. Cannot refresh the access token."
                )
            } ?: throw IllegalStateException(
                "No Access token. Cannot refresh the access token."
            )
        }
    }

    /**
     * Refreshes the access token.
     *
     * @param refreshToken The refresh token to use for refreshing the access token.
     * @return The refreshed access token.
     */
    private suspend fun refreshToken(refreshToken: String): Token {
        config.init()
        logger.i("Refreshing token")

        val response = config.httpClient.request {
            url = config.openId.tokenEndpoint
            form {
                put(GRANT_TYPE, REFRESH_TOKEN)
                put(REFRESH_TOKEN, refreshToken)
                put(CLIENT_ID, config.clientId)
                put(RESPONSE_TYPE, CODE)
            }
        }
        if (response.status.isSuccess()) {
            val token = with(response) {
                json.decodeFromString<Token>(this.body())
            }
            config.tokenStorage.save(token)
            return token
        } else {
            throw ApiException(response.status, response.body())
        }
    }

    /**
     * Revokes the access token.
     */
    suspend fun revoke() {
        catch {
            lock.withLock {
                revoke(null)
            }
        }
    }

    /**
     * Revokes a specific access token. Best effort to revoke the token.
     * The stored token is removed regardless of the result.
     *
     * @param token The access token to revoke. If null, the currently stored token is revoked.
     */
    private suspend fun revoke(token: Token? = null) {
        val accessToken = token ?: config.tokenStorage.get()
        accessToken?.let {
            config.tokenStorage.delete()
            config.init()
            val t = it.refreshToken ?: it.accessToken
            revoke(t)
        }
    }

    private suspend fun revoke(token: String) {
        coroutineScope {
            // Run in the background, and we don't care about the result
            launch {
                config.httpClient.request {
                    url = config.openId.revocationEndpoint
                    form {
                        put(CLIENT_ID, config.clientId)
                        put(TOKEN, token)
                    }
                }
            }
        }
    }

    /**
     * Ends the session. Best effort to end the session.
     * The stored token is removed regardless of the result.
     *
     * @return A boolean indicating whether the session was ended successfully.
     */
    suspend fun endSession(): Boolean {
        return endSession {
            config.agent.endSession(it)
        }
    }

    /**
     * Ends the session with a custom sign-off procedure.
     * @param signOff A suspend function to perform the sign-off.
     * @return A boolean indicating whether the session was ended successfully.
     */
    suspend fun endSession(signOff: suspend (IdToken) -> Boolean): Boolean = lock.withLock {
        val result =
            catch {
                config.init()
                val accessToken = config.tokenStorage.get()
                accessToken?.let {
                    revoke(it)
                    it.idToken?.let { idToken ->
                        return@catch signOff(idToken)
                    }
                }
                //If there is no AccessToken, we assume the session is already ended
                true
            }
        return when (result) {
            is Result.Failure -> false
            is Result.Success -> result.value
        }
    }

    /**
     * Retrieves user information.
     * @return A Result containing the user information or an error.
     */
    suspend fun userinfo(): Result<JsonObject, OidcError> {
        return catch {
            config.init()
            when (val result = token()) { // Retrieve the access token
                is Result.Failure -> {
                    return Result.Failure(result.value)
                }

                is Result.Success -> {
                    val response =
                        config.httpClient.request {
                            url = config.openId.userinfoEndpoint
                            header(
                                "Authorization",
                                "Bearer ${result.value.accessToken}"
                            )
                        }
                    if (response.status.isSuccess()) {
                        return Result.Success(Json.parseToJsonElement(response.body()).jsonObject)
                    } else {
                        throw ApiException(response.status, response.body())
                    }
                }
            }
        }
    }

    /**
     * Exchanges an authorization code for an access token.
     * @param authCode The authorization code to exchange.
     * @return The access token.
     */
    private suspend fun exchangeToken(authCode: AuthCode): Token {
        config.init()
        logger.i("Exchanging token")
        val response = config.httpClient.request {
            url = config.openId.tokenEndpoint
            form {
                put(GRANT_TYPE, AUTHORIZATION_CODE)
                put(CODE, authCode.code)
                put(REDIRECT_URI, config.redirectUri)
                put(CLIENT_ID, config.clientId)
                authCode.codeVerifier?.let {
                    put(CODE_VERIFIER, it)
                }
            }
        }
        if (response.status.isSuccess()) {
            return with(response) {
                json.decodeFromString<Token>(this.body())
            }
        }
        throw ApiException(response.status, response.body())
    }
}