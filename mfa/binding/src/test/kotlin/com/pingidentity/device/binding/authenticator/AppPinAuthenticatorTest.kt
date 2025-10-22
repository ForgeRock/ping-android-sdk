/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.CryptoKey
import com.pingidentity.device.binding.Prompt
import com.pingidentity.device.binding.authenticator.exception.AbortException
import com.pingidentity.device.binding.authenticator.exception.DeviceNotRegisteredException
import com.pingidentity.device.binding.authenticator.exception.InvalidCredentialException
import com.pingidentity.logger.Logger
import com.pingidentity.storage.MemoryStorage
import com.pingidentity.storage.Storage
import io.mockk.mockk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.FileNotFoundException
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class AppPinAuthenticatorTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }
    private lateinit var authenticator: AppPinAuthenticator
    private lateinit var appPinConfig: AppPinConfig
    private lateinit var mockLogger: Logger
    private lateinit var mockStorage: Storage<ByteArray>
    private lateinit var cryptoKey: CryptoKey
    private val testPin = "123456".toCharArray()
    private val testKeyAlias = "test-alias"

    @BeforeTest
    fun setUp() {
        ContextProvider.init(context)

        mockLogger = mockk(relaxed = true)
        mockStorage = MemoryStorage()
        appPinConfig = AppPinConfig()
        cryptoKey = CryptoKey(testKeyAlias)

        appPinConfig.logger = mockLogger
        appPinConfig.prompt = Prompt("Enter PIN", "Authenticate", "Please enter your PIN")
        appPinConfig.pinRetry = 3
        appPinConfig.keystoreType = "PKCS12"
        appPinConfig.storage = { mockStorage }

        authenticator = AppPinAuthenticator(appPinConfig)
        authenticator.cryptoKey = cryptoKey
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(ContextProvider)
    }

    @Test
    fun `test companion object invoke creates authenticator with default config`() {
        val result = AppPinAuthenticator()
        assertEquals(DeviceBindingAuthenticationType.APPLICATION_PIN, result.type)
    }

    @Test
    fun `test companion object invoke creates authenticator with custom config`() {
        val result = AppPinAuthenticator {
            pinRetry = 5
            keystoreType = "BKS"
        }
        assertEquals(DeviceBindingAuthenticationType.APPLICATION_PIN, result.type)
    }

    @Test
    fun `test type returns correct authentication type`() {
        assertEquals(DeviceBindingAuthenticationType.APPLICATION_PIN, authenticator.type)
    }

    @Test
    fun `test register success with PIN collection`() = runTest {
        val attestation = Attestation.None
        appPinConfig.pinCollector = { testPin }
        val result = authenticator.register(context, attestation)

        assertTrue(result.isSuccess)
        val keyPair = result.getOrThrow()
        assertEquals("-ziwgyW4z0C_2KONqVHrl6pptZfJK8HjC16DcAhEQFs", keyPair.keyAlias)

        // Verify RSA spec parameters

        verify { mockLogger.d("AppPinAuthenticator: Generating keys for alias=-ziwgyW4z0C_2KONqVHrl6pptZfJK8HjC16DcAhEQFs") }
        verify { mockLogger.d("AppPinAuthenticator: Collecting PIN for key generation") }
        verify { mockLogger.d("AppPinAuthenticator: Persisting key pair to PKCS12 keystore") }

        assertNotNull(mockStorage.get())
    }

    @Test
    fun `test register handles PIN collection exception`() = runTest {
        val attestation = Attestation.None

        appPinConfig.pinCollector = { throw RuntimeException("PIN collection failed") }

        val result = authenticator.register(context, attestation)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("PIN collection failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test register handles key generation exception`() = runTest {
        val attestation = Attestation.None

        appPinConfig.pinCollector = { testPin }
        appPinConfig.keystoreType = "INVALID_TYPE" // Force failure in key generation

        val result = authenticator.register(context, attestation)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is KeyStoreException)
    }

    @Test
    fun `test authenticate success with generated key pair`() = runTest {

        appPinConfig.pinCollector = { testPin }

        // Register first
        authenticator.register(context, Attestation.None)

        // Then authenticate
        val result = authenticator.authenticate(context)

        assertTrue(result.isSuccess)
        val (privateKey, cryptoObject) = result.getOrThrow()
        assertNotNull(privateKey)
        assertNull(cryptoObject)

        verify { mockLogger.d("AppPinAuthenticator: Starting authentication") }
        verify { mockLogger.d("AppPinAuthenticator: Using generated key pair for authentication") }
    }

    @Test
    fun `test authenticate success with existing key from storage`() = runTest {
        appPinConfig.pinCollector = { testPin }

        // Register first
        authenticator.register(context, Attestation.None)

        // Then authenticate
        val result = authenticator.authenticate(context)

        assertTrue(result.isSuccess)
        val (privateKey, cryptoObject) = result.getOrThrow()
        assertNotNull(privateKey)
        assertNull(cryptoObject)

        // Not start the authentication again without the private key cached
        val result2 = authenticator.authenticate(context)
        assertTrue(result2.isSuccess)

        verify { mockLogger.d("AppPinAuthenticator: Starting authentication") }
    }

    @Test
    fun `test authenticate with PIN retry on invalid credentials`() = runTest {
        val invalidPin = "wrong".toCharArray()
        val validPin = "123456".toCharArray()

        appPinConfig.pinCollector = { validPin }

        // Register first
        authenticator.register(context, Attestation.None)

        // Then authenticate
        val result = authenticator.authenticate(context)

        assertTrue(result.isSuccess)

        appPinConfig.pinCollector = { invalidPin }

        val authenticator = AppPinAuthenticator(appPinConfig)
        val result2 = authenticator.authenticate(context)

        assertTrue(result2.isFailure)
        assertTrue(result2.exceptionOrNull() is InvalidCredentialException)

    }

    @Test
    fun `test authenticate with PIN collection cancellation`() = runTest {

        appPinConfig.pinCollector = { throw RuntimeException("User cancelled") }

        val result = authenticator.authenticate(context)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AbortException)
        assertEquals("User cancelled the operation", result.exceptionOrNull()?.message)

        verify { mockLogger.w("AppPinAuthenticator: PIN collection cancelled by user", any()) }
    }

    @Test
    fun `test authenticate with file not found exception`() = runTest {
        appPinConfig.pinCollector = { testPin }
        appPinConfig.storage = {
            object : Storage<ByteArray> {
                override suspend fun save(item: ByteArray) {
                }

                override suspend fun get(): ByteArray? {
                    throw FileNotFoundException("KeyStore file not found")
                }

                override suspend fun delete() {
                }
            }
        }

        val result = authenticator.authenticate(context)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeviceNotRegisteredException)
        assertEquals("KeyStore file not found", result.exceptionOrNull()?.message)

        verify { mockLogger.w("AppPinAuthenticator: KeyStore file not found", any()) }
    }

    @Test
    fun `test authenticate with PIN retry limit exceeded`() = runTest {
        val invalidPin = "wrong".toCharArray()

        var count = 0
        appPinConfig.pinRetry = 2
        appPinConfig.pinCollector  {
            count++
            invalidPin
        }

        appPinConfig.storage = {
            object : Storage<ByteArray> {
                override suspend fun save(item: ByteArray) {
                }

                override suspend fun get(): ByteArray? {
                    throw UnrecoverableKeyException("Invalid PIN")
                }

                override suspend fun delete() {
                }
            }
        }

        val result = authenticator.authenticate(context)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidCredentialException)

        verify { mockLogger.w("AppPinAuthenticator: PIN retry limit exceeded") }
        assertEquals(2, count) // Ensure pinCollector was called twice
    }

    @Test
    fun `test isSupported returns true for Attestation None`() {
        val result = authenticator.isSupported(context, Attestation.None)
        assertTrue(result)
    }

    @Test
    fun `test isSupported returns false for Attestation Default`() {
        val attestation = Attestation.Default("challenge".toByteArray())
        val result = authenticator.isSupported(context, attestation)
        assertFalse(result)
    }

    @Test
    fun `test deleteKeys calls storage delete`() = runTest {
        mockStorage.save("1234".toByteArray())
        authenticator.deleteKeys()
        assertNull(mockStorage.get())
    }

    @Test
    fun `test register with different keystore types`() = runTest {

        appPinConfig.keystoreType = "BKS"

        appPinConfig.pinCollector =  { testPin }
        val result = authenticator.register(context, Attestation.None)

        assertTrue(result.isSuccess)
        assertNotNull(mockStorage.get())
        verify { mockLogger.d("AppPinAuthenticator: Persisting key pair to BKS keystore") }
    }

    @Test
    fun `test authenticate clears key pair after use`() = runTest {

        // Setup registered key pair
        appPinConfig.pinCollector =  { testPin }

        // Register and authenticate
        authenticator.register(context, Attestation.None)
        val result1 = authenticator.authenticate(context)
        assertTrue(result1.isSuccess)

        // Second authenticate should not use cached key pair
        authenticator.authenticate(context)
        // This will likely fail in our mock setup, but that's expected
        // The important thing is that it tries to get from storage, not cache

        //Keystore file is created
        assertNotNull(mockStorage.get())

        verify { mockLogger.d("AppPinAuthenticator: Using generated key pair for authentication") }
        verify { mockLogger.d("AppPinAuthenticator: Retrieving existing private key") }
    }

}
