/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.client

/**
 * Exception thrown when an error occurs during device operations.
 *
 * @property message A description of the error.
 * @property cause The underlying cause of the exception, if any.
 */
open class DeviceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when the DeviceClient is not properly initialized.
 *
 * @property message A description of the error.
 */
class DeviceClientNotInitializedException(
    message: String = "DeviceClient not initialized. Call initialize() first."
) : DeviceException(message)

/**
 * Exception thrown when a device is not found.
 *
 * @property message A description of the error.
 */
class DeviceNotFoundException(
    message: String
) : DeviceException(message)

/**
 * Exception thrown when device storage operations fail.
 *
 * @property message A description of the error.
 * @property cause The underlying cause of the exception.
 */
class DeviceStorageException(
    message: String,
    cause: Throwable? = null
) : DeviceException(message, cause)

/**
 * Exception thrown when device network operations fail.
 *
 * @property message A description of the error.
 * @property cause The underlying cause of the exception.
 */
class DeviceNetworkException(
    message: String,
    cause: Throwable? = null
) : DeviceException(message, cause)

