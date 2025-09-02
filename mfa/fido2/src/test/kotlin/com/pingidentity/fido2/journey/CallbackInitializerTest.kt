/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.journey

import android.content.Context
import com.pingidentity.journey.plugin.CallbackRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallbackInitializerTest {

    private lateinit var callbackInitializer: CallbackInitializer
    private lateinit var mockContext: Context

    @BeforeTest
    fun setUp() {
        callbackInitializer = CallbackInitializer()
        mockContext = mockk<Context>()
        mockkObject(CallbackRegistry)
        every { CallbackRegistry.register(any(), any()) } returns Unit
    }

    @Test
    fun `create should register FIDO2 registration callback`() {
        // When
        val result = callbackInitializer.create(mockContext)

        // Then
        verify {
            CallbackRegistry.register(
                "Fido2RegistrationCallback",
                ::Fido2RegistrationCallback
            )
        }
        assertEquals(CallbackRegistry, result)
    }

    @Test
    fun `create should register FIDO2 authentication callback`() {
        // When
        val result = callbackInitializer.create(mockContext)

        // Then
        verify {
            CallbackRegistry.register(
                "Fido2AuthenticationCallback",
                ::Fido2AuthenticationCallback
            )
        }
        assertEquals(CallbackRegistry, result)
    }

    @Test
    fun `create should register both callbacks`() {
        // When
        callbackInitializer.create(mockContext)

        // Then
        verify(exactly = 2) {
            CallbackRegistry.register(any(), any())
        }
    }

    @Test
    fun `dependencies should return empty list`() {
        // When
        val dependencies = callbackInitializer.dependencies()

        // Then
        assertTrue(dependencies.isEmpty())
    }
}

