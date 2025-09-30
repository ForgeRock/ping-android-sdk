/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.davinci

import android.content.Context
import com.pingidentity.davinci.plugin.CollectorFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class CollectorInitializerTest {

    private lateinit var mockContext: Context
    private lateinit var initializer: CollectorInitializer

    @BeforeTest
    fun setUp() {
        mockContext = mockk()
        initializer = CollectorInitializer()
        mockkObject(CollectorFactory)
        every { CollectorFactory.register(any(), any()) } returns Unit
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(CollectorFactory)
    }

    @Test
    fun `create should register FIDO2 collector and return CollectorFactory`() {
        val result = initializer.create(mockContext)

        verify { CollectorFactory.register("FIDO2", ::Fido2Collector) }
        assertEquals(CollectorFactory, result)
    }

    @Test
    fun `dependencies should return empty list`() {
        val dependencies = initializer.dependencies()

        assertTrue(dependencies.isEmpty())
    }
}

