/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.davinci

import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.davinci.plugin.CollectorFactory

/**
 * A registry class for initializing and registering collectors.
 */
class CollectorInitializer : Initializer<CollectorFactory> {

    override fun create(context: Context): CollectorFactory {
        CollectorFactory.register("FIDO2", ::Fido2Collector)
        return CollectorFactory
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }

}