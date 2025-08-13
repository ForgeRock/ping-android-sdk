/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.id

import android.provider.Settings
import com.pingidentity.android.ContextProvider

/**
 * The AndroidIDDeviceIdentifier class provides a way to retrieve the Android ID of the device.
 * This ID is unique to each device and is used as a device identifier.
 *
 * Note: The Android ID is a 64-bit number (as a hex string) that is randomly generated when the user
 * first sets up the device and should remain constant for the lifetime of the user's device. However,
 * there are a few limitations:
 * - The value may change if a factory reset is performed
 * - For devices with multiple users, each user has their own Android ID
 * - For apps installed in an Android work profile, the Android ID value is different from the personal profile
 *
 * @see Settings.Secure.ANDROID_ID
 */
object AndroidIDDeviceIdentifier : DeviceIdentifier {
    /**
     * Returns the device's Android ID.
     *
     * @return A unique string representing the device's Android ID
     */
    override val id: suspend () -> String = {
        catch {
            Settings.Secure.getString(
                ContextProvider.context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: throw IllegalStateException("Unable to retrieve Android ID from Settings.Secure.ANDROID_ID")
        }
    }
}