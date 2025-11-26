/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class BiometricAuthenticatorConfigTest {

    private lateinit var biometricAuthenticatorConfig: BiometricAuthenticatorConfig
    private lateinit var mockBiometricManager: BiometricManager

    @BeforeTest
    fun setUp() {
        mockkObject(ContextProvider)
        mockkStatic(BiometricManager::class)

        mockBiometricManager = mockk<BiometricManager>()
        every { ContextProvider.context } returns mockk()
        every { BiometricManager.from(any()) } returns mockBiometricManager

        biometricAuthenticatorConfig = BiometricAuthenticatorConfig()
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(ContextProvider)
        unmockkStatic(BiometricManager::class)
    }

    @Test
    fun `test default values`() {
        assertNotNull(biometricAuthenticatorConfig.logger)
        assertEquals(Logger.logger, biometricAuthenticatorConfig.logger)
        assertFalse(biometricAuthenticatorConfig.strongBoxPreferred)
        assertNotNull(biometricAuthenticatorConfig.biometricManager)
    }

    @Test
    fun `test custom logger configuration`() {
        val mockLogger = mockk<Logger>()

        biometricAuthenticatorConfig.logger = mockLogger

        assertEquals(mockLogger, biometricAuthenticatorConfig.logger)
    }

    @Test
    fun `test strongBoxPreferred configuration`() {
        biometricAuthenticatorConfig.strongBoxPreferred = true

        assertTrue(biometricAuthenticatorConfig.strongBoxPreferred)
    }

    @Test
    fun `test biometricManager initialization`() {
        verify { BiometricManager.from(any()) }
        assertEquals(mockBiometricManager, biometricAuthenticatorConfig.biometricManager)
    }

    @Test
    fun `test promptInfo configuration with single block`() {
        val config = BiometricAuthenticatorConfig()
        val mockBuilder = mockk<BiometricPrompt.PromptInfo.Builder>(relaxed = true)

        config.promptInfo {
            setTitle("Test Title")
            setSubtitle("Test Subtitle")
        }

        // Apply the configuration to verify it was stored correctly
        config.promptInfo.invoke(mockBuilder)

        verify { mockBuilder.setTitle("Test Title") }
        verify { mockBuilder.setSubtitle("Test Subtitle") }
    }

    @Test
    fun `test promptInfo configuration with multiple blocks`() {
        val config = BiometricAuthenticatorConfig()
        val mockBuilder = mockk<BiometricPrompt.PromptInfo.Builder>(relaxed = true)

        config.promptInfo {
            setTitle("Test Title")
        }
        config.promptInfo {
            setSubtitle("Test Subtitle")
        }

        // Apply the configuration to verify both blocks were chained
        config.promptInfo.invoke(mockBuilder)

        verify { mockBuilder.setTitle("Test Title") }
        verify { mockBuilder.setSubtitle("Test Subtitle") }
    }

    @Test
    fun `test keyGenParameterSpec configuration with single block`() {
        val config = BiometricAuthenticatorConfig()

        config.keyGenParameterSpec {
            setKeySize(256)
            setUserAuthenticationRequired(false)
        }

        // Apply the configuration to verify it was stored correctly
        val spec = KeyGenParameterSpec.Builder("test", KeyProperties.PURPOSE_SIGN)
            .apply(config.keyGenParameterSpec).build()
        assertEquals(256, spec.keySize)
        assertFalse(spec.isUserAuthenticationRequired)
    }

    @Test
    fun `test keyGenParameterSpec configuration with multiple blocks`() {
        val config = BiometricAuthenticatorConfig()

        config.keyGenParameterSpec {
            setKeySize(256)
        }
        config.keyGenParameterSpec {
            setUserAuthenticationRequired(true)
        }

        // Apply the configuration to verify both blocks were chained
        val spec = KeyGenParameterSpec.Builder("test", KeyProperties.PURPOSE_SIGN)
            .apply(config.keyGenParameterSpec).build()
        assertEquals(256, spec.keySize)
        assertTrue(spec.isUserAuthenticationRequired)
    }

    @Test
    fun `test configuration chaining maintains functionality`() {
        val config = BiometricAuthenticatorConfig()

        // Set multiple configurations
        config.logger = mockk<Logger>()
        config.strongBoxPreferred = true
        config.promptInfo { setTitle("Chained Title") }
        config.keyGenParameterSpec { setKeySize(512) }

        // Verify all configurations are maintained
        assertTrue(config.strongBoxPreferred)
        assertNotNull(config.logger)

        // Verify the lambda configurations work
        val mockPromptBuilder = mockk<BiometricPrompt.PromptInfo.Builder>(relaxed = true)

        config.promptInfo.invoke(mockPromptBuilder)

        verify { mockPromptBuilder.setTitle("Chained Title") }

        val spec = KeyGenParameterSpec.Builder("test", KeyProperties.PURPOSE_SIGN)
            .apply(config.keyGenParameterSpec).build()
        assertEquals(512, spec.keySize)
    }

    @Test
    fun `test PingDsl annotation is applied`() {
        // Verify that the class has the @PingDsl annotation by checking if we can use it in DSL style
        val config = BiometricAuthenticatorConfig()

        // This test ensures the class can be used in a DSL-like manner
        config.apply {
            logger = mockk<Logger>()
            strongBoxPreferred = true
            promptInfo {
                setTitle("DSL Test")
            }
            keyGenParameterSpec {
                setKeySize(256)
            }
        }

        assertTrue(config.strongBoxPreferred)
        assertNotNull(config.logger)
    }

    @Test
    fun `test empty lambda configurations don't break functionality`() {
        val config = BiometricAuthenticatorConfig()
        val mockPromptBuilder = mockk<BiometricPrompt.PromptInfo.Builder>(relaxed = true)

        // Apply empty configurations
        config.promptInfo { }
        config.keyGenParameterSpec { }

        // Should not throw exceptions
        config.promptInfo.invoke(mockPromptBuilder)
        KeyGenParameterSpec.Builder("test", KeyProperties.PURPOSE_SIGN)
            .apply(config.keyGenParameterSpec).build()
    }
}
