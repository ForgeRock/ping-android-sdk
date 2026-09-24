/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CreateRestoreCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.RestoreCredential
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.utils.PingDsl
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Client for Android's Restore Credentials feature, backed by
 * [androidx.credentials.CredentialManager].
 *
 * Restore Credentials let an app silently sign a user back in on a new device after the
 * device's Backup & Restore transfer completes, independent of the app's primary
 * authentication method (password, passkey, federated sign-in). This client only implements
 * the Android client-side integration - creating, retrieving, and clearing the restore
 * credential. The server-side FIDO2/WebAuthn validation is out of scope.
 *
 * @see <a href="https://developer.android.com/identity/sign-in/restore-credentials">Restore Credentials</a>
 */
class RestoreCredentialClient(private val config: RestoreCredentialClientConfig) {

    /**
     * Creates a restore credential for the currently signed-in user.
     *
     * Always attempts the request with cloud backup enabled first. If the device has no
     * backup or end-to-end encryption (screen lock) configured, [E2eeUnavailableException] is
     * thrown by the platform; this method catches it and retries once with cloud backup
     * disabled, so a restore credential is still created locally on the device.
     *
     * @param input the [PublicKeyCredentialCreationOptionsJSON](https://w3c.github.io/webauthn/#dictdef-publickeycredentialcreationoptionsjson)
     *              returned by the app server
     * @return a [Result] containing the registration response as a [JsonObject] on success,
     *         or an exception on failure
     */
    suspend fun create(
        input: JsonObject,
        block: RestoreCredentialCreationCustomizer.() -> Unit = {}
    ): Result<JsonObject> {
        val customizer = RestoreCredentialCreationCustomizer().apply(block)
        config.logger.d("Creating restore credential with cloud backup enabled")
        return try {
            val response = createRestoreCredential(input, customizer, isCloudBackupEnabled = true)
            config.logger.d("Restore credential created successfully")
            Result.success(response)
        } catch (e: E2eeUnavailableException) {
            config.logger.w("Cloud backup unavailable, retrying restore credential creation without cloud backup", e)
            try {
                val response = createRestoreCredential(input, customizer, isCloudBackupEnabled = false)
                config.logger.d("Restore credential created successfully without cloud backup")
                Result.success(response)
            } catch (fallbackError: Exception) {
                currentCoroutineContext().ensureActive()
                config.logger.e("Failed to create restore credential", fallbackError)
                Result.failure(fallbackError)
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            config.logger.e("Failed to create restore credential", e)
            Result.failure(e)
        }
    }

    private suspend fun createRestoreCredential(
        input: JsonObject,
        customizer: RestoreCredentialCreationCustomizer,
        isCloudBackupEnabled: Boolean
    ): JsonObject {
        val credentialManager = CredentialManager.create(ContextProvider.context)
        val request = customizer.customizer(
            CreateRestoreCredentialRequest(input.toString(), isCloudBackupEnabled)
        )
        val result = credentialManager.createCredential(
            context = ContextProvider.currentActivity,
            request = request
        )
        return when (result) {
            is CreateRestoreCredentialResponse ->
                Json.parseToJsonElement(result.responseJson).jsonObject

            else -> throw IllegalStateException("Unexpected result type: ${result::class.simpleName}")
        }
    }

    /**
     * Retrieves the restore credential and signs the user in, without any user interaction.
     *
     * Call this on first launch on a new device
     * (or the equivalent hook in the app's own `BackupAgent`) for background restoration.
     *
     * @param input the [PublicKeyCredentialRequestOptionsJSON](https://w3c.github.io/webauthn/#dictdef-publickeycredentialrequestoptionsjson)
     *              returned by the app server
     * @return a [Result] containing the authentication response as a [JsonObject] on success,
     *         or an exception on failure (for example [androidx.credentials.exceptions.NoCredentialException]
     *         if no restore credential exists on the device)
     */
    suspend fun signIn(
        input: JsonObject,
        block: RestoreCredentialRetrievalCustomizer.() -> Unit = {}
    ): Result<JsonObject> {
        val customizer = RestoreCredentialRetrievalCustomizer().apply(block)
        config.logger.d("Signing in with restore credential")
        return try {
            val credentialManager = CredentialManager.create(ContextProvider.context)
            val option = customizer.customizer(GetRestoreCredentialOption(input.toString()))
            val result = credentialManager.getCredential(
                context = ContextProvider.currentActivity,
                request = GetCredentialRequest(listOf(option))
            )
            when (val credential = result.credential) {
                is RestoreCredential -> {
                    config.logger.d("Successfully signed in with restore credential")
                    Result.success(
                        Json.parseToJsonElement(credential.authenticationResponseJson).jsonObject
                    )
                }

                else -> throw IllegalStateException("Unexpected result type: ${credential::class.simpleName}")
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            config.logger.w("Failed to sign in with restore credential", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes the restore credential from the device and its cloud backup.
     *
     * Call this whenever the user signs out - locally or as a result of a server-side session
     * invalidation - so the next app launch on this device does not silently sign the user
     * back in. `CredentialManager` does not delete restore credentials automatically.
     *
     * @return a [Result] indicating success, or an exception on failure
     */
    suspend fun clear(): Result<Unit> {
        config.logger.d("Clearing restore credential state")
        return try {
            val credentialManager = CredentialManager.create(ContextProvider.context)
            credentialManager.clearCredentialState(
                ClearCredentialStateRequest(ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL)
            )
            config.logger.d("Restore credential state cleared")
            Result.success(Unit)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            config.logger.e("Failed to clear restore credential state", e)
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Factory method to create a [RestoreCredentialClient] with customizable configuration.
         *
         * @param block Configuration lambda that receives a [RestoreCredentialClientConfig]
         *             instance for customization. Defaults to empty configuration.
         * @return A configured RestoreCredentialClient instance ready for use
         */
        operator fun invoke(block: RestoreCredentialClientConfig.() -> Unit = {}): RestoreCredentialClient {
            val config = RestoreCredentialClientConfig()
            config.apply(block)
            return RestoreCredentialClient(config)
        }
    }
}

/**
 * Customizer for restore credential creation requests.
 *
 * Allows overriding the [CreateRestoreCredentialRequest] built from the app server's
 * response before it is passed to [androidx.credentials.CredentialManager].
 */
class RestoreCredentialCreationCustomizer {
    internal var customizer: (CreateRestoreCredentialRequest) -> CreateRestoreCredentialRequest = { it }

    fun onCreateRestoreCredentialRequest(block: (CreateRestoreCredentialRequest) -> CreateRestoreCredentialRequest) {
        customizer = block
    }
}

/**
 * Customizer for restore credential retrieval requests.
 *
 * Allows overriding the [GetRestoreCredentialOption] built from the app server's response
 * before it is passed to [androidx.credentials.CredentialManager]. Note that a
 * [GetRestoreCredentialOption] can never be combined with any other
 * [androidx.credentials.CredentialOption] - only a single option is ever passed to
 * [androidx.credentials.GetCredentialRequest].
 */
class RestoreCredentialRetrievalCustomizer {
    internal var customizer: (GetRestoreCredentialOption) -> GetRestoreCredentialOption = { it }

    fun onGetRestoreCredentialOption(block: (GetRestoreCredentialOption) -> GetRestoreCredentialOption) {
        customizer = block
    }
}

/**
 * Configuration class for [RestoreCredentialClient] instances.
 */
@PingDsl
class RestoreCredentialClientConfig {
    /**
     * Logger instance for debugging and monitoring restore credential operations.
     *
     * **Default**: Uses the global [Logger.logger] instance.
     */
    var logger: Logger = Logger.logger
}
