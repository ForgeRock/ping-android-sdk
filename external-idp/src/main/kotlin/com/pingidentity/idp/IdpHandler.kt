/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp

/**
 * Interface for handling authentication with external Identity Providers (IdPs).
 *
 * This interface defines the contract for implementations that manage authentication
 * flows with third-party identity providers such as Google, Facebook, Apple, etc.
 * Each implementation is responsible for:
 *
 * 1. Launching the appropriate authentication UI or flow
 * 2. Handling the authentication response
 * 3. Extracting tokens and other relevant information
 * 4. Returning a standardized result
 *
 * Implementations should handle all provider-specific details internally,
 * including error handling, token validation, and user cancellation.
 */
interface IdpHandler {
    /**
     * The token type used by the Identity Provider.
     *
     * This value is included when submitting the authentication result back to the
     * authentication server. Common values include:
     * - "authorization_code" - For OAuth 2.0 authorization codes
     * - "id_token" - For OpenID Connect ID tokens
     * - "access_token" - For OAuth 2.0 access tokens
     */
    var tokenType: String

    /**
     * Initiates and completes the authorization flow with the identity provider.
     *
     * This suspending function will:
     * 1. Configure the authorization request using the provided [idpClient]
     * 2. Launch the appropriate UI for user authentication
     * 3. Process the authentication response
     * 4. Return the authentication result
     *
     * Implementations should handle all provider-specific details, including
     * error scenarios and user cancellation.
     *
     * @param idpClient The client configuration containing parameters required for authorization
     * @return [IdpResult] containing the authentication token and any additional parameters
     * @throws Exception if the authentication fails or is cancelled
     */
    suspend fun authorize(idpClient: IdpClient): IdpResult

}