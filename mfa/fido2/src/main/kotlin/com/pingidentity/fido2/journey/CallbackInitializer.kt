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
 * It registers both registration and authentication callback types.
 */
class CallbackInitializer : Initializer<CallbackRegistry> {

    /**
     * Creates and configures the CallbackRegistry with FIDO2 callback registrations.
     *
     * This method is called automatically during app startup and registers:
     * - Fido2RegistrationCallback for handling FIDO2 credential registration
     * - Fido2AuthenticationCallback for handling FIDO2 authentication
     *
     * @param context The application context
     * @return The configured CallbackRegistry instance
     */
    override fun create(context: Context): CallbackRegistry {
        CallbackRegistry.register(
            "Fido2RegistrationCallback",
            ::Fido2RegistrationCallback
        )
        CallbackRegistry.register(
            "Fido2AuthenticationCallback",
            ::Fido2AuthenticationCallback
        )
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
