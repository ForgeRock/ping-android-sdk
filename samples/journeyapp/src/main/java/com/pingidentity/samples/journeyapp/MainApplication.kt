/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.journeyapp

import android.app.Application
import io.keyless.sdk.Keyless

class MainApplication  : Application() {

    override fun onCreate() {
        super.onCreate()
        Keyless.initialize(this)
    }
}