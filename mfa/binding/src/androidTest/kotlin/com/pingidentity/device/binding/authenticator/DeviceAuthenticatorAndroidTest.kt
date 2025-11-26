/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.CryptoKey
import com.pingidentity.device.binding.UserKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DeviceAuthenticatorAndroidTest {

    private lateinit var context: Context
    private lateinit var testAuthenticator: DeviceAuthenticator
    private lateinit var testKeyPair: KeyPair
    private lateinit var testUserKey: UserKey
    private lateinit var cryptoKey: CryptoKey
    private val testUserId = "test-user-123"
    private val testChallenge = "test-challenge"
    private val testKid = "test-kid"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        ContextProvider.init(context)
        cryptoKey = CryptoKey(testUserId)

        // Clean up any existing keys
        cleanupKeystore()

        // Generate RSA key pair using Android KeyStore
        testKeyPair = generateAndroidKeyStoreKeyPair()

        testUserKey = UserKey(
            id = "test-key-id",
            userId = testUserId,
            userName = "Test User",
            kid = testKid,
            authType = DeviceBindingAuthenticationType.NONE
        )

        testAuthenticator = TestDeviceAuthenticator()

    }

    @After
    fun tearDown() {
        cleanupKeystore()
    }

    @Test
    fun testSignWithSigningParametersCreatesValidJWT() {
        val now = Instant.now()
        val expiration = now.plusSeconds(3600) // 1 hour later

        val signingParams = SigningParameters(
            context = context,
            algorithm = "RS256",
            keyPair = testKeyPair,
            signature = null, // Use real AndroidKeyStore signing instead of mock
            kid = testKid,
            userId = testUserId,
            challenge = testChallenge,
            issueTime = now,
            notBeforeTime = now,
            expiration = expiration,
            attestation = Attestation.None
        )

        val jwtString = testAuthenticator.sign(signingParams)

        // Verify JWT structure
        assertNotNull(jwtString)
        assertTrue(jwtString.contains("."))

        // Parse and verify JWT claims
        val signedJWT = SignedJWT.parse(jwtString)
        val claims = signedJWT.jwtClaimsSet

        assertEquals(testUserId, claims.subject)
        assertEquals(context.packageName, claims.issuer)
        assertEquals(testChallenge, claims.getStringClaim("challenge"))
        assertEquals("android", claims.getStringClaim("platform"))
        assertNotNull(claims.getIntegerClaim("android-version"))
        assertEquals(expiration.epochSecond, claims.expirationTime.time / 1000)
        assertEquals(now.epochSecond, claims.issueTime.time / 1000)
        assertEquals(now.epochSecond, claims.notBeforeTime.time / 1000)

        // Verify JWT header
        val header = signedJWT.header
        assertEquals(JWSAlgorithm.RS256, header.algorithm)
        assertEquals(testKid, header.keyID)
        assertNotNull(header.jwk)

        // Verify signature can be verified with the public key
        assertTrue(signedJWT.verify(com.nimbusds.jose.crypto.RSASSAVerifier(testKeyPair.publicKey)))
    }

    @Test
    fun testSignWithAttestationIncludesCertificateChain() {
        val now = Instant.now()
        val signingParams = SigningParameters(
            context = context,
            algorithm = "RS256",
            keyPair = testKeyPair,
            signature = null,
            kid = testKid,
            userId = testUserId,
            challenge = testChallenge,
            issueTime = now,
            notBeforeTime = now,
            expiration = now.plusSeconds(3600),
            attestation = Attestation.Default("test-challenge".toByteArray())
        )

        val jwtString = testAuthenticator.sign(signingParams)
        val signedJWT = SignedJWT.parse(jwtString)
        val jwk = signedJWT.header.jwk as RSAKey

        // Verify certificate chain is included (Android KeyStore provides attestation)
        assertNotNull(jwk.x509CertChain)
        assertTrue(jwk.x509CertChain.isNotEmpty())

        // Verify the certificate chain contains real Android KeyStore certificates
        val certificates = jwk.x509CertChain
        certificates.forEach { cert ->
            assertNotNull(cert)
        }

        // Verify signature can be verified with the public key
        assertTrue(signedJWT.verify(com.nimbusds.jose.crypto.RSASSAVerifier(testKeyPair.publicKey)))
    }

    private fun generateAndroidKeyStoreKeyPair(): KeyPair {

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            cryptoKey.keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setAttestationChallenge("test-attestation-challenge".toByteArray())
            .build()

        val javaKeyPair = cryptoKey.create(keyGenParameterSpec, useAndroidKeyStore = true)

        return KeyPair(
            publicKey = javaKeyPair.public as RSAPublicKey,
            privateKey = javaKeyPair.private,
            keyAlias = cryptoKey.keyAlias
        )
    }

    private fun cleanupKeystore() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(cryptoKey.keyAlias)) {
                keyStore.deleteEntry(cryptoKey.keyAlias)
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    // Test implementation of DeviceAuthenticator for testing purposes
    private inner class TestDeviceAuthenticator : DeviceAuthenticator {
        override val type: DeviceBindingAuthenticationType = DeviceBindingAuthenticationType.NONE

        override suspend fun register(context: Context, attestation: Attestation): Result<KeyPair> {
            return Result.success(testKeyPair)
        }

        override suspend fun authenticate(context: Context): Result<Pair<PrivateKey, androidx.biometric.BiometricPrompt.CryptoObject?>> {
            return Result.success(Pair(testKeyPair.privateKey, null))
        }

        override suspend fun deleteKeys() {
            // Test implementation
        }
    }
}
