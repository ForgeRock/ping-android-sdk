/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.journey

import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.journey.plugin.CallbackRegistry

/**
 * Initializer for registering device binding and signing callbacks with the Journey framework.
 *
 * This class is responsible for automatically registering device binding-related callbacks
 * during application startup using the AndroidX Startup library. It ensures that
 * DeviceBindingCallback and DeviceSigningVerifierCallback are available for use
 * in Journey authentication flows.
 *
 * The initialization happens automatically when the application starts, eliminating
 * the need for manual callback registration in application code.
 *
 * @see androidx.startup.Initializer
 * @see com.pingidentity.journey.plugin.CallbackRegistry
 */
class CallbackInitializer : Initializer<CallbackRegistry> {

    /**
     * Creates and configures the CallbackRegistry with device binding callbacks.
     *
     * This method is called automatically by the AndroidX Startup library during
     * application initialization. It registers the following callbacks:
     * - DeviceBindingCallback: Handles device binding operations in Journey flows
     * - DeviceSigningVerifierCallback: Handles device signing verification operations
     *
     * @param context The Android application context (unused in this implementation)
     * @return The configured CallbackRegistry instance with registered callbacks
     */
    override fun create(context: Context): CallbackRegistry {
        CallbackRegistry.register("DeviceBindingCallback", ::DeviceBindingCallback)
        CallbackRegistry.register("DeviceSigningVerifierCallback", ::DeviceSigningVerifierCallback)
        return CallbackRegistry
    }

    /**
     * Specifies the dependencies required before this initializer can run.
     *
     * This initializer has no dependencies and can run immediately during
     * application startup without waiting for other initializers to complete.
     *
     * @return An empty list indicating no dependencies are required
     */
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}