/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import android.content.Context
import com.pingidentity.journey.plugin.CallbackRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CallbackInitializerTest {

    private lateinit var callbackInitializer: CallbackInitializer
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        callbackInitializer = CallbackInitializer()
        mockContext = mockk()
        mockkObject(CallbackRegistry)
        every { CallbackRegistry.register(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(CallbackRegistry)
    }

    @Test
    fun `test create registers the expected callbacks`() {
        // When
        val result = callbackInitializer.create(mockContext)

        // Then
        verify { CallbackRegistry.register("IdPCallback", ::IdpCallback) }
        verify { CallbackRegistry.register("SelectIdPCallback", ::SelectIdpCallback) }
        assertEquals(CallbackRegistry, result)
    }

    @Test
    fun `test dependencies returns empty list`() {
        // When
        val dependencies = callbackInitializer.dependencies()

        // Then
        assertEquals(0, dependencies.size)
    }
}

