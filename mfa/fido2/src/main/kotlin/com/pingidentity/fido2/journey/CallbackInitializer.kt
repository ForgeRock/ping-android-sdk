/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.journey

import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.journey.plugin.CallbackRegistry

/**
 * Initializer class responsible for registering FIDO2 callbacks with the Journey framework.
 *
 * This class is part of the Android App Startup library initialization process and
 * automatically registers FIDO2-related callbacks when the application starts.
 * It performs runtime detection of available FIDO2 APIs and selects the appropriate
 * implementation based on device capabilities.
 */
class CallbackInitializer : Initializer<CallbackRegistry> {

    /**
     * Creates and configures the CallbackRegistry with FIDO2 callback registrations.
     *
     * This method is called automatically during app startup and registers the FIDO2 callbacks
     * with the Journey framework. It registers:
     * - **Fido2RegistrationCallback**: For handling FIDO2 credential registration operations
     * - **Fido2AuthenticationCallback**: For handling FIDO2 authentication operations
     *
     * @param context The application context used for callback registration
     * @return The configured CallbackRegistry instance with FIDO2 callbacks registered
     */
    override fun create(context: Context): CallbackRegistry {
        // Register the FIDO2 registration callback
        CallbackRegistry.register("Fido2RegistrationCallback", ::Fido2RegistrationCallback)

        // Register the unified FIDO2 authentication callback
        // This implementation automatically selects the best available API at runtime
        CallbackRegistry.register("Fido2AuthenticationCallback", ::Fido2AuthenticationCallback)

        return CallbackRegistry
    }

    /**
     * Returns the list of initializer dependencies.
     *
     * @return An empty list as this initializer has no dependencies
     */
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}
