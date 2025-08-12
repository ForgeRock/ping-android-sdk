/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.content.Context
import android.telephony.TelephonyManager
import com.pingidentity.android.ContextProvider
import kotlinx.serialization.Serializable

val TelephonyCollector by lazy {
    DeviceCollector<TelephonyInfo>(key = "telephony") {
        try {
            val telephonyManager =
                ContextProvider.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            TelephonyInfo(
                networkCountryIso = telephonyManager.networkCountryIso,
                carrierName = telephonyManager.networkOperatorName,
            )
        } catch (_: UnsupportedOperationException) {
            null
        }
    }
}

@Serializable
data class TelephonyInfo(val networkCountryIso: String?, val carrierName: String?)