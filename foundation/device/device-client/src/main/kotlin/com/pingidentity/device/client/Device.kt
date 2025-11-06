/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.client

import kotlinx.serialization.Serializable
import java.util.Date

/**
 * Represents a unified device registered with Ping Identity's authentication system.
 * This sealed interface provides a common abstraction for different device types
 * while maintaining type safety and extensibility.
 *
 * Each device type (OATH, Push, Bound, Profile, WebAuthn) extends this interface
 * with their specific properties while sharing common identification and metadata fields.
 */
sealed interface Device {
    /**
     * Unique identifier for the device (local ID).
     * This is typically a UUID generated locally when the device is registered.
     */
    val id: String

    /**
     * User identifier on the server.
     * This associates the device with a specific user account.
     */
    val userId: String?

    /**
     * Server-side device identifier (resource ID).
     * This is the identifier used by the server to reference this device.
     * May differ from the local ID depending on the device type.
     */
    val resourceId: String?

    /**
     * The type of device.
     */
    val type: DeviceType

    /**
     * The timestamp when this device was created/registered.
     */
    val createdAt: Date

    /**
     * Human-readable metadata about the device.
     * This can contain issuer, account name, or other display information.
     */
    val metadata: Map<String, String>
}

/**
 * Enum representing the different types of devices that can be registered
 * with Ping Identity's authentication system.
 */
enum class DeviceType {
    /**
     * OATH (TOTP/HOTP) device for generating one-time passwords.
     */
    OATH,

    /**
     * Push notification device for receiving authentication challenges.
     */
    PUSH,

    /**
     * Device binding with cryptographic keys for passwordless authentication.
     */
    BOUND,

    /**
     * Device profile containing device characteristics and metadata.
     */
    PROFILE,

    /**
     * WebAuthn/FIDO2 device for passkey-based authentication.
     */
    WEBAUTHN
}

/**
 * Represents an OATH device (TOTP/HOTP) for generating one-time passwords.
 *
 * @property id Unique identifier for the device (local ID).
 * @property userId User identifier on the server.
 * @property resourceId Server-side device identifier.
 * @property issuer The name of the service issuing this credential.
 * @property accountName The account name associated with this device.
 * @property oathType The type of OATH credential (TOTP or HOTP).
 * @property secret The shared secret key for generating codes.
 * @property algorithm The HMAC algorithm used (SHA1, SHA256, SHA512).
 * @property digits The number of digits in generated codes.
 * @property period For TOTP, the time period in seconds for code validity.
 * @property counter For HOTP, the counter value for the next code.
 * @property createdAt The timestamp when this device was created.
 * @property imageURL Optional URL for the issuer's logo.
 * @property backgroundColor Optional background color for UI display.
 * @property metadata Additional metadata about the device.
 */
@Serializable
data class OathDevice(
    override val id: String,
    override val userId: String? = null,
    override val resourceId: String? = null,
    val issuer: String,
    val accountName: String,
    val oathType: String, // "TOTP" or "HOTP"
    val secret: String,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
    val counter: Long = 0L,
    @Serializable(with = DateSerializer::class)
    override val createdAt: Date = Date(),
    val imageURL: String? = null,
    val backgroundColor: String? = null,
    override val metadata: Map<String, String> = mapOf(
        "issuer" to issuer,
        "accountName" to accountName,
        "type" to oathType
    )
) : Device {
    override val type: DeviceType = DeviceType.OATH
}

/**
 * Represents a Push notification device for receiving authentication challenges.
 *
 * @property id Unique identifier for the device (local ID).
 * @property userId User identifier on the server.
 * @property resourceId Server-side device identifier.
 * @property issuer The name of the service issuing this credential.
 * @property accountName The account name associated with this device.
 * @property serverEndpoint The endpoint for authentication responses.
 * @property sharedSecret The secret key for cryptographic operations.
 * @property platform The platform for this credential (e.g., PING_AM, PING_ONE).
 * @property createdAt The timestamp when this device was created.
 * @property imageURL Optional URL for the issuer's logo.
 * @property backgroundColor Optional background color for UI display.
 * @property metadata Additional metadata about the device.
 */
