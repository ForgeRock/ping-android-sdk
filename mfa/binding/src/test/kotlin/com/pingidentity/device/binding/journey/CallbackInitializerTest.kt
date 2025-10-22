/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.journey

import android.content.Context
import com.pingidentity.journey.plugin.Callback
import com.pingidentity.journey.plugin.CallbackRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class CallbackInitializerTest {

    private lateinit var callbackInitializer: CallbackInitializer
    private val mockContext = mockk<Context>()

    @BeforeTest
    fun setUp() {
        callbackInitializer = CallbackInitializer()
        mockkObject(CallbackRegistry)
        every { CallbackRegistry.register(any(), any()) } returns Unit
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(CallbackRegistry)
    }

    @Test
    fun `create registers DeviceBindingCallback`() {
        val result = callbackInitializer.create(mockContext)

        verify { CallbackRegistry.register("DeviceBindingCallback", any()) }
        assertEquals(CallbackRegistry, result)
    }

    @Test
    fun `create registers DeviceSigningVerifierCallback`() {
        val result = callbackInitializer.create(mockContext)

        verify { CallbackRegistry.register("DeviceSigningVerifierCallback", any()) }
        assertEquals(CallbackRegistry, result)
    }

    @Test
    fun `create registers both callbacks`() {
        val result = callbackInitializer.create(mockContext)

        verify { CallbackRegistry.register("DeviceBindingCallback", any()) }
        verify { CallbackRegistry.register("DeviceSigningVerifierCallback", any()) }
        assertEquals(CallbackRegistry, result)
    }

    @Test
    fun `dependencies returns empty list`() {
        val dependencies = callbackInitializer.dependencies()

        assertTrue(dependencies.isEmpty())
    }

    @Test
    fun `create returns CallbackRegistry instance`() {
        val result = callbackInitializer.create(mockContext)

        assertEquals(CallbackRegistry, result)
    }

    @Test
    fun `verify callback function references are correct`() {
        // Mock the registration to capture the callback functions
        val capturedCallbacks = mutableMapOf<String, () -> Any>()
        every {
            CallbackRegistry.register(
                capture(slot<String>()),
                capture(slot<() -> Callback>())
            )
        } answers {
            capturedCallbacks[firstArg()] = secondArg()
        }

        callbackInitializer.create(mockContext)

        // Verify that the captured functions can create the correct callback instances
        val deviceBindingCallback = capturedCallbacks["DeviceBindingCallback"]?.invoke()
        val deviceSigningVerifierCallback =
            capturedCallbacks["DeviceSigningVerifierCallback"]?.invoke()

        assertTrue(deviceBindingCallback is DeviceBindingCallback)
        assertTrue(deviceSigningVerifierCallback is DeviceSigningVerifierCallback)
    }

    @Test
    fun `initializer can be called multiple times`() {
        callbackInitializer.create(mockContext)
        callbackInitializer.create(mockContext)

        // Verify that registration is called twice for each callback
        verify(exactly = 2) { CallbackRegistry.register("DeviceBindingCallback", any()) }
        verify(exactly = 2) { CallbackRegistry.register("DeviceSigningVerifierCallback", any()) }
    }
}

