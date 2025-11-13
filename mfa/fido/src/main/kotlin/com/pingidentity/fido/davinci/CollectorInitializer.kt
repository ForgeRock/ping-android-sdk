/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido.davinci

import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.davinci.plugin.CollectorFactory

/**
 * Initializer class responsible for registering FIDO2 collectors with the DaVinci framework.
 *
 * This class is part of the Android App Startup library initialization process and
 * automatically registers the FIDO2 collector factory when the application starts.
 * The registered collector handles both FIDO2 registration and authentication workflows.
 */
class CollectorInitializer : Initializer<CollectorFactory> {

    /**
     * Creates and configures the CollectorFactory with FIDO2 collector registration.
     *
     * This method is called automatically during app startup and registers
     * the "FIDO2" collector type with the DaVinci framework.
     *
     * @param context The application context
     * @return The configured CollectorFactory instance
     */
    override fun create(context: Context): CollectorFactory {
        CollectorFactory.register("FIDO2", ::FidoCollector)
        return CollectorFactory
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