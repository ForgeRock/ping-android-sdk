/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.client

/**
 * Interface for managing device storage operations.
 * Implementations of this interface handle persistence of Device objects
 * in various storage backends (SharedPreferences, SQLite, Room, etc.).
 */
interface DeviceStorage {
    /**
     * Initialize the storage.
     * This method must be called before any other storage operations.
     *
     * @throws DeviceStorageException if initialization fails.
     */
    suspend fun initialize()

    /**
     * Save a device to storage.
     * If a device with the same ID already exists, it will be updated.
     *
     * @param device The device to save.
     * @throws DeviceStorageException if the save operation fails.
     */
    suspend fun save(device: Device)

    /**
     * Save multiple devices to storage.
     * This is a batch operation for efficiency.
     *
     * @param devices The list of devices to save.
     * @throws DeviceStorageException if the save operation fails.
     */
    suspend fun saveAll(devices: List<Device>)

    /**
     * Retrieve a device by its ID.
     *
     * @param id The unique identifier of the device.
     * @return The device if found, null otherwise.
     * @throws DeviceStorageException if the retrieval operation fails.
     */
    suspend fun get(id: String): Device?

    /**
     * Retrieve all devices of a specific type.
     *
     * @param type The type of devices to retrieve.
     * @return A list of devices of the specified type.
     * @throws DeviceStorageException if the retrieval operation fails.
     */
    suspend fun getByType(type: DeviceType): List<Device>

    /**
     * Retrieve all devices associated with a specific user.
     *
     * @param userId The user identifier.
     * @return A list of devices associated with the user.
     * @throws DeviceStorageException if the retrieval operation fails.
     */
    suspend fun getByUserId(userId: String): List<Device>

    /**
     * Retrieve all devices.
     *
     * @return A list of all devices in storage.
     * @throws DeviceStorageException if the retrieval operation fails.
     */
    suspend fun getAll(): List<Device>

    /**
     * Delete a device by its ID.
     *
     * @param id The unique identifier of the device to delete.
     * @return true if the device was deleted, false if it didn't exist.
     * @throws DeviceStorageException if the delete operation fails.
     */
    suspend fun delete(id: String): Boolean

    /**
     * Delete all devices of a specific type.
     *
     * @param type The type of devices to delete.
     * @return The number of devices deleted.
     * @throws DeviceStorageException if the delete operation fails.
     */
    suspend fun deleteByType(type: DeviceType): Int

    /**
     * Delete all devices associated with a specific user.
     *
     * @param userId The user identifier.
     * @return The number of devices deleted.
     * @throws DeviceStorageException if the delete operation fails.
     */
    suspend fun deleteByUserId(userId: String): Int

    /**
     * Delete all devices from storage.
     *
     * @throws DeviceStorageException if the clear operation fails.
     */
    suspend fun clear()

    /**
     * Close the storage and release any resources.
     * This method should be called when the storage is no longer needed.
     */
    suspend fun close()
}

