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
import android.os.Build
import android.os.Build.BRAND
import android.os.Build.DEVICE
import android.os.Build.HARDWARE
import android.os.Build.MANUFACTURER
import android.os.Build.MODEL
import com.google.android.gms.location.LocationServices
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.profile.LocationRequestActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Internal manager for handling location permission request results.
 *
 * This object coordinates between the location collector and the permission request activity,
 * using a [CompletableDeferred] to suspend execution until permission results are available.
 */
internal object PermissionResultManager {
    /**
     * Deferred object that will be completed when the permission request activity finishes.
     *
     * This is set by the LocationCollector before launching the permission request activity
     * and completed by the LocationRequestActivity when the user responds to the permission dialog.
     */
    var permissionResultDeferred: CompletableDeferred<Boolean>? = null
}

/**
 * Interface for providing Android Build properties in a testable way.
 *
 * This abstraction allows for easier unit testing by enabling dependency injection
 * of Build property access rather than directly accessing static Build fields.
 * It provides access to key device identification properties from [Build] class.
 *
 * **Testing Benefits:**
 * - Enables mocking of Build properties during unit tests
 * - Allows testing with different device configurations
 * - Prevents tests from being dependent on actual device properties
 *
 * **Usage:**
 * ```kotlin
 * class MyCollector(private val buildProvider: AndroidBuildProvider = DefaultAndroidBuildProvider()) {
 *     fun getDeviceInfo() = buildProvider.getHardware()
 * }
 * ```
 *
 * @see DefaultAndroidBuildProvider for the production implementation
 * @see Build for the underlying Android Build class
 */
interface AndroidBuildProvider {
    /**
     * Returns the hardware identifier string.
     *
     * @return The hardware name from [Build.HARDWARE], or null if not available
     * @see Build.HARDWARE
     */
    fun getHardware(): String? = null

    /**
     * Returns the device manufacturer name.
     *
     * @return The manufacturer name from [Build.MANUFACTURER], or null if not available
     * @see Build.MANUFACTURER
     */
    fun getManufacturer(): String? = null

    /**
     * Returns the device identifier.
     *
     * @return The device identifier from [Build.DEVICE], or null if not available
     * @see Build.DEVICE
     */
    fun getDevice(): String? = null
}

/**
 * Default implementation of [AndroidBuildProvider] that returns actual Android Build properties.
 *
 * This production implementation directly accesses the Android [Build] class to retrieve
 * device information. It provides the real device properties for use in production code.
 *
 * **Implementation Notes:**
 * - Currently implements only [getHardware] and [getManufacturer] methods
 * - Other method ([getDevice]) use default interface implementations (return null)
 * - This partial implementation can be extended as needed for additional Build properties
 *
 * **Thread Safety:**
 * This class is thread-safe as it only accesses static final fields from the Build class.
 *
 * @see AndroidBuildProvider for the interface definition
 * @see Build for the underlying Android system properties
 */
class DefaultAndroidBuildProvider : AndroidBuildProvider {
    /**
     * Returns the actual device hardware identifier from Android Build properties.
     *
     * @return The hardware identifier string from [Build.HARDWARE], or null if not set
     */
    override fun getHardware(): String? {
        return Build.HARDWARE
    }

    /**
     * Returns the actual device manufacturer from Android Build properties.
     *
     * @return The manufacturer name from [Build.MANUFACTURER], or null if not set
     */
    override fun getManufacturer(): String? {
        return Build.MANUFACTURER
    }
}

/**
 * A device collector that gathers GPS location information from the device.
 *
 * This collector attempts to retrieve the device's last known location using Google Play Services.
 * It handles location permission requests automatically by launching a permission request activity
 * when necessary. The collection process is asynchronous and may prompt the user for permissions.
 *
 * **Required Permissions:**
 * - [Manifest.permission.ACCESS_FINE_LOCATION] or [Manifest.permission.ACCESS_COARSE_LOCATION]
 *
 * **Privacy Note:** Location data is sensitive personal information. Ensure proper user consent
 * and privacy policy disclosure before collecting location data.
 *
 * @see LocationInfo for the data structure containing latitude and longitude coordinates
 */
class LocationCollector : DeviceCollector<LocationInfo> {
    override val key: String
        get() = "location"

    /**
     * Collects the device's current location information asynchronously.
     *
     * This method first checks for location permissions. If permissions are not granted,
     * it launches a permission request activity and suspends until the user responds.
     * If permissions are granted, it retrieves the last known location from the
     * fused location provider.
     *
     * @return [LocationInfo] containing latitude and longitude if successful, null if:
     *   - Location permissions are denied by the user
     *   - Location services are disabled
     *   - No location data is available
     *   - An error occurs during location retrieval
     */
    @SuppressLint("MissingPermission")
    override suspend fun collect(): LocationInfo? {
        return getLocation()
    }

    override val serializer: KSerializer<LocationInfo>
        get() = LocationInfo.serializer()
}

/**
 * Internal function that handles the location retrieval process.
 *
 * This function manages the complete flow of location collection including permission checking,
 * permission requesting via activity launch, and actual location retrieval using the
 * Google Play Services fused location provider.
 *
 * @return [LocationInfo] with coordinates if successful, null if any step fails
 */
@SuppressLint("MissingPermission")
private suspend fun getLocation(): LocationInfo? {
    val context = ContextProvider.context

    val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) {
        // Create a deferred object to wait for the result.
        val deferred = CompletableDeferred<Boolean>()
        PermissionResultManager.permissionResultDeferred = deferred

        try {
            // Start the permission request activity
            val intent = LocationRequestActivity.createIntent(context)
            context.startActivity(intent)

            // Suspend and wait for the Activity to complete the deferred.
            // Add a timeout to prevent indefinite waiting if something goes wrong
            val isGranted = kotlinx.coroutines.withTimeoutOrNull(30000) { // 30 second timeout
                deferred.await()
            } ?: false // If timeout occurs, treat as permission denied

            // If permission was denied, stop and return null.
            if (!isGranted) {
                return null
            }
            // If granted, the function will continue below.
        } catch (e: Exception) {
            // Handle any exceptions during permission request
            return null
        } finally {
            // Clean up the deferred reference
            PermissionResultManager.permissionResultDeferred = null
        }
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

/**
 * Data class containing GPS location coordinates.
 *
 * @property latitude The latitude coordinate in decimal degrees, null if unavailable
 * @property longitude The longitude coordinate in decimal degrees, null if unavailable
 *
 * Both coordinates use the WGS84 datum as provided by Android's location services.
 * Positive latitude values indicate north of the equator, negative values indicate south.
 * Positive longitude values indicate east of the prime meridian, negative values indicate west.
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LocationInfo(val latitude: Double?, val longitude: Double?)