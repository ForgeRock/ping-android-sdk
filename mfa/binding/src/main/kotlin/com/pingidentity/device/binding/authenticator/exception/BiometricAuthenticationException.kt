/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator.exception

/**
 * Custom exception for biometric authentication errors.
 *
 * This exception wraps biometric authentication error codes and messages
 * to provide consistent error handling across the application.
 *
 * @param errorCode The biometric error code from BiometricPrompt
 * @param errorMessage The human-readable error message
 */
class BiometricAuthenticationException(
    val errorCode: Int,
    val errorMessage: String
) : Exception("Biometric authentication failed: $errorMessage (code: $errorCode)")