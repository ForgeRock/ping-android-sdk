/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.logger.Logger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Unit tests for the OathAlgorithmHelper class.
 * 
 * These tests verify the code generation functionality for TOTP and HOTP credentials
 * with different algorithms (SHA1, SHA256, SHA512) and digit lengths.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OathAlgorithmHelperTest {
    
    // Mock objects
    private lateinit var mockLogger: Logger
    
    // Test secrets (Base32 encoded)
    private val testSecretSHA1 = "JBSWY3DPEHPK3PXP" // Base32 encoding of "Hello World"
    private val testSecretSHA256 = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP" // Longer for SHA256
    private val testSecretSHA512 = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP" // Longer for SHA512
    
    @Before
    fun setup() {
        // Set up mock logger
        mockLogger = mockk(relaxed = true)
        every { mockLogger.d(any()) } returns Unit
        every { mockLogger.e(any(), any()) } returns Unit
    }

    // TOTP Tests
    
    @Test
    fun `test TOTP code generation with SHA1`() = runTest {
        // Given - Create a TOTP credential with SHA1
        val credential = createTotpCredential(
            secret = testSecretSHA1,
            algorithm = OathAlgorithm.SHA1,
            digits = 6
        )
        
        // When - Generate code
        val codeInfo = OathAlgorithmHelper.generateCode(credential, mockLogger)
        
        // Then - Validate code properties
        assertEquals(6, codeInfo.code.length)
        assertTrue(codeInfo.code.matches(Regex("\\d+")))
        assertTrue(codeInfo.timeRemaining in 0..credential.period)
        assertEquals(-1, codeInfo.counter)
        assertTrue(codeInfo.progress in 0.0..1.0)
        assertEquals(credential.period, codeInfo.totalPeriod)
    }
    
    @Test
    fun `test TOTP code generation with SHA256`() = runTest {
        // Given - Create a TOTP credential with SHA256
        val credential = createTotpCredential(
            secret = testSecretSHA256,
            algorithm = OathAlgorithm.SHA256,
            digits = 6
        )
        
        // When - Generate code
        val codeInfo = OathAlgorithmHelper.generateCode(credential, mockLogger)
        
        // Then - Validate code properties
        assertEquals(6, codeInfo.code.length)
        assertTrue(codeInfo.code.matches(Regex("\\d+")))
        assertTrue(codeInfo.timeRemaining in 0..credential.period)
        assertEquals(-1, codeInfo.counter)
        assertTrue(codeInfo.progress in 0.0..1.0)
        assertEquals(credential.period, codeInfo.totalPeriod)
    }
    
    @Test
    fun `test TOTP code generation with SHA512`() = runTest {
        // Given - Create a TOTP credential with SHA512
        val credential = createTotpCredential(
            secret = testSecretSHA512,
            algorithm = OathAlgorithm.SHA512,
            digits = 6
        )
        
        // When - Generate code
        val codeInfo = OathAlgorithmHelper.generateCode(credential, mockLogger)
        
        // Then - Validate code properties
        assertEquals(6, codeInfo.code.length)
        assertTrue(codeInfo.code.matches(Regex("\\d+")))
        assertTrue(codeInfo.timeRemaining in 0..credential.period)
        assertEquals(-1, codeInfo.counter)
        assertTrue(codeInfo.progress in 0.0..1.0)
        assertEquals(credential.period, codeInfo.totalPeriod)
    }

    @Test
    fun `test TOTP code generation with 8 digits`() = runTest {
        // Given - Create a TOTP credential with 8 digits
        val credential = createTotpCredential(
            secret = testSecretSHA1,
            algorithm = OathAlgorithm.SHA1,
            digits = 8
        )
        
        // When - Generate code
        val codeInfo = OathAlgorithmHelper.generateCode(credential, mockLogger)
        
        // Then - Validate code length
        assertEquals(8, codeInfo.code.length)
    }
    
    @Test
    fun `test TOTP code changes after period expiry`() = runTest {
        // Given - Create two identical TOTP credentials
        val credential1 = createTotpCredential(
            secret = testSecretSHA1,
            algorithm = OathAlgorithm.SHA1,
            digits = 6
        )
        
        // When - Generate a code and simulate waiting for period to expire
        val codeInfo1 = OathAlgorithmHelper.generateCode(credential1, mockLogger)
        
        // Force time to advance by setting a different period (this changes the time counter)
        val credential2 = createTotpCredential(
            secret = testSecretSHA1,
            algorithm = OathAlgorithm.SHA1,
            digits = 6,
            period = 29 // Different period changes the calculated counter
        )
        val codeInfo2 = OathAlgorithmHelper.generateCode(credential2, mockLogger)
        
        // Then - Codes should be different
        assertNotEquals(codeInfo1.code, codeInfo2.code)
    }
    
    @Test
    fun `test TOTP code remains stable during period`() = runTest {
        // Given - Create a TOTP credential
        val credential = createTotpCredential(
            secret = testSecretSHA1,
            algorithm = OathAlgorithm.SHA1,
            digits = 6
        )
        
        // When - Generate two codes in quick succession (same period)
        val codeInfo1 = OathAlgorithmHelper.generateCode(credential, mockLogger)
        val codeInfo2 = OathAlgorithmHelper.generateCode(credential, mockLogger)
        
        // Then - Codes should be the same
        assertEquals(codeInfo1.code, codeInfo2.code)
    }

    @Test
    fun `test TOTP with custom period`() = runTest {
        // Given - Create a TOTP credential with custom period
        val customPeriod = 60
        val credential = createTotpCredential(
            secret = testSecretSHA1,
            algorithm = OathAlgorithm.SHA1,
            digits = 6,
            period = customPeriod
        )
        
        // When - Generate code
        val codeInfo = OathAlgorithmHelper.generateCode(credential, mockLogger)
        
        // Then - Period should be honored
        assertEquals(customPeriod, codeInfo.totalPeriod)
        assertTrue(codeInfo.timeRemaining in 0..customPeriod)
    }

    // HOTP Tests
    
    @Test
    fun `test HOTP code generation with SHA1`() = runTest {
        // Given - Create a HOTP credential with SHA1 and initial counter 0
        val credential = createHotpCredential(
            secret = testSecretSHA1,
            algorithm = OathAlgorithm.SHA1,
            digits = 6,
            counter = 0
        )
        
        // When - Generate code
        val codeInfo = OathAlgorithmHelper.generateCode(credential, mockLogger)
        
        // Then - Validate code properties
        assertEquals(6, codeInfo.code.length)
        assertTrue(codeInfo.code.matches(Regex("\\d+")))
        assertEquals(-1, codeInfo.timeRemaining)
        assertEquals(1, codeInfo.counter) // Counter incremented
        assertEquals(0.0, codeInfo.progress, 0.0)
        assertEquals(0, codeInfo.totalPeriod)
    }
    
    @Test
    fun `test HOTP code generation with SHA256`() = runTest {
        // Given - Create a HOTP credential with SHA256
        val credential = createHotpCredential(
            secret = testSecretSHA256,
            algorithm = OathAlgorithm.SHA256,
            digits = 6,
            counter = 1
        )
        
        // When - Generate code
        val codeInfo = OathAlgorithmHelper.generateCode(credential, mockLogger)
        
        // Then - Validate code properties
        assertEquals(6, codeInfo.code.length)
        assertTrue(codeInfo.code.matches(Regex("\\d+")))
        assertEquals(-1, codeInfo.timeRemaining)
        assertEquals(2, codeInfo.counter) // Counter incremented
        assertEquals(0.0, codeInfo.progress, 0.0)
        assertEquals(0, codeInfo.totalPeriod)
    }
    
    @Test
    fun `test HOTP code generation with SHA512`() = runTest {
        // Given - Create a HOTP credential with SHA512
        val credential = createHotpCredential(
            secret = testSecretSHA512,
            algorithm = OathAlgorithm.SHA512,
            digits = 6,
            counter = 2
        )
        
        // When - Generate code
        val codeInfo = OathAlgorithmHelper.generateCode(credential, mockLogger)
        
        // Then - Validate code properties
        assertEquals(6, codeInfo.code.length)
        assertTrue(codeInfo.code.matches(Regex("\\d+")))
        assertEquals(-1, codeInfo.timeRemaining)
        assertEquals(3, codeInfo.counter) // Counter incremented
        assertEquals(0.0, codeInfo.progress, 0.0)
        assertEquals(0, codeInfo.totalPeriod)
    }

    @Test
    fun `test HOTP code generation with 8 digits`() = runTest {
        // Given - Create a HOTP credential with 8 digits
        val credential = createHotpCredential(
            secret = testSecretSHA1,
            algorithm = OathAlgorithm.SHA1,
            digits = 8,
            counter = 3
        )
        
        // When - Generate code
        val codeInfo = OathAlgorithmHelper.generateCode(credential, mockLogger)
        
        // Then - Validate code length
        assertEquals(8, codeInfo.code.length)
    }
    
    @Test
    fun `test HOTP code changes with different counter values`() = runTest {
        // Given - Create two identical HOTP credentials with different counters
        val credential1 = createHotpCredential(
            secret = testSecretSHA1,
            algorithm = OathAlgorithm.SHA1,
            digits = 6,
            counter = 4
        )
        
        val credential2 = createHotpCredential(
            secret = testSecretSHA1,
            algorithm = OathAlgorithm.SHA1,
            digits = 6,
            counter = 5
        )
        
        // When - Generate codes
        val codeInfo1 = OathAlgorithmHelper.generateCode(credential1, mockLogger)
        val codeInfo2 = OathAlgorithmHelper.generateCode(credential2, mockLogger)
        
        // Then - Codes should be different
        assertNotEquals(codeInfo1.code, codeInfo2.code)
    }
    
    @Test
    fun `test HOTP code generation increments counter`() = runTest {
        // Given - Create a HOTP credential
        val initialCounter = 10L
        val credential = createHotpCredential(
            secret = testSecretSHA1,
            algorithm = OathAlgorithm.SHA1,
            digits = 6,
            counter = initialCounter
        )
        
        // When - Generate code
        val codeInfo = OathAlgorithmHelper.generateCode(credential, mockLogger)
        
        // Then - Counter should be incremented
        assertEquals(initialCounter + 1, codeInfo.counter)
    }

    // Helper methods to create test credentials
    
    private fun createTotpCredential(
        secret: String,
        algorithm: OathAlgorithm,
        digits: Int,
        period: Int = 30
    ): OathCredential {
        return OathCredential(
            issuer = "Test Issuer",
            displayIssuer = "Test Issuer",
            accountName = "test@example.com",
            displayAccountName = "test@example.com",
            oathType = OathType.TOTP,
            secret = secret,
            oathAlgorithm = algorithm,
            digits = digits,
            period = period,
            createdAt = Date()
        )
    }
    
    private fun createHotpCredential(
        secret: String,
        algorithm: OathAlgorithm,
        digits: Int,
        counter: Long
    ): OathCredential {
        return OathCredential(
            issuer = "Test Issuer",
            displayIssuer = "Test Issuer",
            accountName = "test@example.com",
            displayAccountName = "test@example.com",
            oathType = OathType.HOTP,
            secret = secret,
            oathAlgorithm = algorithm,
            digits = digits,
            counter = counter,
            createdAt = Date()
        )
    }
}
