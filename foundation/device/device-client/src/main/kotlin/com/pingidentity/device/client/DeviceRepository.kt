/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.client

/**
 * Interface for communicating with a Device Management Server.
 * Implementations of this interface handle network operations for
 * synchronizing devices with remote servers.
 */
interface DeviceRepository {
    /**
     * Fetch a device from the server by its ID.
     *
     * @param id The unique identifier of the device.
     * @return The device if found, null otherwise.
     * @throws DeviceNetworkException if the network operation fails.
     */
    suspend fun fetch(id: String): Device?

    /**
     * Fetch all devices from the server.
     *
     * @return A list of all devices from the server.
     * @throws DeviceNetworkException if the network operation fails.
     */
    suspend fun fetchAll(): List<Device>

    /**
     * Fetch all devices of a specific type from the server.
     *
     * @param type The type of devices to fetch.
     * @return A list of devices of the specified type.
     * @throws DeviceNetworkException if the network operation fails.
     */
    suspend fun fetchByType(type: DeviceType): List<Device>

    /**
     * Fetch all devices associated with a specific user from the server.
     *
     * @param userId The user identifier.
     * @return A list of devices associated with the user.
     * @throws DeviceNetworkException if the network operation fails.
     */
    suspend fun fetchByUserId(userId: String): List<Device>

    /**
     * Create a new device on the server.
     *
     * @param device The device to create.
     * @return The created device (may include server-generated fields).
     * @throws DeviceNetworkException if the network operation fails.
     */
    suspend fun create(device: Device): Device

    /**
     * Update an existing device on the server.
     *
     * @param device The device to update.
     * @return The updated device.
     * @throws DeviceNetworkException if the network operation fails.
     */
    suspend fun update(device: Device): Device

    /**
     * Delete a device from the server.
     *
     * @param id The unique identifier of the device to delete.
     * @return true if the device was deleted, false otherwise.
     * @throws DeviceNetworkException if the network operation fails.
     */
    suspend fun delete(id: String): Boolean

    /**
     * Synchronize local devices with the server.
     * This operation fetches all devices from the server and returns them
     * for the caller to merge with local storage as needed.
     *
     * @return A list of devices from the server.
     * @throws DeviceNetworkException if the network operation fails.
     */
    suspend fun sync(): List<Device>
}

