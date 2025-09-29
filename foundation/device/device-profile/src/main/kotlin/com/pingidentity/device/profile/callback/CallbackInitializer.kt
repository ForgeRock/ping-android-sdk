/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.callback

import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.device.profile.DeviceProfileCallback
import com.pingidentity.journey.plugin.CallbackRegistry

/**
 * Initializer for registering device profile callbacks with the CallbackRegistry.
 *
 * This class implements the Android Jetpack App Startup Initializer interface to
 * automatically register the DeviceProfileCallback when the application starts.
 * The registration enables the callback to be used within the Ping Identity journey framework.
 */
class CallbackInitializer : Initializer<CallbackRegistry> {

    /**
     * Creates and configures the CallbackRegistry by registering the DeviceProfileCallback.
     *
     * This method is called automatically by the App Startup library during application
     * initialization. It registers the "DeviceProfileCallback" with its corresponding
     * factory function in the CallbackRegistry.
     *
     * @param context The application context
     * @return The configured CallbackRegistry instance
     */
    override fun create(context: Context): CallbackRegistry {
        CallbackRegistry.register("DeviceProfileCallback", ::DeviceProfileCallback)
        return CallbackRegistry
    }

    /**
     * Returns the list of dependencies required before this initializer can run.
     *
     * @return An empty list as this initializer has no dependencies
     */
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}