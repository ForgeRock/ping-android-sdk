/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding

import android.security.keystore.KeyProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAKeyGenParameterSpec

class CryptoKeyTest {

    private lateinit var cryptoKey: CryptoKey
    private val testKeyId = "test-key-id"

    @Before
    fun setUp() {
        cryptoKey = CryptoKey(testKeyId)

        // Mock static methods
        mockkStatic(KeyStore::class)
        mockkStatic(KeyPairGenerator::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(KeyStore::class)
        unmockkStatic(KeyPairGenerator::class)
    }

    @Test
    fun `test keyAlias generation`() {
        val keyAlias = cryptoKey.keyAlias

        assertNotNull(keyAlias)
        assertTrue(keyAlias.isNotEmpty())
    }

    @Test
    fun `test create with AndroidKeyStore`() {
        val mockKeyPairGenerator = mockk<KeyPairGenerator>()
        val mockKeyPair = mockk<KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val mockSpec = RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4)

        every {
            KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )
        } returns mockKeyPairGenerator
        every { mockKeyPairGenerator.initialize(mockSpec) } returns Unit
        every { mockKeyPairGenerator.generateKeyPair() } returns mockKeyPair
        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey

        val result = cryptoKey.create(mockSpec, true)

        assertNotNull(result)
        assertTrue(result.public is RSAPublicKey)
        assertEquals(mockPrivateKey, result.private)
    }

    @Test
    fun `test create without AndroidKeyStore`() {
        val mockKeyPairGenerator = mockk<KeyPairGenerator>()
        val mockKeyPair = mockk<KeyPair>()
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        val mockSpec = RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4)

        every { KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA) } returns mockKeyPairGenerator
        every { mockKeyPairGenerator.initialize(mockSpec) } returns Unit
        every { mockKeyPairGenerator.generateKeyPair() } returns mockKeyPair
        every { mockKeyPair.public } returns mockPublicKey
        every { mockKeyPair.private } returns mockPrivateKey

        val result = cryptoKey.create(mockSpec, false)

        assertNotNull(result)
        assertTrue(result.public is RSAPublicKey)
        assertEquals(mockPrivateKey, result.private)
    }

    @Test
    fun `test privateKey retrieval when key exists`() {
        val mockKeyStore = mockk<KeyStore>()
        val mockPrivateKey = mockk<PrivateKey>()

        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore
        every { mockKeyStore.load(null) } returns Unit
        every { mockKeyStore.getKey(any(), null) } returns mockPrivateKey

        val result = cryptoKey.privateKey

        assertEquals(mockPrivateKey, result)
    }

    @Test
    fun `test privateKey retrieval when key does not exist`() {
        val mockKeyStore = mockk<KeyStore>()

        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore
        every { mockKeyStore.load(null) } returns Unit
        every { mockKeyStore.getKey(any(), null) } returns null

        val result = cryptoKey.privateKey

        assertNull(result)
    }

    @Test
    fun `test certificateChains retrieval`() {
        val mockKeyStore = mockk<KeyStore>()
        val mockCertificates = arrayOf<Certificate>(mockk())

        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore
        every { mockKeyStore.load(null) } returns Unit
        every { mockKeyStore.getCertificateChain(any()) } returns mockCertificates

        val result = cryptoKey.certificateChains

        assertArrayEquals(mockCertificates, result)
    }

    @Test
    fun `test delete key`() {
        val mockKeyStore = mockk<KeyStore>(relaxed = true)

        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore
        every { mockKeyStore.load(null) } returns Unit

        cryptoKey.delete()
    }

    @Test
    fun `test constants values`() {
        assertEquals(2048, cryptoKey.keySize)
        assertEquals(5, cryptoKey.timeout)
    }

    @Test
    fun `test keyAlias is consistent for same keyId`() {

        val cryptoKey1 = CryptoKey(testKeyId)
        val cryptoKey2 = CryptoKey(testKeyId)

        assertEquals(cryptoKey1.keyAlias, cryptoKey2.keyAlias)
    }

    @Test
    fun `test CryptoKeyAware interface implementation`() {
        val cryptoKeyAware = object : CryptoKeyAware {
            override var cryptoKey: CryptoKey = CryptoKey("test")
        }

        assertNotNull(cryptoKeyAware.cryptoKey)
        assertEquals(
            "n4bQgYhMfWWaL-qgxVrQFaO_TxsrC4Is0V1sFbDwCgg",
            cryptoKeyAware.cryptoKey.keyAlias
        )
    }
}

