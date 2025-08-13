/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import com.pingidentity.davinci.plugin.CollectorFactory
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class CollectorRegistryTest {

    private lateinit var collectorRegistry: CollectorRegistry

    @Before
    fun setup() {
        collectorRegistry = CollectorRegistry()
        mockkObject(CollectorFactory)
        every { CollectorFactory.register(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(CollectorFactory)
    }

    @Test
    fun `test initialize registers IdpCollector with correct type`() {
        // When
        collectorRegistry.initialize()

        // Then
        verify { CollectorFactory.register("SOCIAL_LOGIN_BUTTON", ::IdpCollector) }
    }
}

