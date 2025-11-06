/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.client

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of DeviceStorage for testing and simple use cases.
 * This implementation stores devices in memory and does not persist them.
 * All data is lost when the application is closed.
 *
 * This is useful for:
 * - Testing and development
 * - Temporary device storage
 * - Prototyping without persistence requirements
 *
 * For production use cases, implement a persistent storage solution
 * (e.g., using SharedPreferences, SQLite, or Room).
 */
class InMemoryDeviceStorage : DeviceStorage {
    private val devices = mutableMapOf<String, Device>()
    private val mutex = Mutex()

    override suspend fun initialize() {
        // No initialization needed for in-memory storage
    }

    override suspend fun save(device: Device) = mutex.withLock {
        devices[device.id] = device
    }

    override suspend fun saveAll(devices: List<Device>) = mutex.withLock {
        devices.forEach { device ->
            this.devices[device.id] = device
        }
    }

    override suspend fun get(id: String): Device? = mutex.withLock {
        devices[id]
    }

    override suspend fun getByType(type: DeviceType): List<Device> = mutex.withLock {
        devices.values.filter { it.type == type }
    }

    override suspend fun getByUserId(userId: String): List<Device> = mutex.withLock {
        devices.values.filter { it.userId == userId }
    }

    override suspend fun getAll(): List<Device> = mutex.withLock {
        devices.values.toList()
    }

    override suspend fun delete(id: String): Boolean = mutex.withLock {
        devices.remove(id) != null
    }

    override suspend fun deleteByType(type: DeviceType): Int = mutex.withLock {
        val toDelete = devices.values.filter { it.type == type }
        toDelete.forEach { devices.remove(it.id) }
        toDelete.size
    }

    override suspend fun deleteByUserId(userId: String): Int = mutex.withLock {
        val toDelete = devices.values.filter { it.userId == userId }
        toDelete.forEach { devices.remove(it.id) }
        toDelete.size
    }

    override suspend fun clear() = mutex.withLock {
        devices.clear()
    }

    override suspend fun close() {
        // No cleanup needed for in-memory storage
    }
}

