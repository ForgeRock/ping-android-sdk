/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.pingidentity.logger.Logger
import java.util.Locale

/**
 * Utility class for retrieving device information, including the device name.
 */
object DeviceUtils {

    // Default logger instance
    private var _logger = Logger.logger
    
    /**
     * Sets a custom logger for this utility class. If not provided, the default logger is used.
     * 
     * @param logger The custom logger to use.
     */
    @JvmStatic
    fun setLogger(logger: Logger) {
        _logger = logger
    }

    /**
     * Gets the user-friendly name of the device.
     *
     * @param context The application context.
     * @return The device name, or a default value if the name cannot be determined.
     */
    @JvmStatic
    fun getDeviceName(context: Context): String {
        // Use the provided logger if available, otherwise use the default
        var deviceName: String? = null
        
        // Try to get device name from settings
        deviceName = getDeviceNameFromSettings(context)

        // If not found, try from Build properties
        if (deviceName.isNullOrBlank()) {
            deviceName = getDeviceNameFromBuild()
        }

        // If still not found, use a default value
        if (deviceName.isNullOrBlank()) {
            deviceName = "Unknown Android Device"
        }

        return deviceName
    }

    /**
     * Gets the device name from the system settings.
     *
     * @param context The application context.
     * @return The device name from settings, or null if not found.
     */
    private fun getDeviceNameFromSettings(context: Context): String? {
        return try {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        } catch (e: Exception) {
            _logger.w("Error getting device name from settings: ${e.message}")
            null
        }
    }

    /**
     * Gets the device name from the Build class.
     *
     * @return The device name from Build, or null if not found.
     */
    private fun getDeviceNameFromBuild(): String? {
        return try {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            
            if (model.lowercase(Locale.ROOT).startsWith(manufacturer.lowercase(Locale.ROOT))) {
                capitalize(model)
            } else {
                "${capitalize(manufacturer)} $model"
            }
        } catch (e: Exception) {
            _logger.w("Error getting device name from Build: ${e.message}")
            null
        }
    }

    /**
     * Capitalizes the first letter of a string.
     *
     * @param str The string to capitalize.
     * @return The capitalized string.
     */
    private fun capitalize(str: String): String {
        if (str.isEmpty()) {
            return str
        }
        
        val firstChar = str[0]
        return if (Character.isUpperCase(firstChar)) {
            str
        } else {
            str.replaceFirst(firstChar, firstChar.uppercaseChar())
        }
    }
}
