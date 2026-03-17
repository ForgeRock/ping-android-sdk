/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Exported authenticator data from Legacy SDK.
 * This represents the complete migration payload with mechanisms containing nested account data.
 */
@Serializable
data class LegacyExportedData(
    val mechanisms: List<LegacyMechanism>,
    val metadata: LegacyExportMetadata
)

/**
 * Metadata about exported authenticator data.
 */
@Serializable
data class LegacyExportMetadata(
    val totalMechanisms: Int,
    val exportedAt: Long = System.currentTimeMillis()
)

/**
 * Legacy mechanism with nested account data.
 * Supports both OATH and Push mechanisms.
 */
@Serializable
data class LegacyMechanism(
    val id: String,
    val issuer: String,
    val accountName: String,
    val mechanismUID: String,
    val secret: String,
    val type: String,

    // OATH-specific fields
    val oathType: String? = null,
    val algorithm: String? = null,
    val digits: Int? = null,
    val period: Int? = null,
    val counter: Long? = null,

    // Push-specific fields
    val registrationEndpoint: String? = null,
    val authenticationEndpoint: String? = null,
    val platform: String? = null,

    // Common optional fields
    val uid: String? = null,
    val resourceId: String? = null,
    val timeAdded: Long? = null,

    // Nested account data
    val account: LegacyAccount? = null
)

/**
 * Legacy account data nested within mechanisms.
 */
@Serializable
data class LegacyAccount(
    val id: String,
    val issuer: String,
    val displayIssuer: String? = null,
    val accountName: String,
    val displayAccountName: String? = null,
    val imageURL: String? = null,
    val backgroundColor: String? = null,
    val timeAdded: Long? = null,
    val policies: String? = null,
    val lockingPolicy: String? = null,
    val lock: Boolean = false
)


/**
 * JSON serializer for legacy data with lenient parsing.
 */
val legacyJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
    encodeDefaults = false
    explicitNulls = false
}
