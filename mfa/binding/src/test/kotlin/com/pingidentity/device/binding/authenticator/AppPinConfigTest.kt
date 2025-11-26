/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.Prompt
import com.pingidentity.device.binding.ui.collectPin
import com.pingidentity.logger.Logger
import com.pingidentity.storage.CacheStrategy
import com.pingidentity.storage.EncryptedDataStoreStorageConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppPinConfigTest {

    private lateinit var appPinConfig: AppPinConfig

    @Before
    fun setUp() {
        mockkObject(ContextProvider)
        every { ContextProvider.currentActivity } returns mockk()
        appPinConfig = AppPinConfig()
        mockkStatic("com.pingidentity.device.binding.ui.AppPinCollectorKt")
    }

    @After
    fun tearDown() {
        unmockkStatic("com.pingidentity.device.binding.ui.AppPinCollectorKt")
    }

    @Test
    fun `test default values`() {
        assertEquals(3, appPinConfig.pinRetry)
        assertEquals("PKCS12", appPinConfig.keystoreType)
        assertNotNull(appPinConfig.logger)
        assertNotNull(appPinConfig.prompt)
    }

    @Test
    fun `test custom logger configuration`() {
        val mockLogger = mockk<Logger>()

        appPinConfig.logger = mockLogger

        assertEquals(mockLogger, appPinConfig.logger)
    }

    @Test
    fun `test custom prompt configuration`() {
        val customPrompt = Prompt("Custom Title", "Custom Subtitle", "Custom Description")

        appPinConfig.prompt = customPrompt

        assertEquals(customPrompt, appPinConfig.prompt)
    }

    @Test
    fun `test custom pinRetry configuration`() {
        appPinConfig.pinRetry = 5

        assertEquals(5, appPinConfig.pinRetry)
    }

    @Test
    fun `test custom keystoreType configuration`() {
        appPinConfig.keystoreType = "JKS"

        assertEquals("JKS", appPinConfig.keystoreType)
    }

    @Test
    fun `test custom pinCollector configuration`() = runTest {
        val customPin = charArrayOf('1', '2', '3', '4')

        appPinConfig.pinCollector { customPin }

        val result = appPinConfig.pinCollector(Prompt())
        assertEquals(customPin, result)
    }


    @Test
    fun `test custom storage configuration`() {
        val mockStorage = mockk<com.pingidentity.storage.Storage<ByteArray>>()

        appPinConfig.storage = { mockStorage }

        val storage = appPinConfig.storage()
        assertTrue(mockStorage === storage)
    }

    @Test
    fun `test storage configuration with custom options`() {
        appPinConfig.storage {
            fileName = "custom_pin_file"
            keyAlias = "custom_key_alias"
            strongBoxPreferred = true
            cacheStrategy = CacheStrategy.NO_CACHE
        }

        val storage = EncryptedDataStoreStorageConfig().apply(appPinConfig.storageOption)
        assertEquals("custom_pin_file", storage.fileName)
        assertEquals("custom_key_alias", storage.keyAlias)
        assertTrue(storage.strongBoxPreferred)
        assertEquals(CacheStrategy.NO_CACHE, storage.cacheStrategy)
    }

    @Test
    fun `test multiple storage configuration calls`() {
        appPinConfig.storage {
            fileName = "first_config"
        }
        appPinConfig.storage {
            keyAlias = "second_config"
        }

        val storage = EncryptedDataStoreStorageConfig().apply(appPinConfig.storageOption)
        assertEquals("first_config", storage.fileName)
        assertEquals("second_config", storage.keyAlias)
    }

    @Test
    fun `test pinCollector when AppPinCollector is available`() = runTest {
        // Mock the class availability check

        val spy = spyk(appPinConfig)
        every { spy.isAppPinCollectorAvailable() } returns true

        // Mock the collectPin function
        val expectedPin = charArrayOf('1', '2', '3', '4')
        coEvery {
            collectPin(any(), any(), any(), any())
        } returns expectedPin

        val prompt = Prompt("Title", "Subtitle", "Description")
        val result = appPinConfig.pinCollector(prompt)

        assertEquals(expectedPin, result)

    }

    @Test
    fun `test prompt configuration with all properties`() {
        val title = "Enter PIN"
        val subtitle = "Please enter your PIN"
        val description = "Use the PIN you created during registration"

        appPinConfig.prompt = Prompt(title, subtitle, description)

        assertEquals(title, appPinConfig.prompt.title)
        assertEquals(subtitle, appPinConfig.prompt.subtitle)
        assertEquals(description, appPinConfig.prompt.description)
    }

    @Test
    fun `test logger propagation to storage config`() {
        val mockLogger = mockk<Logger>()
        appPinConfig.logger = mockLogger

        val storage = EncryptedDataStoreStorageConfig().apply(appPinConfig.storageOption)
        assertTrue(mockLogger === storage.logger)

    }
}

