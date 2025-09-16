/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.journey

import kotlinx.serialization.json.JsonObject

/**
 * Interface for FIDO2 authentication callbacks in Journey workflows.
 *
 * This interface defines the contract for FIDO2 authentication operations within
 * Journey workflows. The actual implementation is selected at runtime based on
 * device capabilities and available APIs:
 *
 * - **Fido2AuthenticationClientCallback**: Uses Google Play Services FIDO2 API
 *   for device-bound credentials. Provides broader device compatibility.
 *
 * - **Fido2AuthenticationCredentialCallback**: Uses Android Credential Manager API
 *   for discoverable credentials (passkeys). Supports cross-device synchronization.
 *
 * The implementation selection is handled automatically by the CallbackInitializer
 * based on runtime API availability detection.
 */
interface Fido2AuthenticationCallback {
    /**
     * Performs FIDO2 authentication using available device authenticators.
     *
     * This method initiates the FIDO2 authentication ceremony, prompting the user
     * to verify their identity using biometrics, PIN, or other verification methods
     * supported by the authenticator.
     *
     * @return A [Result] containing the authentication response as a [JsonObject] on success,
     *         or an exception on failure
     */
    suspend fun authenticate() : Result<JsonObject>
}