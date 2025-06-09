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
interface MfaOathClient : MfaClient {
    
    /**
     * Creates an OATH Credential from a standard otpauth:// URI (typically from a QR code).
     *
     * @param uri The URI string in the format otpauth://totp/issuer:accountName?secret=SECRET&issuer=issuer&algorithm=SHA1&digits=6&period=30
     * @return The created OathCredential.
     * @throws MfaException if the credential cannot be created.
     */
    fun addCredentialFromUri(uri: String): OathCredential
    
    /**
     * Add a new OATH credential.
     *
     * @param credential The OathCredential to add.
     * @return The created OathCredential.
     * @throws MfaException if the credential cannot be created.
     */
    fun addCredential(credential: OathCredential): OathCredential
    
    /**
     * Get all OATH credentials.
     *
     * @return A list of all OathCredentials.
     * @throws MfaException if the credentials cannot be retrieved.
     */
    fun getCredentials(): List<OathCredential>
    
    /**
     * Get an OATH credential by ID.
     *
     * @param credentialId The ID of the credential to get.
     * @return The OathCredential, or null if not found.
     * @throws MfaException if the credential cannot be retrieved.
     */
    fun getCredential(credentialId: String): OathCredential?
    
    /**
     * Delete an OATH credential by ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return True if the credential was removed, false if it didn't exist.
     * @throws MfaException if the credential cannot be removed.
     */
    fun deleteCredential(credentialId: String): Boolean
    
    /**
     * Generate an OTP code for an OATH credential.
     *
     * @param credentialId The ID of the credential.
     * @return The OTP code.
     * @throws MfaException if the code cannot be generated.
     */
    fun generateCode(credentialId: String): String
    
    /**
     * Generate an OTP code for an OATH credential and get its time validity information.
     *
     * @param credentialId The ID of the credential.
     * @return The OTP code and validity information.
     * @throws MfaException if the code cannot be generated.
     */
    fun generateCodeWithValidity(credentialId: String): OathCodeInfo
}
