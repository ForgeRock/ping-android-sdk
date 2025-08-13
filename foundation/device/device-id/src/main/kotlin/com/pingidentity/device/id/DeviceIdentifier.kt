/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.id

import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Interface for obtaining a device-specific identifier.
 *
 * Implementations of this interface provide different strategies for generating
 * or retrieving identifiers that can uniquely identify a device. These identifiers
 * can be used for device recognition, fraud detection, or other security purposes.
 *
 * The SDK provides several implementations of this interface:
 * - [DefaultDeviceIdentifier]: Uses the Android KeyStore to generate a persistent identifier
 * - [AndroidIDDeviceIdentifier]: Uses the device's Android ID
 *
 * Note: When implementing this interface, consider privacy implications and
 * ensure compliance with relevant privacy regulations.
 */
interface DeviceIdentifier {
    /**
     * Returns a unique identifier for the device.
     *
     * @return A string representing a unique device identifier
     */
    val id: suspend () -> String

    companion object {
        private var id: DeviceIdentifier = DeviceIdentifierDelegate(DefaultDeviceIdentifier)

        /**
         * Global setting for the device identifier implementation.
         *
         * This variable determines which [DeviceIdentifier] strategy is used throughout the SDK.
         * It is not thread-safe and should be set only once during application initialization.
         * Changing this value at runtime will affect all consumers of [DeviceIdentifier].
         */
        var identifier: DeviceIdentifier
            get() = id
            set(value) {
                id = DeviceIdentifierDelegate(value)
            }

    }

}

/**
 * Exception thrown when there is an error retrieving a device identifier.
 *
 * @param message A description of what caused the error.
 * @param cause The underlying exception that caused this error, if any.
 */
class DeviceIdentifierException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Inline function to safely execute a device identifier retrieval function
 * and wrap any exceptions in a [DeviceIdentifierException].
 *
 * @param block The suspend function to execute safely
 * @return The result of the block execution
 * @throws DeviceIdentifierException if an exception occurs during execution
 */
suspend inline fun catch(crossinline block: suspend () -> String): String {
    return try {
        block()
    } catch (e: Exception) {
        coroutineContext.ensureActive()
        throw DeviceIdentifierException("Failed to retrieve device identifier", e)
    }
}
