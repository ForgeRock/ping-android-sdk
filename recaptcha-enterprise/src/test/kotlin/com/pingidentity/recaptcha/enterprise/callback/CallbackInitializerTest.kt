/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recaptcha.enterprise.callback

import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.journey.plugin.CallbackRegistry
import com.pingidentity.recaptcha.enterprise.ReCaptchaEnterpriseCallback
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit tests for [CallbackInitializer].
 *
 * This test class verifies that the [CallbackInitializer] properly integrates with the
 * Android App Startup library and registers the ReCaptcha Enterprise callback with the
 * Journey SDK's callback registry during application startup.
 *
 * The [CallbackInitializer] is responsible for:
 * - Implementing the [Initializer] interface for automatic initialization
 * - Registering the [ReCaptchaEnterpriseCallback] with the correct callback name
 * - Returning the [CallbackRegistry] instance for dependency management
 * - Having no dependencies on other initializers
 *
 * @see CallbackInitializer
 * @see ReCaptchaEnterpriseCallback
 * @see CallbackRegistry
 * @see Initializer
 */
class CallbackInitializerTest {

    private lateinit var initializer: CallbackInitializer
    private lateinit var context: Context

    /**
     * Sets up the test environment before each test method.
     *
     * This method:
     * - Creates a relaxed mock of Android [Context]
     * - Instantiates a new [CallbackInitializer] instance
     * - Mocks the [CallbackRegistry] object to verify interactions
     */
    @BeforeTest
    fun setUp() {
        context = mockk<Context>(relaxed = true)
        initializer = CallbackInitializer()
        mockkObject(CallbackRegistry)
    }

    /**
     * Cleans up the test environment after each test method.
     *
     * This method unmocks all MockK objects to prevent test interference
     * and ensure clean state between tests.
     */
    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Tests that the [CallbackInitializer.create] method properly registers the
     * ReCaptcha Enterprise callback and returns the callback registry.
     *
     * This test verifies the core functionality of the initializer:
     * - The method returns the [CallbackRegistry] singleton instance
     * - The [ReCaptchaEnterpriseCallback] is registered with the correct name
     * - The registration uses a lambda factory function for callback creation
     *
     * @see CallbackInitializer.create
     * @see CallbackRegistry.register
     */
    @Test
    fun `create registers ReCaptchaEnterpriseCallback and returns registry`() {
        // ACT
        val result = initializer.create(context)

        // ASSERT
        assertSame(CallbackRegistry, result)
        verify { CallbackRegistry.register("ReCaptchaEnterpriseCallback", any<() -> ReCaptchaEnterpriseCallback>()) }
    }

    /**
     * Tests that the callback is registered with the exact correct name.
     *
     * This test ensures that the callback name matches exactly what the Journey SDK
     * expects. The name "ReCaptchaEnterpriseCallback" must match the callback type
     * sent from the server in Journey flows.
     *
     * @see CallbackRegistry.register
     */
    @Test
    fun `create registers callback with correct name`() {
        // ACT
        initializer.create(context)

        // ASSERT
        verify { CallbackRegistry.register("ReCaptchaEnterpriseCallback", any<() -> ReCaptchaEnterpriseCallback>()) }
    }

    /**
     * Tests that the [CallbackInitializer.dependencies] method returns an empty list.
     *
     * This test verifies that the ReCaptcha Enterprise initializer has no dependencies
     * on other initializers, meaning it can be initialized at any time during app startup
     * without waiting for other components.
     *
     * @see CallbackInitializer.dependencies
     * @see Initializer.dependencies
     */
    @Test
    fun `dependencies returns empty list`() {
        // ACT
        val dependencies = initializer.dependencies()

        // ASSERT
        assertTrue(dependencies.isEmpty())
        assertEquals(emptyList<Class<out Initializer<*>?>?>(), dependencies)
    }

    /**
     * Tests that the [CallbackInitializer.create] method accepts an Android [Context]
     * parameter and doesn't throw any exceptions.
     *
     * This test verifies the method signature compatibility with the [Initializer]
     * interface and ensures basic functionality without errors.
     *
     * @see CallbackInitializer.create
     */
    @Test
    fun `create method accepts context parameter`() {
        // This test verifies the method signature and that it doesn't throw
        // ACT/ASSERT
        val result = initializer.create(context)
        assertNotNull(result)
    }

    /**
     * Tests that multiple calls to [CallbackInitializer.create] work correctly
     * and return consistent results.
     *
     * This test ensures that:
     * - Multiple initialization calls don't cause errors
     * - The same [CallbackRegistry] instance is returned each time
     * - The initializer is idempotent and safe for repeated calls
     *
     * This is important because the Android App Startup library may call
     * the initializer multiple times in certain scenarios.
     *
     * @see CallbackInitializer.create
     */
    @Test
    fun `multiple calls to create should work without issues`() {
        // ACT
        val result1 = initializer.create(context)
        val result2 = initializer.create(context)

        // ASSERT
        assertSame(result1, result2)
        assertSame(CallbackRegistry, result1)
        assertSame(CallbackRegistry, result2)
    }
}
