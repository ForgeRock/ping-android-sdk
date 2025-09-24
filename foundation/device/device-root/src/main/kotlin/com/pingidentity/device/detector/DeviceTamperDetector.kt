/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.detector

import android.content.Context
import com.pingidentity.logger.Logger

/**
 * Interface for detecting device tampering conditions such as rooting, jailbreaking, or other security compromises.
 *
 * Implementations of this interface should provide methods to detect various forms of device modification
 * that could potentially compromise the security of the application or user data.
 *
 * This interface is designed to be extensible, allowing for different detection strategies
 * and custom tamper detection logic based on specific security requirements.
 *
 * The interface uses a scoring system where detectors return confidence levels as Double values:
 * - `1.0` indicates high confidence that tampering is detected
 * - `0.0` indicates no tampering detected
 * - Values between 0.0 and 1.0 can indicate varying levels of suspicion
 *
 * @since 1.0
 */
interface TamperDetector {
    /**
     * Determines if the current device has been tampered with or compromised.
     *
     * This method performs security checks to detect if the device has been modified
     * in ways that could compromise security, such as:
     * - Root access on Android devices
     * - Modified system files
     * - Debugging tools or emulators
     * - Custom ROMs or firmware
     *
     * @param context The Android context used for performing security checks
     *
     * @return A confidence score where:
     *         - `1.0` indicates high confidence that tampering is detected
     *         - `0.0` indicates no tampering detected
     *         - Values between 0.0 and 1.0 indicate varying levels of suspicion
     * @throws SecurityException if security checks cannot be performed
     */
    suspend fun analyze(context: Context): Double
}

/**
 * Factory function for creating [TamperDetector] instances with custom detection logic.
 *
 * This inline function provides a convenient way to create TamperDetector implementations
 * using lambda expressions, allowing for quick prototyping and custom detection strategies.
 *
 * Example usage:
 * ```kotlin
 * val customDetector = TamperDetector {
 *     // Custom tamper detection logic returning confidence score
 *     if (checkRootAccess() || checkDebuggingTools()) 1.0 else 0.0
 * }
 * ```
 *
 * @param block A suspending lambda function that returns a confidence score (0.0 to 1.0)
 * @return A [TamperDetector] instance that executes the provided detection logic
 *
 * @since 1.0
 */
inline fun TamperDetector(
    crossinline block: suspend () -> Double
): TamperDetector {
    return object : TamperDetector, LoggerAware {

        override lateinit var logger: Logger

        override suspend fun analyze(context: Context): Double = block()
    }
}

/**
 * Interface for detectors that require logging capability.
 *
 * Implement this interface in detectors that need to log information, warnings, or errors
 * during tamper detection. The logger instance can be injected or set by the detection framework.
 */
interface LoggerAware {
    /**
     * Logger instance used for logging within the detector.
     */
    var logger: Logger
}