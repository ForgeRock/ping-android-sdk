/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.journey

import android.app.Application
import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.Prompt
import com.pingidentity.device.binding.UserKey
import com.pingidentity.device.binding.authenticator.AppPinAuthenticator
import com.pingidentity.device.binding.authenticator.AppPinConfig
import com.pingidentity.device.binding.authenticator.BiometricAuthenticatorConfig
import com.pingidentity.device.binding.authenticator.BiometricDeviceCredentialAuthenticator
import com.pingidentity.device.binding.authenticator.BiometricOnlyAuthenticator
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType
import com.pingidentity.device.binding.authenticator.None
import com.pingidentity.logger.Logger
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class DeviceBindingConfigTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }

    @BeforeTest
    fun setUp() {
        ContextProvider.init(context)
    }

    @Test
    fun `test default values`() {
        val config = DeviceBindingConfig()

        assertEquals("RS512", config.signingAlgorithm)
        assertNotNull(config.deviceName)
        assertNotNull(config.deviceIdentifier)
        assertNotNull(config.logger)
    }

    @Test
    fun `test custom logger configuration`() {
        val mockLogger = mockk<Logger>()
        val config = DeviceBindingConfig().apply {
            logger = mockLogger
        }

        assertEquals(mockLogger, config.logger)
    }

    @Test
    fun `test custom signing algorithm`() {
        val config = DeviceBindingConfig().apply {
            signingAlgorithm = "ES256"
        }

        assertEquals("ES256", config.signingAlgorithm)
    }

    @Test
    fun `test time configuration`() {
        val fixedInstant = Instant.ofEpochMilli(1000000L)
        val config = DeviceBindingConfig().apply {
            issueTime = { fixedInstant }
            notBeforeTime = { fixedInstant }
            expirationTime = { fixedInstant.plusSeconds(it.toLong()) }
        }

        assertEquals(fixedInstant, config.issueTime())
        assertEquals(fixedInstant, config.notBeforeTime())
        assertEquals(Instant.ofEpochMilli(1000000L).plusSeconds(60), config.expirationTime(60))
    }

    @Test
    fun `test claims configuration`() {
        val config = DeviceBindingConfig().apply {
            claims {
                put("custom_claim", "test_value")
            }
            claims {
                put("another_claim", 123)
            }
        }

        val claimsMap = mutableMapOf<String, Any>()
        config.claims(claimsMap)

        assertEquals("test_value", claimsMap["custom_claim"])
        assertEquals(123, claimsMap["another_claim"])
    }

    @Test
    fun `test authenticator creation for BIOMETRIC_ONLY`() {
        val config = DeviceBindingConfig()
        val prompt = Prompt("Title", "Subtitle", "Description")

        val authenticator =
            config.authenticator(DeviceBindingAuthenticationType.BIOMETRIC_ONLY, prompt)

        assertTrue(authenticator is BiometricOnlyAuthenticator)
    }

    @Test
    fun `test authenticator creation for BIOMETRIC_ALLOW_FALLBACK`() {
        val config = DeviceBindingConfig()
        val prompt = Prompt("Title", "Subtitle", "Description")

        val authenticator =
            config.authenticator(DeviceBindingAuthenticationType.BIOMETRIC_ALLOW_FALLBACK, prompt)

        assertTrue(authenticator is BiometricDeviceCredentialAuthenticator)
    }

    @Test
    fun `test authenticator creation for APPLICATION_PIN`() {
        val config = DeviceBindingConfig()
        val prompt = Prompt("Title", "Subtitle", "Description")

        val authenticator =
            config.authenticator(DeviceBindingAuthenticationType.APPLICATION_PIN, prompt)

        assertTrue(authenticator is AppPinAuthenticator)
    }

    @Test
    fun `test authenticator creation for NONE`() {
        val config = DeviceBindingConfig()
        val prompt = Prompt("Title", "Subtitle", "Description")

        val authenticator = config.authenticator(DeviceBindingAuthenticationType.NONE, prompt)

        assertTrue(authenticator is None)
    }

    @Test
    fun `test custom device authenticator`() {
        val mockAuthenticator =
            mockk<com.pingidentity.device.binding.authenticator.DeviceAuthenticator>()
        val config = DeviceBindingConfig().apply {
            deviceAuthenticator = { mockAuthenticator }
        }
        val prompt = Prompt("Title", "Subtitle", "Description")

        val authenticator =
            config.authenticator(DeviceBindingAuthenticationType.BIOMETRIC_ONLY, prompt)

        assertEquals(mockAuthenticator, authenticator)
    }

    @Test
    fun `test custom user key selector`() = runTest {
        val mockUserKey = mockk<UserKey>()
        val userKeys = listOf(mockUserKey)
        var selectorCalled = false

        val config = DeviceBindingConfig().apply {
            userKeySelector { keys ->
                selectorCalled = true
                assertEquals(userKeys, keys)
                mockUserKey
            }
        }

        assertEquals(mockUserKey, config.userKeySelector(userKeys))
        assertTrue(selectorCalled)
    }

    @Test
    fun `test key storage creation`() {
        val config = DeviceBindingConfig().apply {
            userKeyStorage {
                // Configure storage if needed
            }
        }

        val storage = config.keyStorage()
        assertNotNull(storage)
    }

    @Test
    fun `test app pin config`() {
        val config = DeviceBindingConfig().apply {
            appPinConfig {
                pinRetry = 5
                keystoreType = "PKCS12"
            }
        }

        val appPinConfig = AppPinConfig().apply(config.appPinConfig)
        assertEquals(5, appPinConfig.pinRetry)
        assertEquals("PKCS12", appPinConfig.keystoreType)
    }

    @Test
    fun `test biometric authenticator config`() {
        val config = DeviceBindingConfig().apply {
            biometricAuthenticatorConfig {
                promptInfo {
                    setTitle("Custom Title")
                }
            }
        }

        val biometricAuthenticatorConfig = BiometricAuthenticatorConfig().apply(config.biometricAuthenticatorConfig)
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder().apply(biometricAuthenticatorConfig.promptInfo)
        promptInfoBuilder.setNegativeButtonText("Cancel")
        val promptInfo = promptInfoBuilder.build()
        assertEquals("Custom Title", promptInfo.title)

    }
}

