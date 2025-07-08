/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect.journey

import android.content.Context
import com.pingidentity.journey.plugin.CallbackRegistry
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class CallbackInitializerTest {

    @Test
    fun createRegistersCallbacksAndReturnsRegistry() {
        val context = mockk<Context>(relaxed = true)
        val registry = CallbackRegistry

        val initializer = CallbackInitializer()
        val result = initializer.create(context)

        assertSame(registry, result)
        assertNotNull(CallbackRegistry.callbacks()["PingOneProtectEvaluationCallback"])
        assertNotNull(CallbackRegistry.callbacks()["PingOneProtectInitializeCallback"])
    }

}