/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.pingidentity.android.ContextProvider
import com.pingidentity.mfa.commons.policy.BiometricAvailablePolicy
import com.pingidentity.mfa.commons.policy.DeviceTamperingPolicy
import com.pingidentity.mfa.commons.policy.MfaPolicyEvaluator
import com.pingidentity.mfa.oath.storage.SQLOathStorage
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
 * Integration test for OATH Policy functionality to verify the
 * policy framework works correctly with OATH credentials.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class OathPolicyIntegrationTest {

    private lateinit var appContext: Context
    private val testDbName = "test_oath_policy_${System.currentTimeMillis()}.db"
    private lateinit var testClient: OathClient

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
     * Creates a test client with policy evaluator
     */
    private suspend fun createTestClientWithPolicies(): OathClient {
        val testStorage = SQLOathStorage {
            context = appContext
            passphraseProvider = NonePassphraseProvider()
            databaseName = testDbName
        }

        val policyEvaluator = MfaPolicyEvaluator {
            policies = listOf(
                BiometricAvailablePolicy(),
                DeviceTamperingPolicy()
            )
        }

        testClient = OathClient {
            storage = testStorage
            this.policyEvaluator = policyEvaluator
        }

        return testClient
    }

    /**
     * Creates a test OATH credential with optional policies
     */
    private fun createTestOathCredential(
        policies: String? = null,
        isLocked: Boolean = false,
        lockingPolicy: String? = null
    ): OathCredential {
        return OathCredential(
            id = UUID.randomUUID().toString(),
            issuer = "Example Corp",
            accountName = "test@example.com",
            secret = "JBSWY3DPEHPK3PXP",
            oathType = OathType.TOTP,
            oathAlgorithm = OathAlgorithm.SHA1,
            digits = 6,
            period = 30,
            policies = policies,
            isLocked = isLocked,
            lockingPolicy = lockingPolicy
        )
    }

    @Test
    fun testCredentialWithoutPoliciesIsNotLocked() = runTest {
        val client = createTestClientWithPolicies()

        // Create a test credential without policies
        val credential = createTestOathCredential()

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
        val credential = createTestOathCredential(
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
    fun testOtpGenerationWithPolicies() = runTest {
        val client = createTestClientWithPolicies()

        // Create a test credential with policies
        val credential = createTestOathCredential(
            policies = """{"deviceTampering":{"score":0.8}}"""
        )

        // Store the credential
        val savedCredential = client.saveCredential(credential).getOrThrow()

        // Generate OTP (should work with policies present)
        val otpResult = client.generateCode(savedCredential.id).getOrThrow()
        assertNotNull("OTP should be generated", otpResult)
        assertTrue("OTP should be non-empty", otpResult.isNotBlank())

        client.close()
    }

    @Test
    fun testCredentialLockingBasic() = runTest {
        val client = createTestClientWithPolicies()

        // Create a test credential that is locked
        val credential = createTestOathCredential(
            isLocked = true,
            lockingPolicy = "TestPolicy"
        )

        // Store the credential
        val savedCredential = client.saveCredential(credential).getOrThrow()

        // Verify credential is locked
        assertTrue("Credential should be locked", savedCredential.isLocked)
        assertEquals("Locking policy should be set", "TestPolicy", savedCredential.lockingPolicy)

        client.close()
    }

    @Test
    fun testCredentialUnlockingBasic() = runTest {
        val client = createTestClientWithPolicies()

        // Create a test credential that is unlocked
        val credential = createTestOathCredential(
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
    fun testCredentialPersistenceWithLockState() = runTest {
        val client = createTestClientWithPolicies()

        // Create and save a locked credential
        val originalCredential = createTestOathCredential(
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
        val credential = createTestOathCredential(
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
        val credential1 = createTestOathCredential(
            policies = """{"biometricAvailable":{}}"""
        )
        val credential2 = createTestOathCredential()

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
        val credential = createTestOathCredential(
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
}
