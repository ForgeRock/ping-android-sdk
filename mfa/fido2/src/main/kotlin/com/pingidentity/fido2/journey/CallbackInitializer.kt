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
 * This class is responsible for registering callbacks in the application.
 * It extends the Initializer interface, which means it is part of the initialization process of the application.
 */
class CallbackInitializer : Initializer<CallbackRegistry> {

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

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}
