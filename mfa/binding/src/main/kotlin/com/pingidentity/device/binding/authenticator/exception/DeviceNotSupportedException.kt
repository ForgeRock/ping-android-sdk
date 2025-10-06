/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator.exception

/**
 * Exception thrown when the device does not support required authentication features.
 *
 * This exception indicates that the device lacks necessary hardware or software capabilities
 * for the requested authentication method. Common scenarios include:
 * - Device does not have biometric sensors
 * - Device does not support the required biometric types
 * - Device does not have a secure keystore
 *
 * @param message The error message describing which feature is not supported
 * @param cause The underlying cause of the exception, if any
 */
class DeviceNotSupportedException(
    message: String = "Device not supported",
    cause: Throwable? = null
) : Exception(message, cause)