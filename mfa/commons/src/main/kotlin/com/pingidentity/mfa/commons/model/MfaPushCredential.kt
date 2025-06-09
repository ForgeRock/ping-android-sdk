/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.model

import java.util.Date

/**
 * Interface representing a Push credential.
 */
interface MfaPushCredential {
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
     * The endpoint URL for the push server.
     */
    val serverEndpoint: String
    
    /**
     * The shared secret for enhanced security.
     */
    val sharedSecret: String
    
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
     * Convert the credential to a JSON string representation.
     */
    fun toJson(): String
}
