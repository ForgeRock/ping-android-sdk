/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.mfa.commons.model.MfaOathCredential
import org.json.JSONObject
import java.util.Date
import java.util.UUID

/**
 * OathCredential represents an OATH (TOTP/HOTP) credential.
 *
 * @property id Unique identifier for the credential (local ID).
 * @property userId User identifier on the server.
 * @property resourceId Server-side device identifier.
 * @property issuer The name of the issuer for this credential.
 * @property displayIssuer The name of the issuer for this credential, editable by the user.
 * @property accountName The account name (username) associated with this credential.
 * @property displayAccountName The account name (username) associated with this credential, editable by the user.
 * @property oathType The type of credential (TOTP or HOTP).
 * @property secret The shared secret key used to generate OTP codes.
 * @property oathAlgorithm The HMAC algorithm used (SHA1, SHA256, SHA512).
 * @property digits The number of digits in the generated codes.
 * @property period For TOTP, the time period in seconds for which a code is valid.
 * @property counter For HOTP, the counter value used to generate the next code.
 * @property createdAt The timestamp when this credential was created.
 * @property imageURL Optional URL for the issuer's logo or image.
 * @property backgroundColor Optional background color for the credential.
 * @property policies Optional Authenticator Policies in a JSON String format for the credential.
 * @property lockingPolicy Optional lName of the Policy locking the credential.
 * @property isLocked Indicates whether the credential is locked.
 */
data class OathCredential(
    override val id: String = UUID.randomUUID().toString(),
    override val userId: String = "",
    override val resourceId: String = "",
    override val issuer: String,
    override val displayIssuer: String,
    override val accountName: String,
    override val displayAccountName: String,
    val oathType: OathType,
    override val secret: String,
    val oathAlgorithm: OathAlgorithm = OathAlgorithm.SHA1,
    override val digits: Int = 6,
    override val period: Int = 30,
    override val counter: Long = 0L,
    override val createdAt: Date = Date(),
    override val imageURL: String? = null,
    override val backgroundColor: String? = null,
    override val policies: String? = null,
    override val lockingPolicy: String? = null,
    override val isLocked: Boolean = false
) : MfaOathCredential {

    /**
     * The type of credential (TOTP or HOTP) as String.
     */
    override val type: String
        get() = oathType.name

    /**
     * The HMAC algorithm used as String.
     */
    override val algorithm: String
        get() = oathAlgorithm.name
    
    companion object {
        /**
         * Create an OathCredential from a URI.
         * Format: otpauth://totp/Issuer:AccountName?secret=SECRET&issuer=Issuer&algorithm=SHA1&digits=6&period=30
         * Format: otpauth://hotp/Issuer:AccountName?secret=SECRET&issuer=Issuer&algorithm=SHA1&digits=6&counter=0
         *
         * @param uri The URI string.
         * @return An OathCredential.
         * @throws IllegalArgumentException if the URI is invalid.
         */
        @JvmStatic
        fun fromUri(uri: String): OathCredential {
            return OathUriParser.parse(uri)
        }
    }
    
    /**
     * Convert this credential to a URI string.
     *
     * @return A URI string for this credential.
     */
    override fun toUri(): String {
        return OathUriParser.format(this)
    }

    /**
     * Converts this credential to a JSON string representation.
     *
     * @return A JSON string representing this credential.
     */
    override fun toJson(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("userId", userId)
        json.put("resourceId", resourceId)
        json.put("issuer", issuer)
        json.put("displayIssuer", displayIssuer)
        json.put("accountName", accountName)
        json.put("displayAccountName", displayAccountName)
        json.put("type", type)
        json.put("secret", secret)
        json.put("algorithm", algorithm)
        json.put("digits", digits)
        json.put("period", period)
        json.put("counter", counter)
        json.put("createdAt", createdAt.time)
        json.put("isLocked", isLocked)

        // Handle optional fields
        imageURL?.let { json.put("imageURL", it) }
        backgroundColor?.let { json.put("backgroundColor", it) }
        policies?.let { json.put("policies", it) }
        lockingPolicy?.let { json.put("lockingPolicy", it) }

        return json.toString()
    }

    /**
     * Returns a string representation of this credential.
     *
     * @return String representation of this credential.
     */
    override fun toString(): String {
        return "OathCredential(id='$id', issuer='$issuer', displayIssuer='$displayIssuer', " +
               "accountName='$accountName', displayAccountName='$displayAccountName', " +
               "type=$oathType, userId='$userId', resourceId='$resourceId', " +
               "algorithm=$oathAlgorithm, digits=$digits, period=$period, counter=$counter, " +
               "createdAt=$createdAt, imageURL=$imageURL, backgroundColor=$backgroundColor, " +
               "policies=$policies, lockingPolicy=$lockingPolicy, isLocked=$isLocked)"
    }
}