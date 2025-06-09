/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.model

import java.util.Date

/**
 * Interface defining the OATH credential properties.
 * This interface is used to avoid circular dependencies between modules.
 */
interface MfaOathCredential {
    /**
     * Unique identifier for the credential (local ID).
     */
    val id: String
    
    /**
     * User identifier on the server.
     */
    val userId: String
    
    /**
     * Server-side device identifier.
     */
    val resourceId: String
    
    /**
     * The name of the issuer for this credential.
     */
    val issuer: String
    
    /**
     * The name of the issuer for this credential, editable by the user.
     */
    val displayIssuer: String
    
    /**
     * The account name (username) associated with this credential.
     */
    val accountName: String
    
    /**
     * The account name (username) associated with this credential, editable by the user.
     */
    val displayAccountName: String
    
    /**
     * The type of credential (TOTP or HOTP).
     */
    val type: String
    
    /**
     * The shared secret key used to generate OTP codes.
     */
    val secret: String
    
    /**
     * The HMAC algorithm used (SHA1, SHA256, SHA512).
     */
    val algorithm: String
    
    /**
     * The number of digits in the generated codes.
     */
    val digits: Int
    
    /**
     * For TOTP, the time period in seconds for which a code is valid.
     */
    val period: Int
    
    /**
     * For HOTP, the counter value used to generate the next code.
     */
    val counter: Long
    
    /**
     * The timestamp when this credential was created.
     */
    val createdAt: Date
    
    /**
     * Optional URL for the issuer's logo or image.
     */
    val imageURL: String?
    
    /**
     * Optional background color for the credential.
     */
    val backgroundColor: String?
    
    /**
     * Optional Authenticator Policies in a JSON String format for the credential.
     */
    val policies: String?
    
    /**
     * Optional Name of the Policy locking the credential.
     */
    val lockingPolicy: String?
    
    /**
     * Indicates whether the credential is locked.
     */
    val isLocked: Boolean
    
    /**
     * Convert this credential to a URI string.
     *
     * @return A URI string for this credential.
     */
    fun toUri(): String

    /**
     * Convert this credential to a JSON string representation.
     *
     * @return A JSON string representing this credential.
     */
    fun toJson(): String
}