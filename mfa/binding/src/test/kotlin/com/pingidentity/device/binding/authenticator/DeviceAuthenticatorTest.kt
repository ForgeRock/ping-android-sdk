/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import android.app.Application
import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.test.core.app.ApplicationProvider
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.SignedJWT
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.UserKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class DeviceAuthenticatorTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }
    private lateinit var testAuthenticator: DeviceAuthenticator
    private lateinit var testKeyPair: KeyPair
    private lateinit var testUserKey: UserKey
    private val testUserId = "test-user-123"
    private val testChallenge = "test-challenge"
    private val testKid = "test-kid"

    @BeforeTest
    fun setUp() {
        ContextProvider.init(context)

        // Generate a real RSA key pair for testing
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val javaKeyPair = keyPairGenerator.generateKeyPair()

        testKeyPair = KeyPair(
            publicKey = javaKeyPair.public as RSAPublicKey,
            privateKey = javaKeyPair.private,
            keyAlias = "test-alias"
        )

        testUserKey = UserKey(
            id = "test-key-id",
            userId = testUserId,
            userName = "Test User",
            kid = testKid,
            authType = DeviceBindingAuthenticationType.NONE
        )

        testAuthenticator = TestDeviceAuthenticator()
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }


    @Test
    fun `test sign with UserKeySigningParameters creates valid JWT`() {
        val now = Instant.now()
        val expiration = now.plusSeconds(3600)
        val customClaims = mapOf(
            "custom_claim" to "custom_value",
            "numeric_claim" to 42
        )

        val userKeySigningParams = UserKeySigningParameters(
            context = context,
            algorithm = "RS256",
            userKey = testUserKey,
            privateKey = testKeyPair.privateKey,
            signature = null,
            challenge = testChallenge,
            issueTime = now,
            notBeforeTime = now,
            expiration = expiration,
            customClaims = customClaims
        )

        val jwtString = testAuthenticator.sign(userKeySigningParams)

        // Verify JWT structure
        assertNotNull(jwtString)
        assertTrue(jwtString.contains("."))

        // Parse and verify JWT claims
        val signedJWT = SignedJWT.parse(jwtString)
        val claims = signedJWT.jwtClaimsSet

        assertEquals(testUserId, claims.subject)
        assertEquals(context.packageName, claims.issuer)
        assertEquals(testChallenge, claims.getStringClaim("challenge"))
        assertEquals("custom_value", claims.getStringClaim("custom_claim"))
        assertEquals(42, claims.getIntegerClaim("numeric_claim"))
        assertEquals(expiration.epochSecond, claims.expirationTime.time / 1000)

        // Verify JWT header
        val header = signedJWT.header
        assertEquals(JWSAlgorithm.RS256, header.algorithm)
        assertEquals(testKid, header.keyID)
    }


    @Test
    fun `test sign with CryptoObject signature`() {
        val mockSignature = mockk<Signature>(relaxed = true)
        val mockCryptoObject = mockk<BiometricPrompt.CryptoObject>()
        every { mockCryptoObject.signature } returns mockSignature

        val now = Instant.now()
        val signingParams = SigningParameters(
            context = context,
            algorithm = "RS256",
            keyPair = testKeyPair,
            signature = mockSignature,
            kid = testKid,
            userId = testUserId,
            challenge = testChallenge,
            issueTime = now,
            notBeforeTime = now,
            expiration = now.plusSeconds(3600)
        )

        val jwtString = testAuthenticator.sign(signingParams)

        // Verify JWT was created successfully
        assertNotNull(jwtString)
        assertTrue(jwtString.contains("."))
    }

    @Test
    fun `test KeyPair data class properties`() {
        assertEquals(testKeyPair.publicKey, testKeyPair.publicKey)
        assertEquals(testKeyPair.privateKey, testKeyPair.privateKey)
        assertEquals("test-alias", testKeyPair.keyAlias)

        // Test keyAlias modification
        testKeyPair.keyAlias = "new-alias"
        assertEquals("new-alias", testKeyPair.keyAlias)
    }

    @Test
    fun `test SigningParameters data class properties`() {
        val now = Instant.now()
        val expiration = now.plusSeconds(3600)
        val mockSignature = mockk<Signature>()

        val signingParams = SigningParameters(
            context = context,
            algorithm = "RS256",
            keyPair = testKeyPair,
            signature = mockSignature,
            kid = testKid,
            userId = testUserId,
            challenge = testChallenge,
            issueTime = now,
            notBeforeTime = now,
            expiration = expiration,
            attestation = Attestation.None
        )

        assertEquals(context, signingParams.context)
        assertEquals("RS256", signingParams.algorithm)
        assertEquals(testKeyPair, signingParams.keyPair)
        assertEquals(mockSignature, signingParams.signature)
        assertEquals(testKid, signingParams.kid)
        assertEquals(testUserId, signingParams.userId)
        assertEquals(testChallenge, signingParams.challenge)
        assertEquals(now, signingParams.issueTime)
        assertEquals(now, signingParams.notBeforeTime)
        assertEquals(expiration, signingParams.expiration)
        assertEquals(Attestation.None, signingParams.attestation)
    }

    @Test
    fun `test UserKeySigningParameters data class properties`() {
        val now = Instant.now()
        val expiration = now.plusSeconds(3600)
        val customClaims = mapOf("key" to "value")

        val userKeySigningParams = UserKeySigningParameters(
            context = context,
            algorithm = "RS256",
            userKey = testUserKey,
            privateKey = testKeyPair.privateKey,
            signature = null,
            challenge = testChallenge,
            issueTime = now,
            notBeforeTime = now,
            expiration = expiration,
            customClaims = customClaims
        )

        assertEquals(context, userKeySigningParams.context)
        assertEquals("RS256", userKeySigningParams.algorithm)
        assertEquals(testUserKey, userKeySigningParams.userKey)
        assertEquals(testKeyPair.privateKey, userKeySigningParams.privateKey)
        assertNull(userKeySigningParams.signature)
        assertEquals(testChallenge, userKeySigningParams.challenge)
        assertEquals(now, userKeySigningParams.issueTime)
        assertEquals(now, userKeySigningParams.notBeforeTime)
        assertEquals(expiration, userKeySigningParams.expiration)
        assertEquals(customClaims, userKeySigningParams.customClaims)
    }

    @Test
    fun `test UserKeySigningParameters with empty custom claims`() {
        val now = Instant.now()
        val userKeySigningParams = UserKeySigningParameters(
            context = context,
            algorithm = "RS256",
            userKey = testUserKey,
            privateKey = testKeyPair.privateKey,
            signature = null,
            challenge = testChallenge,
            issueTime = now,
            notBeforeTime = now,
            expiration = now.plusSeconds(3600)
        )

        assertTrue(userKeySigningParams.customClaims.isEmpty())
    }

    @Test
    fun `test default isSupported returns true`() {
        assertTrue(testAuthenticator.isSupported(context, Attestation.None))
        assertTrue(testAuthenticator.isSupported(context, Attestation.Default("test".toByteArray())))
    }

    @Test
    fun `test sign with different algorithms`() {
        val algorithms = listOf("RS256", "RS384", "RS512")

        algorithms.forEach { algorithm ->
            val now = Instant.now()
            val signingParams = SigningParameters(
                context = context,
                algorithm = algorithm,
                keyPair = testKeyPair,
                signature = null,
                kid = testKid,
                userId = testUserId,
                challenge = testChallenge,
                issueTime = now,
                notBeforeTime = now,
                expiration = now.plusSeconds(3600)
            )

            val jwtString = testAuthenticator.sign(signingParams)
            val signedJWT = SignedJWT.parse(jwtString)

            assertEquals(JWSAlgorithm.parse(algorithm), signedJWT.header.algorithm)
        }
    }

    @Test
    fun `test sign preserves all custom claims`() {
        val customClaims = mapOf(
            "string_claim" to "test_value",
            "number_claim" to 123,
            "boolean_claim" to true,
            "nested_claim" to mapOf("inner" to "value")
        )

        val now = Instant.now()
        val userKeySigningParams = UserKeySigningParameters(
            context = context,
            algorithm = "RS256",
            userKey = testUserKey,
            privateKey = testKeyPair.privateKey,
            signature = null,
            challenge = testChallenge,
            issueTime = now,
            notBeforeTime = now,
            expiration = now.plusSeconds(3600),
            customClaims = customClaims
        )

        val jwtString = testAuthenticator.sign(userKeySigningParams)
        val signedJWT = SignedJWT.parse(jwtString)
        val claims = signedJWT.jwtClaimsSet

        assertEquals("test_value", claims.getStringClaim("string_claim"))
        assertEquals(123, claims.getIntegerClaim("number_claim"))
        assertEquals(true, claims.getBooleanClaim("boolean_claim"))
        assertNotNull(claims.getClaim("nested_claim"))
    }

    @Test
    fun `test KeyPair equality and hashCode`() {
        val keyPair1 = testKeyPair.copy()
        val keyPair2 = testKeyPair.copy()

        assertEquals(keyPair1, keyPair2)
        assertEquals(keyPair1.hashCode(), keyPair2.hashCode())
    }

    @Test
    fun `test SigningParameters equality and hashCode`() {
        val now = Instant.now()
        val params1 = SigningParameters(
            context = context,
            algorithm = "RS256",
            keyPair = testKeyPair,
            signature = null,
            kid = testKid,
            userId = testUserId,
            challenge = testChallenge,
            issueTime = now,
            notBeforeTime = now,
            expiration = now.plusSeconds(3600)
        )

        val params2 = params1.copy()

        assertEquals(params1, params2)
        assertEquals(params1.hashCode(), params2.hashCode())
    }

    @Test
    fun `test UserKeySigningParameters equality and hashCode`() {
        val now = Instant.now()
        val params1 = UserKeySigningParameters(
            context = context,
            algorithm = "RS256",
            userKey = testUserKey,
            privateKey = testKeyPair.privateKey,
            signature = null,
            challenge = testChallenge,
            issueTime = now,
            notBeforeTime = now,
            expiration = now.plusSeconds(3600)
        )

        val params2 = params1.copy()

        assertEquals(params1, params2)
        assertEquals(params1.hashCode(), params2.hashCode())
    }

    // Test implementation of DeviceAuthenticator for testing purposes
    private inner class TestDeviceAuthenticator : DeviceAuthenticator {
        override val type: DeviceBindingAuthenticationType = DeviceBindingAuthenticationType.NONE

        override suspend fun register(context: Context, attestation: Attestation): Result<KeyPair> {
            return Result.success(testKeyPair)
        }

        override suspend fun authenticate(context: Context): Result<Pair<PrivateKey, BiometricPrompt.CryptoObject?>> {
            return Result.success(Pair(testKeyPair.privateKey, null))
        }

        override suspend fun deleteKeys() {
            // Test implementation
        }
    }
}
