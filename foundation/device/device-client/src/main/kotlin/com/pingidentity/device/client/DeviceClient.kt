/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.client

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.utils.PingDsl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Configuration for the DeviceClient.
 *
 * @property context The Android application context.
 * @property storage The storage implementation for local device persistence.
 * @property repository Optional repository for server synchronization.
 * @property logger Logger for debugging and diagnostics.
 * @property autoSync Whether to automatically sync with the server on operations.
 */
@PingDsl
data class DeviceClientConfig(
    var context: Context,
    var storage: DeviceStorage? = null,
    var repository: DeviceRepository? = null,
    var logger: Logger = Logger.STANDARD,
    var autoSync: Boolean = false
)

/**
 * DeviceClient is a Kotlin-based API client that manages device-related CRUD operations
 * with Ping Identity's authentication system.
 *
 * It provides a unified interface to interact with multiple device types:
 * - OATH devices (TOTP/HOTP credentials)
 * - Push devices (push notification credentials)
 * - Bound devices (device binding with cryptographic keys)
 * - Profile devices (device characteristics and metadata)
 * - WebAuthn devices (FIDO2/passkey credentials)
 *
 * Features:
 * - Local storage with caching for offline access
 * - Optional server synchronization via DeviceRepository
 * - Type-safe CRUD operations with Kotlin Result API
 * - Coroutine-based async operations
 * - Comprehensive error handling
 *
 * Usage:
 * ```kotlin
 * // Initialize with default storage
 * val client = DeviceClient.create(applicationContext) {
 *     logger = Logger.STANDARD
 * }
 *
 * // Create a device
 * val device = OathDevice(
 *     id = UUID.randomUUID().toString(),
 *     issuer = "Example",
 *     accountName = "user@example.com",
 *     oathType = "TOTP",
 *     secret = "BASE32SECRET"
 * )
 *
 * client.create(device).onSuccess { created ->
 *     println("Device created: ${created.id}")
 * }
 *
 * // Retrieve devices
 * client.getAll().onSuccess { devices ->
 *     devices.forEach { println(it.id) }
 * }
 *
 * // Update a device (for supported types)
 * client.update(device).onSuccess { updated ->
 *     println("Device updated")
 * }
 *
 * // Delete a device
 * client.delete(device.id).onSuccess { deleted ->
 *     println("Device deleted: $deleted")
 * }
 * ```
 *
 * @property config The configuration for this DeviceClient.
 */