@Serializable
data class PushDevice(
    override val id: String,
    override val userId: String? = null,
    override val resourceId: String? = null,
    val issuer: String,
    val accountName: String,
    val serverEndpoint: String,
    val sharedSecret: String,
    val platform: String = "PING_AM",
    @Serializable(with = DateSerializer::class)
    override val createdAt: Date = Date(),
    val imageURL: String? = null,
    val backgroundColor: String? = null,
    override val metadata: Map<String, String> = mapOf(
        "issuer" to issuer,
        "accountName" to accountName,
        "platform" to platform
    )
) : Device {
    override val type: DeviceType = DeviceType.PUSH
}

/**
 * Represents a device binding with cryptographic keys for passwordless authentication.
 *
 * @property id Unique identifier for the device (local ID).
 * @property userId User identifier on the server.
 * @property resourceId Server-side device identifier (key ID).
 * @property userName The human-readable username.
 * @property kid The key identifier used in JWT headers.
 * @property authType The authentication method required to access the key.
 * @property createdAt The timestamp when this device was created.
 * @property metadata Additional metadata about the device.
 */
@Serializable
data class BoundDevice(
    override val id: String,
    override val userId: String,
    override val resourceId: String, // kid
    val userName: String,
    val kid: String,
    val authType: String, // Authentication type (e.g., "BIOMETRIC")
    @Serializable(with = DateSerializer::class)
    override val createdAt: Date = Date(),
    override val metadata: Map<String, String> = mapOf(
        "userId" to userId,
        "userName" to userName,
        "kid" to kid,
        "authType" to authType
    )
) : Device {
    override val type: DeviceType = DeviceType.BOUND
}

/**
 * Represents a device profile containing device characteristics and metadata.
 *
 * @property id Unique identifier for the device profile.
 * @property userId User identifier associated with this profile.
 * @property resourceId Server-side device identifier.
 * @property deviceName The device model name.
 * @property deviceManufacturer The device manufacturer.
 * @property platform The platform (e.g., "Android").
 * @property platformVersion The OS version.
 * @property createdAt The timestamp when this profile was created.
 * @property metadata Additional device metadata.
 */
@Serializable
data class ProfileDevice(
    override val id: String,
    override val userId: String? = null,
    override val resourceId: String? = null,
    val deviceName: String,
    val deviceManufacturer: String,
    val platform: String,
    val platformVersion: String,
    @Serializable(with = DateSerializer::class)
    override val createdAt: Date = Date(),
    override val metadata: Map<String, String> = mapOf(
        "deviceName" to deviceName,
        "manufacturer" to deviceManufacturer,
        "platform" to platform,
        "version" to platformVersion
    )
) : Device {
    override val type: DeviceType = DeviceType.PROFILE
}

/**
 * Represents a WebAuthn/FIDO2 device (passkey) for biometric/passwordless authentication.
 *
 * @property id Unique identifier for the device (credential ID).
 * @property userId User identifier on the server.
 * @property resourceId Server-side device identifier.
 * @property rpId The relying party identifier.
 * @property userHandle The user handle for this credential.
 * @property userName The human-readable username.
 * @property credentialId The credential ID (base64url encoded).
 * @property createdAt The timestamp when this credential was created.
 * @property metadata Additional metadata about the device.
 */
@Serializable
data class WebAuthnDevice(
    override val id: String,
    override val userId: String? = null,
    override val resourceId: String? = null,
    val rpId: String,
    val userHandle: String? = null,
    val userName: String? = null,
    val credentialId: String,
    @Serializable(with = DateSerializer::class)
    override val createdAt: Date = Date(),
    override val metadata: Map<String, String> = mapOf(
        "rpId" to rpId,
        "credentialId" to credentialId
    ) + (userName?.let { mapOf("userName" to it) } ?: emptyMap())
) : Device {
    override val type: DeviceType = DeviceType.WEBAUTHN
}

