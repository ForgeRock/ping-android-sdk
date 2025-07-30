/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.MfaConfiguration
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@ExperimentalCoroutinesApi
class OathClientTest {

    private lateinit var context: Context
    private lateinit var logger: Logger
    
    @Before
    fun setup() {
        // Mock application context
        context = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        
        // Mock ContextProvider for all tests
        mockkObject(ContextProvider)
        every { ContextProvider.context } returns context
    }
    
    @After
    fun tearDown() {
        // Clean up mocks after each test
        unmockkAll()
    }
    
    @Test
    fun `test OathClient class exists and has factory methods`() = runTest {
        // Given we have access to a mock context via ContextProvider
        
        // Just assert that the OathClient class exists by accessing it and checking if it's not null
        assertNotNull("OathClient class should exist", OathClient::class.java)
        
        // Check that the static methods exist by confirming they can be referenced without errors
        // We don't need to check the exact signature of the method, just that the class has the expected methods
        val methods = OathClient.Companion::class.java.declaredMethods
        val hasInvokeMethod = methods.any { it.name == "invoke" }
        val hasCreateMethod = methods.any { it.name == "create" }
        
        assert(hasInvokeMethod) { "OathClient.Companion should have an invoke method" }
        assert(hasCreateMethod) { "OathClient.Companion should have a create method" }
    }
    
    @Test
    fun `test OathClient constructor accepts MfaConfiguration`() {
        // Given we have access to a mock context via ContextProvider
        
        // Create a mock configuration
        val mockConfig = mockk<MfaConfiguration>(relaxed = true)
        every { mockConfig.context } returns context
        
        // When we create a client with the configuration
        // Note: We don't initialize() here to avoid native SQLite libraries
        val client = OathClient(mockConfig)
        
        // Then the client should not be null
        assertNotNull("Client should not be null", client)
    }
    
    @Test
    fun `test MfaConfiguration uses ContextProvider`() {
        // Given we have mocked the ContextProvider
        
        // When we build a configuration and access the context
        val config = MfaConfiguration {}
        val retrievedContext = config.context
        
        // Then it should use the context from ContextProvider
        assertEquals("Context should come from ContextProvider", context, retrievedContext)
        
        // Verify ContextProvider.context was accessed
        verify { ContextProvider.context }
    }
}
