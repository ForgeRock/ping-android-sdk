/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding

import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType
import kotlinx.serialization.Serializable

/**
 * Represents metadata for a user's cryptographic key stored on the device.
 *
 * This data class encapsulates all the essential information about a user's device-bound
 * cryptographic key, including identification, authentication requirements, and creation
 * timestamps. It serves as a persistent record that allows the system to:
 *
 * - Locate and identify specific keys for cryptographic operations
 * - Determine the appropriate authentication method for key access
 * - Track key lifecycle and creation history
 * - Support user interfaces that display available keys
 * - Enable key selection when multiple keys exist for a user
 *
 * The UserKey is serializable and designed for storage in secure local databases,
 * enabling offline access to key metadata while the actual private keys remain
 * securely stored in the Android KeyStore.
 *
 * Example usage:
 * ```kotlin
 * val userKey = UserKey(
 *     id = "device-key-123",
 *     userId = "user-456",
 *     userName = "john.doe@example.com",
 *     kid = "rsa-key-789",
 *     authType = DeviceBindingAuthenticationType.BIOMETRIC_ALLOW_FALLBACK
 * )
 * ```
 *
 * @see DeviceBindingAuthenticationType
 * @see CryptoKey
 * @see UserKeysStorage
 *
 * @param id The unique identifier for this specific user key instance.
 *           This ID is used internally by the SDK to correlate key metadata
 *           with actual cryptographic keys stored in the Android KeyStore.
 *           Must be unique across all user keys on the device.
 *
 * @param userId The unique identifier of the user who owns this key.
 *               This corresponds to the user ID provided during device binding
 *               and is used to associate keys with specific user accounts.
 *               Multiple keys can exist for the same user with different authentication types.
 *
 * @param userName The human-readable username or email address associated with this key.
 *                 This value is used for display purposes in user interfaces,
 *                 such as key selection dialogs or account management screens.
 *                 It provides users with recognizable information about key ownership.
 *
 * @param kid The key identifier used in JWT headers and cryptographic operations.
 *            This value is included in the "kid" (Key ID) field of JWT headers
 *            when signing tokens, allowing servers to identify which key was used
 *            for signature verification. Must be unique within the authentication context.
 *
 * @param authType The authentication method required to access this key's private key.
 *                 Determines what type of user verification (biometric, PIN, none, etc.)
 *                 must be completed before the key can be used for cryptographic operations.
 *                 This value is set during device binding and cannot be changed afterward.
 *
 * @param createdAt The timestamp (in milliseconds since Unix epoch) when this key was created.
 *                  Defaults to the current system time when the UserKey instance is created.
 *                  Used for key lifecycle management, sorting, and auditing purposes.
 *                  Can be used to identify the most recently created keys or implement
 *                  key rotation policies.
 */
@Serializable
data class UserKey (
    val id: String,
    val userId: String,
    val userName: String,
    val kid: String,
    val authType: DeviceBindingAuthenticationType,
    val createdAt: Long = System.currentTimeMillis()
)
