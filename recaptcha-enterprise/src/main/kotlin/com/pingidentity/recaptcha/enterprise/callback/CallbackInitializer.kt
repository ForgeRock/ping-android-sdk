/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recaptcha.enterprise.callback

import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.journey.plugin.CallbackRegistry
import com.pingidentity.recaptcha.enterprise.ReCaptchaEnterpriseCallback

/**
 * Initializer for registering the ReCaptcha Enterprise callback with the Journey SDK.
 *
 * This class is used by Android's App Startup library to automatically register
 * the ReCaptchaEnterpriseCallback during application initialization. The callback
 * is registered with the name "ReCaptchaEnterpriseCallback" and can be used in
 * Journey flows to handle ReCaptcha Enterprise verification challenges.
 *
 * The initializer is automatically invoked during app startup, ensuring the
 * callback is available for use in authentication flows.
 */
class CallbackInitializer : Initializer<CallbackRegistry> {

    /**
     * Creates and configures the CallbackRegistry with ReCaptcha Enterprise callback support.
     *
     * @param context The application context
     * @return The configured CallbackRegistry instance
     */
    override fun create(context: Context): CallbackRegistry {
        CallbackRegistry.register("ReCaptchaEnterpriseCallback", ::ReCaptchaEnterpriseCallback)
        return CallbackRegistry
    }

    /**
     * Specifies dependencies for this initializer.
     *
     * @return An empty list as this initializer has no dependencies
     */
    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}