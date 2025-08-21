/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.mfa.commons.MfaClient
import com.pingidentity.mfa.commons.exception.MfaException

/**
 * Interface for OATH client functionality.
 * Extends the base MfaClient interface with OATH-specific functionality.
 */
interface OathMfaClient : MfaClient {
    
    /**
     * Creates an OATH Credential from a standard otpauth:// URI (typically from a QR code).
     *
     * @param uri The URI string in the format otpauth://totp/issuer:accountName?secret=SECRET&issuer=issuer&algorithm=SHA1&digits=6&period=30
     * @return A Result containing the created OathCredential or an Exception in case of failure.
     */
    suspend fun addCredentialFromUri(uri: String): Result<OathCredential>

    /**
     * Save an OATH credential.
     *
     * @param credential The OathCredential to add.
     * @return A Result containing the created OathCredential or an Exception in case of failure.
     */
    suspend fun saveCredential(credential: OathCredential): Result<OathCredential>

    /**
     * Get all OATH credentials.
     *
     * @return A Result containing a list of all OathCredentials or an Exception in case of failure.
     */
    suspend fun getCredentials(): Result<List<OathCredential>>

    /**
     * Get an OATH credential by ID.
     *
     * @param credentialId The ID of the credential to get.
     * @return A Result containing the OathCredential (or null if not found) or an Exception in case of failure.
     */
    suspend fun getCredential(credentialId: String): Result<OathCredential?>

    /**
     * Delete an OATH credential by ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    suspend fun deleteCredential(credentialId: String): Result<Boolean>

    /**
     * Generate an OTP code for an OATH credential.
     *
     * @param credentialId The ID of the credential.
     * @return A Result containing the OTP code or an Exception in case of failure.
     */
    suspend fun generateCode(credentialId: String): Result<String>

    /**
     * Generate an OTP code for an OATH credential and get its time validity information.
     *
     * @param credentialId The ID of the credential.
     * @return A Result containing the OTP code and validity information or an Exception in case of failure.
     */
    suspend fun generateCodeWithValidity(credentialId: String): Result<OathCodeInfo>
}