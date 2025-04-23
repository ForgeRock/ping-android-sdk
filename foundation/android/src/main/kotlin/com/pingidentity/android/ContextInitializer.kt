/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.android

import android.content.Context
import androidx.startup.Initializer

/**
 * This class is responsible for initializing the ContextProvider in the application.
 * It extends the Initializer interface, which means it is part of the initialization process of the application.
 */
class ContextInitializer : Initializer<ContextProvider> {

    override fun create(context: Context): ContextProvider {
        ContextProvider.init(context)
        return ContextProvider
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}