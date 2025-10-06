/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator.exception

/**
 * Exception thrown when attempting to use a device that has not been registered.
 *
 * This exception indicates that the device or credential does not exist in the system.
 * It may occur when:
 * - The device has never been registered
 * - The device registration was removed or deleted
 * - All biometric and device credentials were removed from the device, causing the private key to be deleted
 *
 * @param message The error message describing why the device is not registered
 * @param cause The underlying cause of the exception, if any
 */
class DeviceNotRegisteredException(
    message: String = "Device not registered",
    cause: Throwable? = null
) : Exception(message, cause)