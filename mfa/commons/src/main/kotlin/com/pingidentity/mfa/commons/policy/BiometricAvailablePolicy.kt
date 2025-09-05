/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.policy

import android.content.Context
import androidx.biometric.BiometricManager

/**
 * Policy that checks if biometric authentication is available on the device.
 * 
 * This policy evaluates whether the device has biometric capabilities and
 * if they are properly configured for authentication.
 * 
 * JSON format: {"biometricAvailable": {}}
 */
class BiometricAvailablePolicy : MfaPolicy() {
    
    companion object {
        const val POLICY_NAME = "biometricAvailable"
    }
    
    override fun getName(): String {
        return POLICY_NAME
    }
    
    override fun evaluate(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    // Biometric authentication is available and working
                    true
                }
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                    // No biometric credentials are enrolled, but hardware is available
                    // Consider this as non-compliant since user hasn't set up biometrics
                    false
                }
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                    // No biometric hardware available
                    false
                }
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                    // Biometric hardware is unavailable at this time
                    false
                }
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                    // Security update required for biometric authentication
                    false
                }
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                    // Biometric authentication is not supported on this device
                    false
                }
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                    // Status unknown, consider as non-compliant for security
                    false
                }
                else -> {
                    // Any other status, consider as non-compliant
                    false
                }
            }
        } catch (_: Exception) {
            // If there's an exception checking biometric status, consider as non-compliant
            false
        }
    }
}