class DeviceClient internal constructor(
    private val config: DeviceClientConfig
) {
    private val logger: Logger = config.logger
    private val storage: DeviceStorage = config.storage ?: InMemoryDeviceStorage()
    private val repository: DeviceRepository? = config.repository
    private val autoSync: Boolean = config.autoSync

    private var isInitialized = false

    companion object {
        /**
         * Create a DeviceClient instance with a customizable configuration block.
         *
         * @param context The Android application context.
         * @param block The configuration block to customize the client configuration.
         * @return An initialized DeviceClient instance.
         */
        suspend fun create(
            context: Context,
            block: DeviceClientConfig.() -> Unit = {}
        ): DeviceClient {
            val config = DeviceClientConfig(context = context).apply(block)
            return DeviceClient(config).apply {
                initialize()
            }
        }
    }

    /**
     * Initialize the DeviceClient.
     * This method initializes the storage and must be called before any other operations.
     *
     * @throws DeviceException if initialization fails.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            storage.initialize()
            isInitialized = true
            logger.d("DeviceClient initialized successfully")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to initialize DeviceClient: ${e.message}", e)
            throw DeviceException("Failed to initialize DeviceClient", e)
        }
    }

    /**
     * Create a new device.
     * This saves the device locally and optionally syncs with the server.
     *
     * @param device The device to create.
     * @return A Result containing the created device or an exception.
     */
    suspend fun create(device: Device): Result<Device> = withContext(Dispatchers.IO) {
        try {
            checkInitialized()

            // Save locally first
            storage.save(device)
            logger.d("Device created locally: ${device.id}")

            // Optionally sync with server
            return@withContext if (autoSync && repository != null) {
                try {
                    val serverDevice = repository.create(device)
                    logger.d("Device created on server: ${serverDevice.id}")
                    // Update local storage with server response
                    storage.save(serverDevice)
                    Result.success(serverDevice)
                } catch (e: Exception) {
                    coroutineContext.ensureActive()
                    logger.w("Failed to sync device creation with server: ${e.message}", e)
                    // Return the locally created device even if server sync fails
                    Result.success(device)
                }
            } else {
                Result.success(device)
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to create device: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieve a device by its ID.
     *
     * @param id The unique identifier of the device.
     * @return A Result containing the device if found, null otherwise, or an exception.
     */
    suspend fun get(id: String): Result<Device?> = withContext(Dispatchers.IO) {
        try {
            checkInitialized()
            val device = storage.get(id)
            logger.d("Retrieved device: $id, found: ${device != null}")
            Result.success(device)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get device: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieve all devices.
     *
     * @return A Result containing a list of all devices or an exception.
     */
    suspend fun getAll(): Result<List<Device>> = withContext(Dispatchers.IO) {
        try {
            checkInitialized()
            val devices = storage.getAll()
            logger.d("Retrieved ${devices.size} devices")
            Result.success(devices)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get all devices: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieve all devices of a specific type.
     *
     * @param type The type of devices to retrieve.
     * @return A Result containing a list of devices or an exception.
     */
    suspend fun getByType(type: DeviceType): Result<List<Device>> = withContext(Dispatchers.IO) {
        try {
            checkInitialized()
            val devices = storage.getByType(type)
            logger.d("Retrieved ${devices.size} devices of type $type")
            Result.success(devices)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get devices by type: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieve all devices associated with a specific user.
     *
     * @param userId The user identifier.
     * @return A Result containing a list of devices or an exception.
     */
    suspend fun getByUserId(userId: String): Result<List<Device>> = withContext(Dispatchers.IO) {
        try {
            checkInitialized()
            val devices = storage.getByUserId(userId)
            logger.d("Retrieved ${devices.size} devices for user $userId")
            Result.success(devices)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get devices by user: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update an existing device.
     * Note: Not all device types support updates. Check the device type before calling.
     *
     * @param device The device to update.
     * @return A Result containing the updated device or an exception.
     */
    suspend fun update(device: Device): Result<Device> = withContext(Dispatchers.IO) {
        try {
            checkInitialized()

            // Check if device exists
            val existing = storage.get(device.id)
            if (existing == null) {
                logger.w("Device not found for update: ${device.id}")
                return@withContext Result.failure(DeviceNotFoundException("Device not found: ${device.id}"))
            }

            // Save locally
            storage.save(device)
            logger.d("Device updated locally: ${device.id}")

            // Optionally sync with server
            return@withContext if (autoSync && repository != null) {
                try {
                    val serverDevice = repository.update(device)
                    logger.d("Device updated on server: ${serverDevice.id}")
                    storage.save(serverDevice)
                    Result.success(serverDevice)
                } catch (e: Exception) {
                    coroutineContext.ensureActive()
                    logger.w("Failed to sync device update with server: ${e.message}", e)
                    Result.success(device)
                }
            } else {
                Result.success(device)
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to update device: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a device by its ID.
     *
     * @param id The unique identifier of the device to delete.
     * @return A Result containing true if deleted, false otherwise, or an exception.
     */
    suspend fun delete(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            checkInitialized()

            val deleted = storage.delete(id)
            logger.d("Device deleted locally: $id, success: $deleted")

            // Optionally sync with server
            if (deleted && autoSync && repository != null) {
                try {
                    repository.delete(id)
                    logger.d("Device deleted on server: $id")
                } catch (e: Exception) {
                    coroutineContext.ensureActive()
                    logger.w("Failed to sync device deletion with server: ${e.message}", e)
                }
            }

            Result.success(deleted)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to delete device: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete all devices of a specific type.
     *
     * @param type The type of devices to delete.
     * @return A Result containing the number of devices deleted or an exception.
     */
    suspend fun deleteByType(type: DeviceType): Result<Int> = withContext(Dispatchers.IO) {
        try {
            checkInitialized()
            val count = storage.deleteByType(type)
            logger.d("Deleted $count devices of type $type")
            Result.success(count)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to delete devices by type: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete all devices associated with a specific user.
     *
     * @param userId The user identifier.
     * @return A Result containing the number of devices deleted or an exception.
     */
    suspend fun deleteByUserId(userId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            checkInitialized()
            val count = storage.deleteByUserId(userId)
            logger.d("Deleted $count devices for user $userId")
            Result.success(count)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to delete devices by user: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Clear all devices from local storage.
     *
     * @return A Result indicating success or an exception.
     */
    suspend fun clear(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkInitialized()
            storage.clear()
            logger.d("All devices cleared from storage")
            Result.success(Unit)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to clear devices: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Synchronize local devices with the server.
     * This fetches all devices from the server and merges them with local storage.
     *
     * @return A Result containing the list of devices after sync or an exception.
     */
    suspend fun sync(): Result<List<Device>> = withContext(Dispatchers.IO) {
        try {
            checkInitialized()

            if (repository == null) {
                logger.w("Repository not configured, cannot sync")
                return@withContext Result.failure(
                    DeviceException("Repository not configured for synchronization")
                )
            }

            val serverDevices = repository.sync()
            logger.d("Fetched ${serverDevices.size} devices from server")

            // Merge with local storage
            storage.saveAll(serverDevices)
            logger.d("Merged server devices with local storage")

            Result.success(serverDevices)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to sync devices: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Close the DeviceClient and release resources.
     */
    suspend fun close() = withContext(Dispatchers.IO) {
        try {
            storage.close()
            isInitialized = false
            logger.d("DeviceClient closed")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.w("Error closing DeviceClient: ${e.message}", e)
        }
    }

    /**
     * Check if the client is initialized.
     *
     * @throws DeviceClientNotInitializedException if not initialized.
     */
    private fun checkInitialized() {
        if (!isInitialized) {
            throw DeviceClientNotInitializedException()
        }
    }
}

