/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.id

import android.annotation.SuppressLint
import android.provider.Settings
import com.pingidentity.android.ContextProvider

@SuppressLint("HardwareIds")
object DefaultDeviceIdentifier: DeviceIdentifier {
    override val id: String by lazy {
        "MyDeviceId"
        /*
        Settings.Secure.getString(
            ContextProvider.context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
         */
    }

}