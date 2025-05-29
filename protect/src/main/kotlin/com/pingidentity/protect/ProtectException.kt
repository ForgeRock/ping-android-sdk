/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect

/**
 * Exception class for handling errors in the Protect library.
 *
 * @param message The detail message for the exception.
 */
class ProtectException(message: String?) : Exception(message)