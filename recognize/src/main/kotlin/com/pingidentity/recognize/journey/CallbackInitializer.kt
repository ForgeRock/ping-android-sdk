/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import android.app.Application
import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.journey.plugin.CallbackRegistry
import io.keyless.sdk.Keyless

/**
 * Jetpack App Startup [Initializer] for the PingOne Recognize Journey integration.
 *
 * Registers [RecognizeCallback] in the [CallbackRegistry] under the well-known key
 * `"PingOneRecognizeCallback"` so that Journey nodes of that type are automatically
 * dispatched to the correct callback factory without any manual setup.
 *
 * Also calls [io.keyless.sdk.Keyless.initialize] with the application context, which is
 * required by the Keyless SDK before any biometric operation can be started.
 *
 * This class is declared in `AndroidManifest.xml` under the `androidx.startup.InitializationProvider`
 * so it runs automatically during [Application] startup — no explicit invocation is needed.
 */
class CallbackInitializer : Initializer<CallbackRegistry> {

    /**
     * Registers the [RecognizeCallback] factory and initialises the Keyless SDK.
     *
     * @param context Application [Context] provided by `androidx.startup`.
     * @return The [CallbackRegistry] singleton, as required by the [Initializer] contract.
     */
    override fun create(context: Context): CallbackRegistry {
        // Factory callback: dispatches to Enroll or Authenticate based on operationType
        CallbackRegistry.register(
            "PingOneRecognizeCallback",
            ::RecognizeCallback
        )
        //TODO Not sure this will work
        Keyless.initialize(context.applicationContext as Application)
        return CallbackRegistry
    }

    /**
     * No other [Initializer] dependencies are required.
     *
     * @return Empty list.
     */
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}