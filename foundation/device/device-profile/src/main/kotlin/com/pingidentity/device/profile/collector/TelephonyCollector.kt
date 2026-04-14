/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import com.pingidentity.android.ContextProvider
import kotlinx.serialization.Serializable

/**
 * A device collector that gathers telephony and carrier information from the Android device.
 *
 * This collector retrieves network and carrier details using the Android TelephonyManager.
 * It handles devices that may not support telephony services (like tablets) by catching
 * UnsupportedOperationException and returning null in such cases.
 *
 * **Required Permissions:**
 * No special permissions are required for basic network country and carrier name information.
 *
 * **Device Compatibility:**
 * - Smartphones: Full telephony information available
 * - Tablets/WiFi-only devices: May return null if telephony services are unavailable
 * - Emulators: May return mock or default values
 *
 * @see TelephonyInfo for the data structure containing carrier and network details
 */
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

/**
 * Data class containing telephony and carrier information.
 *
 * This class holds network and carrier details that can be used for network analysis,
 * fraud detection, and regional service customization.
 *
 * @property networkCountryIso The ISO 3166-1 alpha-2 country code of the current registered operator's MCC (Mobile Country Code).
 *   Returns null if not available or if the device doesn't support telephony.
 *   Examples: "US" for United States, "CA" for Canada, "GB" for United Kingdom
 *
 * @property carrierName The human-readable name of the current registered network operator.
 *   Returns null if not available or if the device doesn't support telephony.
 *   Examples: "Verizon", "AT&T", "Rogers", "Vodafone"
 *
 * ```kotlin
 * val telephonyInfo = TelephonyInfo(
 *     networkCountryIso = "US",
 *     carrierName = "Verizon"
 * )
 * ```
 *
 * @see TelephonyManager.getNetworkCountryIso
 * @see TelephonyManager.getNetworkOperatorName
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class TelephonyInfo(val networkCountryIso: String?, val carrierName: String?)