/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.biometric.BiometricPrompt
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.device.binding.authenticator.exception.BiometricAuthenticationException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.security.PrivateKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class AuthenticateWithBiometricUnitTest {

    private lateinit var context: Context
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var privateKey: PrivateKey

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        BiometricOperationState.clear()

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Test Authentication")
            .setSubtitle("Please authenticate")
            .setNegativeButtonText("Cancel")
            .build()

        // Generate a real private key for testing
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        privateKey = keyPairGenerator.generateKeyPair().private
    }

    @AfterTest
    fun tearDown() {
        BiometricOperationState.clear()
    }

    @Test
    fun testAuthenticateWithBiometricLaunchesActivity() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val intentSlot = slot<Intent>()
        every { mockContext.startActivity(capture(intentSlot)) } returns Unit

        // Start authentication in a coroutine
        val authJob = launch {
            try {
                withTimeout(5000) { // 5 second timeout
                    authenticateWithBiometric(mockContext, promptInfo, privateKey)
                }
            } catch (e: CancellationException) {
                // Expected when we cancel
            } catch (e: Exception) {
                // Other exceptions are also acceptable for this test
            }
        }

        // Give the function time to set up state and launch activity
        delay(500)

        // Verify that the state was set up correctly
        assertEquals(promptInfo, BiometricOperationState.promptInfo)
        assertEquals(privateKey, BiometricOperationState.privateKey)
        assertNotNull(BiometricOperationState.continuation.get())

        // Verify intent was started
        verify { mockContext.startActivity(any()) }
        val capturedIntent = intentSlot.captured
        assertEquals(BiometricPromptActivity::class.java.name, capturedIntent.component?.className)
        assertTrue(capturedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        // Cancel the authentication to clean up
        authJob.cancel()

        // Give time for cleanup
        delay(100)

        // Verify cleanup occurred
        assertNull(BiometricOperationState.continuation.get())
        assertNull(BiometricOperationState.promptInfo)
        assertNull(BiometricOperationState.privateKey)
    }

    @Test
    fun testAuthenticateWithBiometricWithNullPrivateKey() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.startActivity(any()) } returns Unit

        val authJob = launch {
            try {
                withTimeout(5000) {
                    authenticateWithBiometric(mockContext, promptInfo, null)
                }
            } catch (e: Exception) {
                // Expected for this test
            }
        }

        delay(500)

        // Verify state setup with null private key
        assertEquals(promptInfo, BiometricOperationState.promptInfo)
        assertNull(BiometricOperationState.privateKey)
        assertNotNull(BiometricOperationState.continuation.get())

        verify { mockContext.startActivity(any()) }

        authJob.cancel()
    }

    @Test
    fun testBiometricOperationStateSetup() {
        // Test that BiometricOperationState can be set up correctly
        BiometricOperationState.promptInfo = promptInfo
        BiometricOperationState.privateKey = privateKey

        assertEquals(promptInfo, BiometricOperationState.promptInfo)
        assertEquals(privateKey, BiometricOperationState.privateKey)

        BiometricOperationState.clear()
        assertNull(BiometricOperationState.promptInfo)
        assertNull(BiometricOperationState.privateKey)
    }

    @Test
    fun testBiometricOperationStateWithNullPromptInfo() {
        // Test state handling with null promptInfo
        BiometricOperationState.promptInfo = null
        BiometricOperationState.privateKey = privateKey

        assertNull(BiometricOperationState.promptInfo)
        assertEquals(privateKey, BiometricOperationState.privateKey)
    }

    @Test
    fun testBiometricOperationStateSignatureHandling() {
        // Test state setup for signature creation scenarios
        BiometricOperationState.promptInfo = promptInfo
        BiometricOperationState.privateKey = privateKey

        // Verify state is set correctly for signature creation
        assertNotNull(BiometricOperationState.promptInfo)
        assertNotNull(BiometricOperationState.privateKey)

        BiometricOperationState.clear()
    }

    @Test
    fun testBiometricOperationStateWithoutPrivateKey() {
        // Test state setup without private key (no CryptoObject)
        BiometricOperationState.promptInfo = promptInfo
        BiometricOperationState.privateKey = null

        assertEquals(promptInfo, BiometricOperationState.promptInfo)
        assertNull(BiometricOperationState.privateKey)

        BiometricOperationState.clear()
    }

    @Test
    fun testAuthenticateWithBiometricSuccessSimulation() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.startActivity(any()) } returns Unit

        var authResult: BiometricPrompt.AuthenticationResult? = null
        var authException: Exception? = null

        val authJob = launch {
            try {
                authResult = authenticateWithBiometric(mockContext, promptInfo, privateKey)
            } catch (e: Exception) {
                authException = e
            }
        }

        // Give time for setup
        delay(500)

        // Simulate successful authentication
        val continuation = BiometricOperationState.continuation.get()
        assertNotNull(continuation)

        val mockResult = createMockAuthenticationResult()

        // Resume with success
        BiometricOperationState.continuation.getAndSet(null)?.resumeWith(Result.success(mockResult))
        BiometricOperationState.clear()

        // Give time for completion
        delay(100)

        // Verify successful result
        assertNotNull(authResult)
        assertNull(authException)

        authJob.cancel()
    }

    @Test
    fun testAuthenticateWithBiometricErrorSimulation() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.startActivity(any()) } returns Unit

        var authResult: BiometricPrompt.AuthenticationResult? = null
        var authException: Exception? = null

        val authJob = launch {
            try {
                authResult = authenticateWithBiometric(mockContext, promptInfo, privateKey)
            } catch (e: Exception) {
                authException = e
            }
        }

        // Give time for setup
        delay(500)

        // Simulate authentication error
        val continuation = BiometricOperationState.continuation.get()
        assertNotNull(continuation)

        val biometricException = BiometricAuthenticationException(
            BiometricPrompt.ERROR_USER_CANCELED,
            "User canceled authentication"
        )

        // Resume with exception
        BiometricOperationState.continuation.getAndSet(null)?.resumeWith(Result.failure(biometricException))
        BiometricOperationState.clear()

        // Give time for completion
        delay(100)

        // Verify error result
        assertNull(authResult)
        assertNotNull(authException)
        assertTrue(authException is BiometricAuthenticationException)
        assertEquals(BiometricPrompt.ERROR_USER_CANCELED, (authException as BiometricAuthenticationException).errorCode)

        authJob.cancel()
    }

    @Test
    fun testAuthenticateWithBiometricCancellation() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.startActivity(any()) } returns Unit

        val authJob = launch {
            try {
                authenticateWithBiometric(mockContext, promptInfo, privateKey)
            } catch (e: CancellationException) {
                // Expected when cancelled
            }
        }

        // Give time for setup
        delay(500)

        // Verify state is set up
        assertNotNull(BiometricOperationState.continuation.get())
        assertNotNull(BiometricOperationState.promptInfo)

        // Cancel the job
        authJob.cancel()

        // Give time for cancellation cleanup
        delay(100)

        // Verify cleanup occurred due to invokeOnCancellation
        assertNull(BiometricOperationState.continuation.get())
        assertNull(BiometricOperationState.promptInfo)
        assertNull(BiometricOperationState.privateKey)
    }

    @Test
    fun testBiometricPromptActivityIntentHandling() {
        val mockContext = mockk<Context>(relaxed = true)
        val intentSlot = slot<Intent>()
        every { mockContext.startActivity(capture(intentSlot)) } returns Unit

        // Test that the intent can be created with proper configuration
        val intent = Intent(mockContext, BiometricPromptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        // Set up valid state first
        BiometricOperationState.promptInfo = promptInfo
        BiometricOperationState.privateKey = privateKey

        // Simulate starting the activity
        mockContext.startActivity(intent)

        // Verify intent was called
        verify { mockContext.startActivity(any()) }

        val capturedIntent = intentSlot.captured
        assertEquals(BiometricPromptActivity::class.java.name, capturedIntent.component?.className)
        assertTrue(capturedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        BiometricOperationState.clear()
    }

    private fun createMockAuthenticationResult(): BiometricPrompt.AuthenticationResult {
        // Create a mock result for testing using MockK
        return mockk<BiometricPrompt.AuthenticationResult>(relaxed = true) {
            every { cryptoObject } returns null
            every { authenticationType } returns BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC
        }
    }
}
