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
import com.pingidentity.device.profile.LocationRequestActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

internal object PermissionResultManager {
    // This deferred will be used to wait for the permission result.
    var permissionResultDeferred: CompletableDeferred<Boolean>? = null
}
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

    val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) {
        // Create a deferred object to wait for the result.
        val deferred = CompletableDeferred<Boolean>()
        PermissionResultManager.permissionResultDeferred = deferred

        // Start the app's activity. Add the NEW_TASK flag since we're calling from a non-Activity context.
        val intent = LocationRequestActivity.createIntent(context)
        context.startActivity(intent)

        // Suspend and wait for the Activity to complete the deferred.
        val isGranted = deferred.await()

        // If permission was denied, stop and return null.
        if (!isGranted) {
            return null
        }
        // If granted, the function will continue below.
    }

    val locationClient = LocationServices.getFusedLocationProviderClient(context)
    return try {
        val location: Location? = locationClient.lastLocation.await()
        location?.let {
            LocationInfo(latitude = it.latitude, longitude = it.longitude)
        }
    } catch (_: Exception) {
        null
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LocationInfo(val latitude: Double?, val longitude: Double?)