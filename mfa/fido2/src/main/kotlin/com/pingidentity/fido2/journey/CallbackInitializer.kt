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
     * This method is called automatically during app startup and registers:
     * - Fido2RegistrationCallback for handling FIDO2 credential registration
     * - Either Fido2AuthenticationClientCallback (Google Play Services) or
     *   Fido2AuthenticationCredentialCallback (Credential Manager) for authentication
     *
     * The authentication callback selection follows this priority:
     * 1. If Google Play Services FIDO is available: Use Fido2AuthenticationClientCallback
     * 2. Otherwise: Use Fido2AuthenticationCredentialCallback (Credential Manager)
     *
     * This approach ensures broader device compatibility by preferring the more established
     * Google Play Services API when available, while falling back to the newer Credential
     * Manager API on devices where Google Play Services FIDO is not present.
     *
     * @param context The application context
     * @return The configured CallbackRegistry instance
     */
    override fun create(context: Context): CallbackRegistry {
        CallbackRegistry.register("Fido2RegistrationCallback", ::Fido2RegistrationCallback)

        // Check if Google Play Services FIDO is available for broader device compatibility
        val authenticationCallback = try {
            Class.forName("com.google.android.gms.fido.Fido")
            ::Fido2AuthenticationClientCallback
        } catch (e: ClassNotFoundException) {
            // Fall back to Credential Manager implementation
            ::Fido2AuthenticationCredentialCallback
        }

        CallbackRegistry.register("Fido2AuthenticationCallback", authenticationCallback)
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
