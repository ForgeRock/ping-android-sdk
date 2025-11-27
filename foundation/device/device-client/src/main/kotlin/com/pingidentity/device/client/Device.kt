/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.client

import android.annotation.SuppressLint
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonObject

/**
 * Abstract base class for all device types.
 * All device models must extend this class.
 */
@Serializable
abstract class Device {
    abstract val id: String
    abstract val deviceName: String
    abstract val urlSuffix: String
}

/**
 * Interface for device operations.
 * Supports fetching, updating and deleting devices.
 */
interface DeviceRepository<T> {
    /**
     * Fetch all devices of type [T] encapsulated in a [Result].
     */
    suspend fun devices(): Result<List<T>>
    /**
     * Delete a device of type [T].
     */
    suspend fun delete(device: T): Result<T>
    /**
     * Update a device of type [T].
     */
    suspend fun update(device: T): Result<T>
}

/**
 * Data class for Bound devices.
 */
@Serializable
@JsonIgnoreUnknownKeys
@OptIn(ExperimentalSerializationApi::class)
data class BoundDevice(
    @SerialName("_id")
    override val id: String,
    override var deviceName: String,
    val deviceId: String,
    val uuid: String,
    val createdDate: Long,
    val lastAccessDate: Long
) : Device() {
    override var urlSuffix: String = "devices/2fa/binding"
}

/**
 * Data class for OATH devices.
 */
@Serializable
@JsonIgnoreUnknownKeys
@OptIn(ExperimentalSerializationApi::class)
data class OathDevice(
    @SerialName("_id")
    override val id: String,
    override var deviceName: String,
    val uuid: String,
    val createdDate: Long,
    val lastAccessDate: Long
) : Device() {
    override var urlSuffix: String = "devices/2fa/oath"
}

/**
 * Data class for Push devices.
 */
@Serializable
@JsonIgnoreUnknownKeys
@OptIn(ExperimentalSerializationApi::class)
data class PushDevice(
    @SerialName("_id")
    override val id: String,
    override var deviceName: String,
    val uuid: String,
    val createdDate: Long,
    val lastAccessDate: Long
) : Device() {
    override var urlSuffix: String = "devices/2fa/push"
}

/**
 * Data class for WebAuthn devices.
 */
@Serializable
@JsonIgnoreUnknownKeys
@OptIn(ExperimentalSerializationApi::class)
data class WebAuthnDevice(
    @SerialName("_id")
    override val id: String,
    override var deviceName: String,
    val uuid: String,
    val credentialId: String,
    val createdDate: Long,
    val lastAccessDate: Long
) : Device() {
    override var urlSuffix: String = "devices/2fa/webauthn"
}

/**
 * Data class for Profile devices.
 */
@Serializable
@JsonIgnoreUnknownKeys
@OptIn(ExperimentalSerializationApi::class)
data class ProfileDevice(
    @SerialName("_id")
    override val id: String,
    @SerialName("alias")
    override var deviceName: String, // alias
    val identifier: String,
    val metadata: JsonObject,
    val location: Location? = null,
    val lastSelectedDate: Long
) : Device() {
    override var urlSuffix: String = "devices/profile"
}

/**
 * Data class for device location.
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Location(val latitude: Double, val longitude: Double)
