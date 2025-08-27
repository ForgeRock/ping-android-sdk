/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.pingidentity.android.ContextProvider
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

class LocationCollector : DeviceCollector<LocationInfo> {
    override val key: String
        get() = "location"

    @SuppressLint("MissingPermission")
    override suspend fun collect(): LocationInfo? {
        return getLocation()
    }

    override val serializer: KSerializer<LocationInfo>
        get() = LocationInfo.serializer()
}

@SuppressLint("MissingPermission")
private suspend fun getLocation(): LocationInfo? {
    val context = ContextProvider.context

    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }

    val locationClient = LocationServices.getFusedLocationProviderClient(context)

    return try {
        val location: Location? = locationClient.lastLocation.await()
        location?.let {
            LocationInfo(
                latitude = it.latitude,
                longitude = it.longitude
            )
        }
    } catch (_: Exception) {
        null
    }
}

@Serializable
data class LocationInfo(val latitude: Double?, val longitude: Double?)