/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.CryptoKey
import com.pingidentity.device.binding.authenticator.exception.BiometricAuthenticationException
import com.pingidentity.device.binding.authenticator.exception.DeviceNotRegisteredException
import com.pingidentity.logger.Logger
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class BiometricOnlyAuthenticatorTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }
    private lateinit var authenticator: BiometricOnlyAuthenticator
    private lateinit var mockConfig: BiometricAuthenticatorConfig
    private lateinit var mockBiometricManager: BiometricManager
    private lateinit var mockLogger: Logger
    private lateinit var mockPackageManager: PackageManager
    private lateinit var contextSpy: Context

    @BeforeTest
    fun setUp() {
        mockkStatic(Build.VERSION::class)
        mockkStatic("com.pingidentity.device.binding.authenticator.BiometricPromptActivityKt")

        mockBiometricManager = mockk()
        mockLogger = mockk(relaxed = true)
        mockPackageManager = mockk()
        mockConfig = mockk(relaxed = true)

        ContextProvider.init(context)

        every { mockConfig.biometricManager } returns mockBiometricManager
        every { mockConfig.logger } returns mockLogger
        every { mockConfig.strongBoxPreferred } returns false
        every { mockConfig.promptInfo } returns {
            setTitle("Authenticate")
            setSubtitle("Please authenticate to proceed")
        }
        every { mockConfig.keyGenParameterSpec } returns mockk(relaxed = true)

        contextSpy = spyk(context)
        every { contextSpy.packageManager } returns mockPackageManager

        authenticator = BiometricOnlyAuthenticator(mockConfig)
        val cryptoKey = spyk(CryptoKey("test-alias"))
        authenticator.cryptoKey = cryptoKey
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(ContextProvider)
        unmockkStatic(Build.VERSION::class)
        unmockkStatic("com.pingidentity.device.binding.authenticator.BiometricPromptActivityKt")
    }

    @Test
    fun `test companion object invoke creates authenticator with default config`() {
        val result = BiometricOnlyAuthenticator()
        assertEquals(DeviceBindingAuthenticationType.BIOMETRIC_ONLY, result.type)
    }

    @Test
    fun `test type returns correct authentication type`() {
        assertEquals(DeviceBindingAuthenticationType.BIOMETRIC_ONLY, authenticator.type)
    }

    @Test
    fun `test authenticate success with biometric strong support using crypto object`() = runTest {
        val mockPrivateKey = mockk<PrivateKey>()
        val mockAuthResult = mockk<BiometricPrompt.AuthenticationResult>()
        val mockCryptoObject = mockk<BiometricPrompt.CryptoObject>()

        every { authenticator.cryptoKey.privateKey } returns mockPrivateKey
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_SUCCESS
        every { mockAuthResult.cryptoObject } returns mockCryptoObject

        coEvery {
            authenticateWithBiometric(any(), any(), any())
        } returns mockAuthResult

        val result = authenticator.authenticate(context)

        assertTrue(result.isSuccess)
        val (privateKey, cryptoObject) = result.getOrThrow()
        assertEquals(mockPrivateKey, privateKey)
        assertEquals(mockCryptoObject, cryptoObject)
        verify { mockLogger.d("BiometricOnly: Using BIOMETRIC_STRONG with crypto object") }
    }

    @Test
    fun `test authenticate success with biometric weak fallback when strong not available`() = runTest {
        val mockPrivateKey = mockk<PrivateKey>()
        val mockAuthResult = mockk<BiometricPrompt.AuthenticationResult>()

        every { authenticator.cryptoKey.privateKey } returns mockPrivateKey
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        every { mockAuthResult.cryptoObject } returns null

        coEvery {
            authenticateWithBiometric(any(), any(), null)
        } returns mockAuthResult

        val result = authenticator.authenticate(context)

        assertTrue(result.isSuccess)
        val (privateKey, cryptoObject) = result.getOrThrow()
        assertEquals(mockPrivateKey, privateKey)
        assertEquals(null, cryptoObject)
    }

    @Test
    fun `test authenticate fails when device not registered`() = runTest {
        every { authenticator.cryptoKey.privateKey } returns null

        val result = authenticator.authenticate(context)

        assertTrue(result.isFailure)
        assertFailsWith<DeviceNotRegisteredException> {
            result.getOrThrow()
        }
    }

    @Test
    fun `test authenticate handles biometric authentication exception`() = runTest {
        val mockPrivateKey = mockk<PrivateKey>()

        every { authenticator.cryptoKey.privateKey } returns mockPrivateKey
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_SUCCESS

        coEvery {
            authenticateWithBiometric(any(), any(), any())
        } throws BiometricAuthenticationException(1, "Authentication failed")

        val result = authenticator.authenticate(context)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BiometricAuthenticationException)
    }

    @Test
    fun `test register success without strongbox`() = runTest {
        val mockKeyPair = mockk<java.security.KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val keyGenSpec = slot<KeyGenParameterSpec>()
        val attestation = Attestation.Default("test-challenge".toByteArray())

        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey
        every { authenticator.cryptoKey.create(capture(keyGenSpec)) } returns mockKeyPair
        every { mockPackageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE) } returns false
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_SUCCESS

        val result = authenticator.register(context, attestation)

        assertTrue(result.isSuccess)
        val keyPair = result.getOrThrow()
        assertEquals(mockPublicKey, keyPair.publicKey)
        assertEquals(mockPrivateKey, keyPair.privateKey)
        assertEquals("-ziwgyW4z0C_2KONqVHrl6pptZfJK8HjC16DcAhEQFs", keyPair.keyAlias)
        assertFalse(keyGenSpec.captured.isStrongBoxBacked)
        assertTrue(keyGenSpec.captured.isUserAuthenticationRequired)
    }

    @Test
    fun `test register success with strongbox when available and preferred`() = runTest {
        val mockKeyPair = mockk<java.security.KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val keyGenSpec = slot<KeyGenParameterSpec>()
        val attestation = Attestation.None

        every { mockConfig.strongBoxPreferred } returns true
        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey
        every { authenticator.cryptoKey.create(capture(keyGenSpec)) } returns mockKeyPair
        every { mockPackageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE) } returns true
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_SUCCESS

        val result = authenticator.register(contextSpy, attestation)

        assertTrue(result.isSuccess)
        assertTrue(keyGenSpec.captured.isStrongBoxBacked)
        verify { mockLogger.d("StrongBox is available, using StrongBox for key generation") }
    }

    @Test
    fun `test register without attestation challenge`() = runTest {
        val mockKeyPair = mockk<java.security.KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val mockKeyGenSpec = slot<KeyGenParameterSpec>()
        val attestation = Attestation.None

        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey
        every { authenticator.cryptoKey.create(capture(mockKeyGenSpec)) } returns mockKeyPair
        every { mockPackageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE) } returns false
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_SUCCESS

        val result = authenticator.register(context, attestation)

        assertTrue(mockKeyGenSpec.captured.attestationChallenge == null)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test register with time-based authentication when strong biometric not available`() = runTest {
        val mockKeyPair = mockk<java.security.KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val keyGenSpec = slot<KeyGenParameterSpec>()
        val attestation = Attestation.None

        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey
        every { authenticator.cryptoKey.create(capture(keyGenSpec)) } returns mockKeyPair
        every { mockPackageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE) } returns false
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        val result = authenticator.register(context, attestation)

        assertTrue(result.isSuccess)
        verify { mockLogger.d("BiometricOnly: Device doesn't support BIOMETRIC_STRONG, using time-based authentication") }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `test register uses deprecated method on older Android versions`() = runTest {
        val mockKeyPair = mockk<java.security.KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val keyGenSpec = slot<KeyGenParameterSpec>()
        val attestation = Attestation.None

        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey
        every { authenticator.cryptoKey.create(capture(keyGenSpec)) } returns mockKeyPair
        every { mockPackageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE) } returns false
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        val result = authenticator.register(context, attestation)

        assertTrue(result.isSuccess)
        assertTrue(
            keyGenSpec.captured.userAuthenticationValidityDurationSeconds == authenticator.cryptoKey.timeout
        )
    }

    @Test
    fun `test isSupported returns true when biometric weak available`() {
        every {
            mockBiometricManager.canAuthenticate(BIOMETRIC_WEAK)
        } returns BiometricManager.BIOMETRIC_SUCCESS

        val result = authenticator.isSupported(context, Attestation.None)

        assertTrue(result)
    }

    @Test
    fun `test isSupported returns false when biometric weak not available`() {
        every {
            mockBiometricManager.canAuthenticate(BIOMETRIC_WEAK)
        } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        val result = authenticator.isSupported(context, Attestation.None)

        assertFalse(result)
    }

    @Test
    fun `test register handles exception properly`() = runTest {
        val attestation = Attestation.None

        every { authenticator.cryptoKey.create(any()) } throws RuntimeException("Key generation failed")
        every { mockPackageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE) } returns false
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_SUCCESS

        val result = authenticator.register(context, attestation)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Key generation failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test authenticate with empty attestation challenge`() = runTest {
        val mockKeyPair = mockk<java.security.KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val mockKeyGenSpec = slot<KeyGenParameterSpec>()
        val attestation = Attestation.Default(byteArrayOf())

        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey
        every { authenticator.cryptoKey.create(capture(mockKeyGenSpec)) } returns mockKeyPair
        every { mockPackageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE) } returns false
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_SUCCESS

        val result = authenticator.register(context, attestation)

        assertTrue(result.isSuccess)
        assertTrue(mockKeyGenSpec.captured.attestationChallenge == null)
    }

    @Test
    fun `test register logs appropriate messages during key generation`() = runTest {
        val mockKeyPair = mockk<java.security.KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val attestation = Attestation.None

        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey
        every { authenticator.cryptoKey.create(any()) } returns mockKeyPair
        every { mockPackageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE) } returns false
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_SUCCESS

        val result = authenticator.register(context, attestation)

        assertTrue(result.isSuccess)
        verify { mockLogger.d("BiometricOnly: Generating keys") }
        verify { mockLogger.i("BiometricOnly: Successfully generated keys") }
    }

    @Test
    fun `test authenticate logs appropriate messages during authentication`() = runTest {
        val mockPrivateKey = mockk<PrivateKey>()
        val mockAuthResult = mockk<BiometricPrompt.AuthenticationResult>()
        val mockCryptoObject = mockk<BiometricPrompt.CryptoObject>()

        every { authenticator.cryptoKey.privateKey } returns mockPrivateKey
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_SUCCESS
        every { mockAuthResult.cryptoObject } returns mockCryptoObject

        coEvery {
            authenticateWithBiometric(any(), any(), any())
        } returns mockAuthResult

        val result = authenticator.authenticate(context)

        assertTrue(result.isSuccess)
        verify { mockLogger.d("BiometricOnly: Starting authentication") }
        verify { mockLogger.d("BiometricOnly: Initiating biometric authentication") }
        verify { mockLogger.i("BiometricOnly: Authentication successful") }
    }
}
