/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.mfa.commons.exception.MfaException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.clearMocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID

/**
 * Unit tests for the OathService class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OathServiceTest {
    
    // Mock objects
    private lateinit var mockStorage: OathStorage
    private lateinit var configWithCache: com.pingidentity.mfa.commons.MfaConfiguration
    private lateinit var configWithoutCache: com.pingidentity.mfa.commons.MfaConfiguration
    
    // Test subjects
    private lateinit var oathServiceWithCache: OathService
    private lateinit var oathServiceWithoutCache: OathService
    
    // Test data
    private lateinit var testCredential: OathCredential
    private lateinit var testCredentialId: String
    
    @Before
    fun setup() {
        // Set up mock storage client
        mockStorage = mockk()
        
        // Create mock configurations
        val mockLogger = mockk<com.pingidentity.logger.Logger>(relaxed = true)
        
        configWithCache = mockk<com.pingidentity.mfa.commons.MfaConfiguration>().apply {
            every { enableCredentialCache } returns true
            every { logger } returns mockLogger
        }
        configWithoutCache = mockk<com.pingidentity.mfa.commons.MfaConfiguration>().apply {
            every { enableCredentialCache } returns false
            every { logger } returns mockLogger
        }
        
        // Initialize service variants with mock storage
        oathServiceWithCache = OathService(mockStorage, configWithCache)
        oathServiceWithoutCache = OathService(mockStorage, configWithoutCache)
        
        // Create test credential
        testCredentialId = UUID.randomUUID().toString()
        testCredential = OathCredential(
            id = testCredentialId,
            userId = "test-user",
            resourceId = "test-device",
            issuer = "Test Issuer",
            displayIssuer = "Test Issuer",
            accountName = "test@example.com",
            displayAccountName = "test@example.com",
            oathType = OathType.TOTP,
            secret = "JBSWY3DPEHPK3PXP", // Base32 encoded test secret
            oathAlgorithm = OathAlgorithm.SHA1,
            digits = 6,
            period = 30,
            createdAt = Date()
        )
    }
    
    @Test
    fun `test parse URI creates valid credential`() {
        // Given
        val uri = "otpauth://totp/Test%20Issuer:test@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Test%20Issuer&algorithm=SHA1&digits=6&period=30"
        
        // When
        val credential = oathServiceWithCache.parseUri(uri)
        
        // Then
        assertEquals("Test Issuer", credential.issuer)
        assertEquals("test@example.com", credential.accountName)
        assertEquals(OathType.TOTP, credential.oathType)
        assertEquals("JBSWY3DPEHPK3PXP", credential.secret)
        assertEquals(OathAlgorithm.SHA1, credential.oathAlgorithm)
        assertEquals(6, credential.digits)
        assertEquals(30, credential.period)
    }
    
    @Test
    fun `test format URI creates valid URI string`() {
        // When
        val uri = oathServiceWithCache.formatUri(testCredential)
        
        // Then
        println("Actual URI: $uri")
        assertTrue("URI should start with otpauth://totp/ but was $uri", uri.startsWith("otpauth://totp/"))
        assertTrue("URI should contain Test%20Issuer:test%40example.com but was $uri", uri.contains("Test%20Issuer:test%40example.com"))
        assertTrue("URI should contain secret=JBSWY3DPEHPK3PXP but was $uri", uri.contains("secret=JBSWY3DPEHPK3PXP"))
    }
    
    @Test
    fun `test add credential stores to storage with cache enabled`() {
        // Given
        val credentialSlot = slot<OathCredential>()
        
        every {
            mockStorage.storeOathCredential(capture(credentialSlot))
        } just runs
        
        // When
        val result = oathServiceWithCache.addCredential(testCredential)
        
        // Then
        assertEquals(testCredential, result)
        assertEquals(testCredential.id, credentialSlot.captured.id)
        assertEquals(testCredential.secret, credentialSlot.captured.secret)
        verify(exactly = 1) { 
            mockStorage.storeOathCredential(any()) 
        }
    }
    
    @Test
    fun `test get credential returns from cache when caching enabled`() {
        // Setup credential in cache first by adding it
        // Given
        every {
            mockStorage.storeOathCredential(any())
        } just runs
        
        // Add credential to cache
        oathServiceWithCache.addCredential(testCredential)
        
        // When
        val result = oathServiceWithCache.getCredential(testCredentialId)
        
        // Then
        assertNotNull(result)
        assertEquals(testCredentialId, result?.id)
    }
    
    @Test
    fun `test generate code returns valid code`() {
        // When
        val codeInfo = oathServiceWithCache.generateCode(testCredential)
        
        // Then
        assertNotNull(codeInfo)
        assertNotNull(codeInfo.code)
        assertEquals(6, codeInfo.code.length) // We specified 6 digits in test credential
    }
    
    @Test
    fun `test generate code for credential updates HOTP counter when caching enabled`() {
        // Given
        val hotpCredential = testCredential.copy(
            oathType = OathType.HOTP,
            counter = 1L
        )
        
        every {
            mockStorage.storeOathCredential(any<OathCredential>())
        } just runs
        
        // Add credential to cache
        oathServiceWithCache.addCredential(hotpCredential)
        
        // Mock retrieveOathCredential to return our test credential
        every {
            mockStorage.retrieveOathCredential(hotpCredential.id)
        } returns hotpCredential
        
        // When
        val codeInfo = oathServiceWithCache.generateCodeForCredential(hotpCredential.id)
        
        // Then
        assertNotNull(codeInfo)
        assertNotNull(codeInfo.code)
        assertEquals(2L, codeInfo.counter) // Counter should be incremented
        verify { 
            mockStorage.storeOathCredential(match { 
                it.id == hotpCredential.id && it.counter == 2L 
            })
        }
    }
    
    @Test(expected = MfaException::class)
    fun `test generate code for missing credential throws exception`() {
        // Given
        val nonExistentId = "non-existent-id"
        
        every {
            mockStorage.retrieveOathCredential(nonExistentId)
        } returns null
        
        // When - should throw exception
        oathServiceWithCache.generateCodeForCredential(nonExistentId)
    }
    
    @Test
    fun `test credential is not cached when caching disabled`() {
        // Given
        every {
            mockStorage.storeOathCredential(any<OathCredential>())
        } just runs
        
        // Add credential
        oathServiceWithoutCache.addCredential(testCredential)
        
        // Clear mock to verify next call
        clearMocks(mockStorage)
        
        // Setup credential retrieval from storage
        every {
            mockStorage.retrieveOathCredential(testCredentialId)
        } returns testCredential
        
        // This should trigger a storage access since caching is disabled
        oathServiceWithoutCache.getCredential(testCredentialId)
        
        // Then
        // Verify storage was accessed (would not happen if credential was cached)
        verify(exactly = 1) {
            mockStorage.retrieveOathCredential(testCredentialId)
        }
    }
}
