/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import com.pingidentity.android.ModuleInitializer
import com.pingidentity.journey.plugin.CallbackRegistry

class IdpCallbackRegistry : ModuleInitializer() {

    /**
     * Initializes the module by registering the IdP collector.
     */
    override fun initialize() {
        CallbackRegistry.register("IdPCallback", ::IdpCallback)
        CallbackRegistry.register("SelectIdPCallback", ::SelectIdpCallback)
    }
}