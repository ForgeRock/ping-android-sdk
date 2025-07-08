/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect.davinci

import android.content.Context
import com.pingidentity.davinci.plugin.CollectorFactory
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class CollectorInitializerTest {

    @Test
    fun `create registers collector and returns registry`() {
        val context = mockk<Context>(relaxed = true)
        val registry = CollectorFactory

        val initializer = CollectorInitializer()
        val result = initializer.create(context)

        assertSame(registry, result)
        assertNotNull(CollectorFactory.collectors()["PROTECT"])
    }

}