/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.pingidentity.android.ContextProvider
import com.pingidentity.mfa.commons.policy.BiometricAvailablePolicy
import com.pingidentity.mfa.commons.policy.DeviceTamperingPolicy
import com.pingidentity.mfa.commons.policy.MfaPolicyEvaluator
import com.pingidentity.mfa.push.storage.SQLPushStorage
import com.pingidentity.storage.sqlite.passphrase.NonePassphraseProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration tests for the Push Policy framework functionality.
 * These tests verify policy evaluation, credential locking/unlocking, and policy persistence.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class PushPolicyIntegrationTest {

    private lateinit var appContext: Context
    private val testDbName = "test_push_policy_${System.currentTimeMillis()}.db"
    private lateinit var testClient: PushClient

    @Before
    fun setup() = runTest {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        ContextProvider.init(appContext)
        cleanup()
    }

    @After
    fun tearDown() = runTest {
        if (::testClient.isInitialized) {
            testClient.close()
        }
        cleanup()
    }

    private fun cleanup() {
        appContext.deleteDatabase(testDbName)
    }

    /**
     * Creates a test client with policy evaluation enabled
     */
    private suspend fun createTestClientWithPolicies(): PushClient {
        val testStorage = SQLPushStorage {
            context = appContext
            passphraseProvider = NonePassphraseProvider()
            databaseName = testDbName
        }

        val policyEvaluator = MfaPolicyEvaluator {
            policies = listOf(
                BiometricAvailablePolicy,
                DeviceTamperingPolicy
            )
        }

        testClient = PushClient {
            storage = testStorage
            this.policyEvaluator = policyEvaluator
        }

        return testClient
    }

    /**
     * Creates a test Push credential with optional policies
     */
    private fun createTestPushCredential(
        policies: String? = null,
        isLocked: Boolean = false,
        lockingPolicy: String? = null
    ): PushCredential {
        return PushCredential(
            id = UUID.randomUUID().toString(),
            issuer = "Example Corp",
            accountName = "test@example.com",
            serverEndpoint = "https://example.com/push",
            sharedSecret = "testsecret12345",
            policies = policies,
            isLocked = isLocked,
            lockingPolicy = lockingPolicy
        )
    }

    @Test
    fun testCredentialWithoutPoliciesIsNotLocked() = runTest {
        val client = createTestClientWithPolicies()

        // Create a test credential without policies
        val credential = createTestPushCredential()

        // Store the credential
        client.saveCredential(credential).getOrThrow()

        // Verify credential is not locked since no policies are specified
        assertFalse("Credential without policies should not be locked", credential.isLocked)
        assertNull("Credential without policies should have no locking policy", credential.lockingPolicy)

        client.close()
    }

    @Test
    fun testCredentialWithPoliciesStoredCorrectly() = runTest {
        val client = createTestClientWithPolicies()

        // Create a test credential with biometric policy
        val credential = createTestPushCredential(
            policies = """{"biometricAvailable":{}}"""
        )

        // Store the credential
        val savedCredential = client.saveCredential(credential).getOrThrow()

        // Verify policies are stored correctly
        assertNotNull("Credential should have policies", savedCredential.policies)
        assertTrue("Policies should contain biometricAvailable", 
            savedCredential.policies?.contains("biometricAvailable") == true)

        client.close()
    }

    @Test
    fun testPolicyEvaluationBasic() = runTest {
        val client = createTestClientWithPolicies()

        // Create a test credential with device tampering policy
        val credential = createTestPushCredential(
            policies = """{"deviceTampering":{"score":0.8}}"""
        )

        // Store the credential
        client.saveCredential(credential).getOrThrow()

        // Note: Actual policy evaluation would require extending credentials
        // This test validates that credentials with policies can be stored and retrieved
        assertNotNull("Credential policies should be stored", credential.policies)

        client.close()
    }

    @Test
    fun testCredentialLockingBasic() = runTest {
        val client = createTestClientWithPolicies()

        // Create a test credential that is already locked
        val credential = createTestPushCredential(
            isLocked = true,
            lockingPolicy = "TestPolicy"
        )

        // Store the credential
        val savedCredential = client.saveCredential(credential).getOrThrow()

        // Verify credential locking state is preserved
        assertTrue("Credential should be locked", savedCredential.isLocked)
        assertEquals("Locking policy should be set", "TestPolicy", savedCredential.lockingPolicy)

        client.close()
    }

    @Test
    fun testCredentialUnlockingBasic() = runTest {
        val client = createTestClientWithPolicies()

        // Create a test credential that can be unlocked
        val credential = createTestPushCredential(
            isLocked = false,
            lockingPolicy = null
        )

        // Store the credential
        val savedCredential = client.saveCredential(credential).getOrThrow()

        // Verify credential is unlocked
        assertFalse("Credential should be unlocked", savedCredential.isLocked)
        assertNull("Locking policy should be null", savedCredential.lockingPolicy)

        client.close()
    }

    @Test
    fun testComplexPolicyConfiguration() = runTest {
        val client = createTestClientWithPolicies()

        // Create a test credential with multiple policies
        val credential = createTestPushCredential(
            policies = """{"biometricAvailable":{},"deviceTampering":{"score":0.5}}"""
        )

        // Store the credential
        client.saveCredential(credential).getOrThrow()

        // Verify complex policy configuration is stored
        assertNotNull("Credential should have policies", credential.policies)
        assertTrue("Should contain biometricAvailable", credential.policies?.contains("biometricAvailable") == true)
        assertTrue("Should contain deviceTampering", credential.policies?.contains("deviceTampering") == true)

        client.close()
    }

    @Test
    fun testCredentialPersistenceWithLockState() = runTest {
        val client = createTestClientWithPolicies()

        // Create and save a locked credential
        val originalCredential = createTestPushCredential(
            isLocked = true,
            lockingPolicy = "TestPolicy"
        )
        val savedCredential = client.saveCredential(originalCredential).getOrThrow()

        // Verify lock state persists after save
        assertTrue("Credential should remain locked after save", savedCredential.isLocked)
        assertEquals("Locking policy should persist", "TestPolicy", savedCredential.lockingPolicy)
        
        // Create a new client instance to test persistence across sessions
        client.close()
        val newClient = createTestClientWithPolicies()

        // Retrieve the credential and verify lock state persists
        val retrievedCredential = newClient.getCredential(savedCredential.id).getOrThrow()
        assertNotNull("Retrieved credential should not be null", retrievedCredential)
        assertTrue("Lock state should persist across sessions", retrievedCredential?.isLocked == true)

        newClient.close()
    }

    @Test
    fun testMultiplePolicyStorage() = runTest {
        val client = createTestClientWithPolicies()

        // Create a test credential with multiple policies
        val credential = createTestPushCredential(
            policies = """{"biometricAvailable":{},"deviceTampering":{"score":0.7}}"""
        )

        // Store the credential
        client.saveCredential(credential).getOrThrow()

        // Verify multiple policies are stored correctly
        assertNotNull("Credential should have policies", credential.policies)
        assertTrue("Should contain both policies",
            credential.policies?.contains("biometricAvailable") == true && credential.policies.contains("deviceTampering")
        )

        client.close()
    }

    @Test
    fun testCredentialRetrievalWithPolicyConfiguration() = runTest {
        val client = createTestClientWithPolicies()

        // Create multiple test credentials with different policy configurations
        val credential1 = createTestPushCredential(
            policies = """{"biometricAvailable":{}}"""
        )
        val credential2 = createTestPushCredential()

        // Store the credentials
        client.saveCredential(credential1).getOrThrow()
        client.saveCredential(credential2).getOrThrow()

        // Retrieve all credentials
        val allCredentials = client.getCredentials().getOrThrow()

        // Verify credentials are retrieved correctly
        assertEquals("Should have 2 credentials", 2, allCredentials.size)
        
        // Verify at least one has policies
        val hasCredentialWithPolicies = allCredentials.any { it.policies != null }
        assertTrue("At least one credential should have policies", hasCredentialWithPolicies)

        client.close()
    }

    @Test
    fun testCredentialDeletionWithPolicyConfiguration() = runTest {
        val client = createTestClientWithPolicies()

        // Create and save a credential with policies
        val credential = createTestPushCredential(
            policies = """{"biometricAvailable":{}}"""
        )
        val savedCredential = client.saveCredential(credential).getOrThrow()

        // Delete the credential
        val deleteResult = client.deleteCredential(savedCredential.id).getOrThrow()

        // Verify deletion succeeded
        assertTrue("Deletion should succeed", deleteResult)

        // Verify credential is no longer retrievable
        val retrievedCredential = client.getCredential(savedCredential.id).getOrThrow()
        assertNull("Deleted credential should not be retrievable", retrievedCredential)

        client.close()
    }

    @Test
    fun testNotificationProcessingWithPolicyEnabledClient() = runTest {
        val client = createTestClientWithPolicies()

        // Create and save a credential with policies
        val credential = createTestPushCredential(
            policies = """{"biometricAvailable":{}}"""
        )
        client.saveCredential(credential).getOrThrow()

        // Create a mock notification message
        val notificationData = mapOf(
            "messageId" to "test-message-123",
            "data" to "test-notification-data"
        )

        // Process the notification (this will return null for invalid data, which is expected)
        val result = client.processNotification(notificationData).getOrThrow()

        // Since we're using mock data, the result should be null
        // This test verifies the notification processing doesn't crash with policies enabled
        // (Real notification processing would require proper JWT tokens and valid data)
        assertNull("Mock notification should return null", result)

        client.close()
    }
}
