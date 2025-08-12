/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/**
 * Represents a device collector that collects data of type [T].
 *
 * @param T The type of data to be collected, which must be serializable.
 */
interface DeviceCollector<T : @Serializable Any> {
    val key: String
    suspend fun collect(): T?
    val serializer: KSerializer<T>
}

/**
 * Creates a [DeviceCollector] with the specified key and collection logic.
 *
 * @param key The unique key for the collector.
 * @param collect The suspend function that collects the data.
 * @return A new instance of [DeviceCollector].
 */
inline fun <reified T : @Serializable Any> DeviceCollector(
    key: String,
    noinline collect: suspend () -> T?
): DeviceCollector<T> {
    return object : DeviceCollector<T> {
        override val key = key
        override val serializer: KSerializer<T> = serializer()
        override suspend fun collect(): T? = collect()
    }
}