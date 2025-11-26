/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.logger.Logger
import com.pingidentity.mfa.push.storage.PushStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Unit tests for PushDeviceTokenManager class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PushDeviceTokenManagerTest {

    companion object {
        private const val TEST_DEVICE_TOKEN = "test-device-token"
        private const val TEST_NEW_DEVICE_TOKEN = "test-new-device-token"
    }

    private lateinit var pushDeviceTokenManager: PushDeviceTokenManager
    private lateinit var mockStorage: PushStorage
    private lateinit var mockLogger: Logger
    private lateinit var testDeviceToken: PushDeviceToken

    @Before
    fun setUp() {
        // Set up mocks
        mockStorage = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)

        // Create a test device token
        testDeviceToken = PushDeviceToken(
            tokenId = TEST_DEVICE_TOKEN,
            createdAt = Date()
        )

        // Initialize the manager with mocks
        pushDeviceTokenManager = PushDeviceTokenManager(mockStorage, mockLogger)
    }

    @Test
    fun `test getCurrentDeviceToken returns token when available`() = runTest {
        // Given
        coEvery { mockStorage.getCurrentPushDeviceToken() } returns testDeviceToken

        // When
        val result = pushDeviceTokenManager.getCurrentDeviceToken()

        // Then
        Assert.assertNotNull(result)
        Assert.assertEquals(testDeviceToken, result)
        coVerify { mockStorage.getCurrentPushDeviceToken() }
    }

    @Test
    fun `test getCurrentDeviceToken returns null when not available`() = runTest {
        // Given
        coEvery { mockStorage.getCurrentPushDeviceToken() } returns null

        // When
        val result = pushDeviceTokenManager.getCurrentDeviceToken()

        // Then
        Assert.assertNull(result)
        coVerify { mockStorage.getCurrentPushDeviceToken() }
    }

    @Test
    fun `test getCurrentDeviceToken handles exception`() = runTest {
        // Given
        coEvery { mockStorage.getCurrentPushDeviceToken() } throws RuntimeException("Test exception")

        // When
        val result = pushDeviceTokenManager.getCurrentDeviceToken()

        // Then
        Assert.assertNull(result)
        coVerify { mockStorage.getCurrentPushDeviceToken() }
    }

    @Test
    fun `test getDeviceTokenId returns cached token id`() = runTest {
        // Given
        // This test is simplified to just check that getDeviceTokenId returns the token ID
        // from the token returned by storage
        coEvery { mockStorage.getCurrentPushDeviceToken() } returns testDeviceToken
        
        // When 
        val result = pushDeviceTokenManager.getDeviceTokenId()

        // Then
        Assert.assertEquals(TEST_DEVICE_TOKEN, result)
        coVerify { mockStorage.getCurrentPushDeviceToken() }
    }

    @Test
    fun `test getDeviceTokenId retrieves from storage when cache is empty`() = runTest {
        // Given
        coEvery { mockStorage.getCurrentPushDeviceToken() } returns testDeviceToken

        // When
        val result = pushDeviceTokenManager.getDeviceTokenId()

        // Then
        Assert.assertEquals(TEST_DEVICE_TOKEN, result)
        coVerify { mockStorage.getCurrentPushDeviceToken() }
    }

    @Test
    fun `test getDeviceTokenId returns null when token not found`() = runTest {
        // Given
        coEvery { mockStorage.getCurrentPushDeviceToken() } returns null

        // When
        val result = pushDeviceTokenManager.getDeviceTokenId()

        // Then
        Assert.assertNull(result)
        coVerify { mockStorage.getCurrentPushDeviceToken() }
    }

    @Test
    fun `test shouldUpdateToken returns true for new token`() = runTest {
        // Given
        // Mock getCurrentPushDeviceToken to return our test token
        coEvery { mockStorage.getCurrentPushDeviceToken() } returns testDeviceToken
        
        // Call getDeviceTokenId to cache the token internally
        pushDeviceTokenManager.getDeviceTokenId()

        // When
        val result = pushDeviceTokenManager.shouldUpdateToken(TEST_NEW_DEVICE_TOKEN)

        // Then
        Assert.assertTrue(result)
    }

    @Test
    fun `test shouldUpdateToken returns false for same token`() = runTest {
        // Given
        // Mock getCurrentPushDeviceToken to return our test token
        coEvery { mockStorage.getCurrentPushDeviceToken() } returns testDeviceToken
        
        // Call getDeviceTokenId to cache the token internally
        pushDeviceTokenManager.getDeviceTokenId()

        // When
        val result = pushDeviceTokenManager.shouldUpdateToken(TEST_DEVICE_TOKEN)

        // Then
        Assert.assertFalse(result)
    }

    @Test
    fun `test shouldUpdateToken retrieves from storage when cache is empty`() = runTest {
        // Given
        coEvery { mockStorage.getCurrentPushDeviceToken() } returns testDeviceToken

        // When
        val result = pushDeviceTokenManager.shouldUpdateToken(TEST_NEW_DEVICE_TOKEN)

        // Then
        Assert.assertTrue(result)
        coVerify { mockStorage.getCurrentPushDeviceToken() }
    }

    @Test
    fun `test shouldUpdateToken returns false for empty token`() = runTest {
        // When
        val result = pushDeviceTokenManager.shouldUpdateToken("")

        // Then
        Assert.assertFalse(result)
        coVerify(exactly = 0) { mockStorage.getCurrentPushDeviceToken() }
    }

    @Test
    fun `test updateDeviceToken stores new token successfully`() = runTest {
        // Given
        // Mock getCurrentPushDeviceToken to return our test token
        coEvery { mockStorage.getCurrentPushDeviceToken() } returns testDeviceToken
        
        // Call getDeviceTokenId to cache the token internally
        pushDeviceTokenManager.getDeviceTokenId()
        
        // Mock the storage call
        coEvery { mockStorage.storePushDeviceToken(any()) } returns Unit

        // When
        val result = pushDeviceTokenManager.updateDeviceToken(TEST_NEW_DEVICE_TOKEN)

        // Then
        Assert.assertTrue(result)
        coVerify {
            mockStorage.getCurrentPushDeviceToken()
            mockStorage.storePushDeviceToken(any())
        }
    }

    @Test
    fun `test updateDeviceToken skips update for same token`() = runTest {
        // Given
        // Mock getCurrentPushDeviceToken to return our test token
        coEvery { mockStorage.getCurrentPushDeviceToken() } returns testDeviceToken
        
        // Call getDeviceTokenId to cache the token internally
        pushDeviceTokenManager.getDeviceTokenId()

        // When
        val result = pushDeviceTokenManager.updateDeviceToken(TEST_DEVICE_TOKEN)

        // Then
        Assert.assertTrue(result)
        // Verify that storage is not called to store the token since it hasn't changed
        coVerify(exactly = 0) { mockStorage.storePushDeviceToken(any()) }
    }

    @Test
    fun `test updateDeviceToken fails for empty token`() = runTest {
        // When
        val result = pushDeviceTokenManager.updateDeviceToken("")

        // Then
        Assert.assertFalse(result)
        // Verify that storage methods are not called with empty token
        coVerify(exactly = 0) {
            mockStorage.getCurrentPushDeviceToken()
            mockStorage.storePushDeviceToken(any())
        }
    }

    @Test
    fun `test updateDeviceToken handles storage exceptions`() = runTest {
        // Given
        // Return a different token ID to force an update
        val differentToken = PushDeviceToken(tokenId = "different-token", createdAt = Date())
        coEvery { mockStorage.getCurrentPushDeviceToken() } returns differentToken
        
        // Mock the storage to throw an exception when storing
        coEvery { mockStorage.storePushDeviceToken(any()) } throws RuntimeException("Test exception")

        // When
        val result = pushDeviceTokenManager.updateDeviceToken(TEST_NEW_DEVICE_TOKEN)

        // Then
        Assert.assertFalse(result)
        coVerify { 
            mockStorage.getCurrentPushDeviceToken()
            mockStorage.storePushDeviceToken(any())
        }
    }

    @Test
    fun `test updateDeviceToken updates in-memory token after successful storage update`() = runTest {
        // Given
        // Return a different token ID initially
        val differentToken = PushDeviceToken(tokenId = "different-token", createdAt = Date())
        coEvery { mockStorage.getCurrentPushDeviceToken() } returns differentToken

        // Mock successful storage
        coEvery { mockStorage.storePushDeviceToken(any()) } returns Unit

        // When - First update the token
        val updateResult = pushDeviceTokenManager.updateDeviceToken(TEST_NEW_DEVICE_TOKEN)

        // Then - Verify the token was updated successfully
        Assert.assertTrue(updateResult)

        // When - Call getDeviceTokenId() to retrieve the updated token
        val tokenId = pushDeviceTokenManager.getDeviceTokenId()

        // Then - Should return the new token without accessing storage again
        Assert.assertEquals(TEST_NEW_DEVICE_TOKEN, tokenId)
        // getCurrentPushDeviceToken should be called once during the update operation
        coVerify(exactly = 1) { mockStorage.getCurrentPushDeviceToken() }
    }
}
