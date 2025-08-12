/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresPermission
import com.pingidentity.android.ContextProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

class NetworkCollector : DeviceCollector<NetworkInfo> {
    override val key: String
        get() = "network"

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override suspend fun collect(): NetworkInfo {
        return NetworkInfo(connected = isConnected())
    }

    override val serializer: KSerializer<NetworkInfo>
        get() = NetworkInfo.serializer()
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
private fun isConnected(): Boolean {
    val connectivityManager = ContextProvider.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    // For Android 10 (API 29) and above
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    // For older versions
    else {
        // activeNetworkInfo is deprecated in API 29
        @Suppress("DEPRECATION")
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }
}

@Serializable
data class NetworkInfo(val connected: Boolean)