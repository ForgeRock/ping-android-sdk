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

    // Platform-specific providers with default Android implementations
    private var settingsProvider: PlatformSettingsProvider = AndroidPlatformSettingsProvider()
    private var buildInfoProvider: PlatformBuildInfoProvider = AndroidPlatformBuildInfoProvider()

    /**
     * Sets a custom logger for this utility class. If not provided, the default logger is used.
     *
     * @param logger The custom logger to use.
     */
    fun setLogger(logger: Logger) {
        _logger = logger
    }

    /**
     * Gets the user-friendly name of the device.
     *
     * @param context The application context.
     * @return The device name, or a default value if the name cannot be determined.
     */
    fun getDeviceName(context: Context): String {
        val deviceName = getDeviceNameFromSettings(context)
            ?: getDeviceNameFromBuild()
            ?: "Unknown Android Device"
        return deviceName
    }

    /**
     * Gets the device name from the Android Settings.
     *
     * @param context The application context.
     * @return The device name from Settings, or null if not found.
     */
    private fun getDeviceNameFromSettings(context: Context): String? {
        return try {
            settingsProvider.getDeviceNameFromSettings(context)
        } catch (e: Exception) {
            _logger.w("Error getting device name from Settings: ${e.message}")
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
            val manufacturer = buildInfoProvider.getManufacturer()
            val model = buildInfoProvider.getModel()

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
    internal fun capitalize(str: String): String {
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

    /**
     * For testing only - allows injecting mock providers
     */
    internal fun setProviders(
        settingsProvider: PlatformSettingsProvider,
        buildInfoProvider: PlatformBuildInfoProvider
    ) {
        this.settingsProvider = settingsProvider
        this.buildInfoProvider = buildInfoProvider
    }

    /**
     * For testing only - reset providers to default implementations
     */
    internal fun resetProviders() {
        settingsProvider = AndroidPlatformSettingsProvider()
        buildInfoProvider = AndroidPlatformBuildInfoProvider()
    }
}

/**
 * Interfaces for platform-specific functionality
 */
internal interface PlatformSettingsProvider {
    fun getDeviceNameFromSettings(context: Context): String?
}

/**
 * Interface for platform-specific build information
 */
internal interface PlatformBuildInfoProvider {
    fun getManufacturer(): String
    fun getModel(): String
}

/**
 * Default implementation that uses Android's actual Settings
 */
internal class AndroidPlatformSettingsProvider : PlatformSettingsProvider {
    override fun getDeviceNameFromSettings(context: Context): String? {
        return try {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Default implementation that uses Android's Build properties
 */
internal class AndroidPlatformBuildInfoProvider : PlatformBuildInfoProvider {
    override fun getManufacturer(): String = Build.MANUFACTURER
    override fun getModel(): String = Build.MODEL
}