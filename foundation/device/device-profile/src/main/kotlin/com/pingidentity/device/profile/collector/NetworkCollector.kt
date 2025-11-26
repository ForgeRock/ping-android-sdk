/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import com.pingidentity.android.ContextProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * A device collector that gathers network connectivity information from the Android device.
 *
 * This collector determines whether the device currently has an active internet connection
 * by checking network capabilities and connectivity status. It uses different approaches
 * based on the Android API level to ensure compatibility across device versions.
 *
 * **Required Permissions:**
 * - [Manifest.permission.ACCESS_NETWORK_STATE]
 *
 * **API Compatibility:**
 * - Android 10+ (API 29+): Uses [NetworkCapabilities] for modern network state checking
 *
 * @see NetworkInfo for the data structure containing connectivity status
 */
class NetworkCollector : DeviceCollector<NetworkInfo> {
    override val key: String
        get() = "network"

    /**
     * Collects the current network connectivity status of the device.
     *
     * This method checks if the device has an active internet connection by querying
     * the system's connectivity manager. The implementation varies based on Android
     * API level to ensure compatibility.
     *
     * @return [NetworkInfo] containing the current connectivity status
     * @throws SecurityException if [Manifest.permission.ACCESS_NETWORK_STATE] is not granted
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override suspend fun collect(): NetworkInfo {
        return NetworkInfo(connected = isConnected())
    }

    override val serializer: KSerializer<NetworkInfo>
        get() = NetworkInfo.serializer()

    /**
     * Determines if the device currently has an active internet connection.
     *
     * This function uses different approaches based on the Android API level:
     * - API 29+: Checks [NetworkCapabilities.NET_CAPABILITY_INTERNET] on the active network
     * - API 28 and below: Uses the deprecated [android.net.NetworkInfo.isConnected] method
     *
     * @return true if the device has an active internet connection, false otherwise
     * @throws SecurityException if [Manifest.permission.ACCESS_NETWORK_STATE] is not granted
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun isConnected(): Boolean {
        val connectivityManager = ContextProvider.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // For Android 10 (API 29) and above
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * Data class containing network connectivity information.
 *
 * @property connected true if the device has an active internet connection, false otherwise
 *
 * Note: This indicates network connectivity at the time of collection and may change
 * rapidly as the device moves between networks or experiences connectivity issues.
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class NetworkInfo(val connected: Boolean)