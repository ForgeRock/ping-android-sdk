/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.CryptoKey
import com.pingidentity.device.binding.authenticator.exception.DeviceNotRegisteredException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class NoneAuthenticatorTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }
    private lateinit var authenticator: NoneAuthenticator
    private lateinit var mockCryptoKey: CryptoKey

    @BeforeTest
    fun setUp() {
        ContextProvider.init(context)

        mockCryptoKey = spyk(CryptoKey("test-alias"))
        authenticator = NoneAuthenticator()
        authenticator.cryptoKey = mockCryptoKey
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(ContextProvider)
    }

    @Test
    fun `test type returns correct authentication type`() {
        assertEquals(DeviceBindingAuthenticationType.NONE, authenticator.type)
    }

    @Test
    fun `test register success with attestation challenge`() = runTest {
        val mockKeyPair = mockk<java.security.KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val keyGenSpec = slot<KeyGenParameterSpec>()
        val attestation = Attestation.Default("test-challenge".toByteArray())

        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey
        every { mockCryptoKey.create(capture(keyGenSpec)) } returns mockKeyPair
        every { mockCryptoKey.keyAlias } returns "test-alias"

        val result = authenticator.register(context, attestation)

        assertTrue(result.isSuccess)
        val keyPair = result.getOrThrow()
        assertEquals(mockPublicKey, keyPair.publicKey)
        assertEquals(mockPrivateKey, keyPair.privateKey)
        assertEquals("test-alias", keyPair.keyAlias)

        // Verify the captured spec has the correct attestation challenge
        assertEquals("test-challenge".toByteArray().contentToString(),
                    keyGenSpec.captured.attestationChallenge?.contentToString())
    }

    @Test
    fun `test register success without attestation challenge`() = runTest {
        val mockKeyPair = mockk<java.security.KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val keyGenSpec = slot<KeyGenParameterSpec>()
        val attestation = Attestation.None

        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey
        every { mockCryptoKey.create(capture(keyGenSpec)) } returns mockKeyPair
        every { mockCryptoKey.keyAlias } returns "test-alias"

        val result = authenticator.register(context, attestation)

        assertTrue(result.isSuccess)
        val keyPair = result.getOrThrow()
        assertEquals(mockPublicKey, keyPair.publicKey)
        assertEquals(mockPrivateKey, keyPair.privateKey)
        assertEquals("test-alias", keyPair.keyAlias)

        // Verify no attestation challenge is set for None type
        assertNull(keyGenSpec.captured.attestationChallenge)
    }

    @Test
    fun `test register handles exception properly`() = runTest {
        val attestation = Attestation.None

        every { mockCryptoKey.create(any()) } throws RuntimeException("Key generation failed")

        val result = authenticator.register(context, attestation)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Key generation failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test register handles key cast exception`() = runTest {
        val mockKeyPair = mockk<java.security.KeyPair>()
        val mockPublicKey = mockk<java.security.PublicKey>() // Not RSAPublicKey
        val mockPrivateKey = mockk<PrivateKey>()
        val attestation = Attestation.None

        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey
        every { mockCryptoKey.create(any()) } returns mockKeyPair
        every { mockCryptoKey.keyAlias } returns "test-alias"

        val result = authenticator.register(context, attestation)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ClassCastException)
    }

    @Test
    fun `test authenticate success when private key exists`() = runTest {
        val mockPrivateKey = mockk<PrivateKey>()

        every { mockCryptoKey.privateKey } returns mockPrivateKey

        val result = authenticator.authenticate(context)

        assertTrue(result.isSuccess)
        val (privateKey, cryptoObject) = result.getOrThrow()
        assertEquals(mockPrivateKey, privateKey)
        assertNull(cryptoObject)
    }

    @Test
    fun `test authenticate fails when private key does not exist`() = runTest {
        every { mockCryptoKey.privateKey } returns null

        val result = authenticator.authenticate(context)

        assertTrue(result.isFailure)
        assertFailsWith<DeviceNotRegisteredException> {
            result.getOrThrow()
        }
        assertEquals("Device is not registered", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test deleteKeys calls cryptoKey delete`() = runTest {
        every { mockCryptoKey.delete() } returns Unit

        authenticator.deleteKeys()

        verify { mockCryptoKey.delete() }
    }

    @Test
    fun `test deleteKeys handles exception`() = runTest {
        every { mockCryptoKey.delete() } throws RuntimeException("Delete failed")

        // Should not throw exception, as deleteKeys doesn't wrap in Result
        try {
            authenticator.deleteKeys()
        } catch (e: Exception) {
            assertEquals("Delete failed", e.message)
        }

        verify { mockCryptoKey.delete() }
    }

    @Test
    fun `test cryptoKey property can be set and accessed`() {
        val newCryptoKey = CryptoKey("new-alias")
        authenticator.cryptoKey = newCryptoKey

        assertEquals(newCryptoKey, authenticator.cryptoKey)
        assertEquals("rpOHKilXojAHOSHPdtJ_DSuxMaJwbPyneST5Hl7OAQw", authenticator.cryptoKey.keyAlias)
    }

    @Test
    fun `test register with empty attestation challenge in Default`() = runTest {
        val mockKeyPair = mockk<java.security.KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val keyGenSpec = slot<KeyGenParameterSpec>()
        val attestation = Attestation.Default(byteArrayOf())

        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey
        every { mockCryptoKey.create(capture(keyGenSpec)) } returns mockKeyPair
        every { mockCryptoKey.keyAlias } returns "test-alias"

        val result = authenticator.register(context, attestation)

        assertTrue(result.isSuccess)
        // Verify empty challenge is treated as null
        assertTrue(keyGenSpec.captured.attestationChallenge.isEmpty())
    }

    @Test
    fun `test multiple register calls with different attestations`() = runTest {
        val mockKeyPair = mockk<java.security.KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val keyGenSpec1 = slot<KeyGenParameterSpec>()
        val keyGenSpec2 = slot<KeyGenParameterSpec>()

        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey
        every { mockCryptoKey.keyAlias } returns "test-alias"

        // First registration with challenge
        every { mockCryptoKey.create(capture(keyGenSpec1)) } returns mockKeyPair
        val attestation1 = Attestation.Default("challenge1".toByteArray())
        val result1 = authenticator.register(context, attestation1)
        assertTrue(result1.isSuccess)

        // Second registration without challenge
        every { mockCryptoKey.create(capture(keyGenSpec2)) } returns mockKeyPair
        val attestation2 = Attestation.None
        val result2 = authenticator.register(context, attestation2)
        assertTrue(result2.isSuccess)

        // Verify the attestation challenges were set correctly
        assertEquals("challenge1".toByteArray().contentToString(),
                    keyGenSpec1.captured.attestationChallenge?.contentToString())
        assertNull(keyGenSpec2.captured.attestationChallenge)
    }
}
