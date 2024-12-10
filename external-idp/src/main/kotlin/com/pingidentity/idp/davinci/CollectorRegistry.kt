/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import com.pingidentity.android.ModuleInitializer
import com.pingidentity.davinci.plugin.CollectorFactory

/**
 * A registry class for initializing and registering collectors.
 */
class CollectorRegistry: ModuleInitializer() {

    /**
     * Initializes the module by registering the IdP collector.
     */
    override fun initialize() {
        CollectorFactory.register("SOCIAL_LOGIN_BUTTON", ::IdpCollector)
    }
}