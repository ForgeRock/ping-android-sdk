/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.exception.MfaStorageException
import com.pingidentity.mfa.oath.storage.SharedPrefsOathStorage
import com.pingidentity.utils.TestModeDetector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * Android instrumented test for SharedPrefsOathStorage integration with OathClient.
 * This test validates that the custom SharedPreferences-based storage works
 * seamlessly with OathClient as if it were using the default storage.
 * 
 * This test class consolidates tests for:
 * 1. Full integration with OathClient including credential management and OTP generation
 * 2. Multiple credential types (TOTP/HOTP) handling
 * 3. URI-based credential import
 * 4. Persistence across client instances
 * 5. Error handling and storage failure recovery
 * 6. Thread safety for concurrent operations
 * 7. Time-based code generation with timing verification
 */
@RunWith(AndroidJUnit4::class)
class SharedPrefsOathStorageAndroidTest {

    private lateinit var storage: SharedPrefsOathStorage
    private lateinit var client: MfaOathClient
    private lateinit var context: Context
    private val testPrefsName = "test_oath_integration_prefs"
    
    // Test data
    private val testSecret = "JBSWY3DPEHPK3PXP"
    private val testIssuer = "Test Issuer"
    private val testAccountName = "testuser@example.com"
    
    companion object {
        // Initialize test mode early to ensure it's enabled before any SDK initialization
        init {
            println("Enabling test mode for SharedPrefsOathStorageAndroidTest")
            TestModeDetector.enableTestMode()
        }
    }
    
    @Before
    fun setup() {
        // Get the context from instrumentation
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Ensure test mode is enabled
        TestModeDetector.enableTestMode()
        
        // Initialize ContextProvider with the app context
        ContextProvider.init(context)
        
        // Clear any existing preferences
        context.getSharedPreferences(testPrefsName, Context.MODE_PRIVATE).edit().clear().apply()
        
        // Initialize storage
        storage = SharedPrefsOathStorage(context, testPrefsName)
        
        // Create client with our custom storage using DSL style
        client = OathClient {
            this.storage = this@SharedPrefsOathStorageAndroidTest.storage
            enableCredentialCache = false
            encryptionEnabled = false // Disable encryption for tests
            logger = Logger.logger
        }
    }
    
    @After
    fun tearDown() {
        // Clean up any test data
        try {
            val credentials = client.getCredentials()
            credentials.forEach { credential ->
                client.deleteCredential(credential.id)
            }
        } catch (e: Exception) {
            println("Error during cleanup: ${e.message}")
        }
        
        // Close storage
        storage.close()
        
        // Clear test preferences
        context.getSharedPreferences(testPrefsName, Context.MODE_PRIVATE).edit().clear().apply()
    }
    
    @Test
    fun testFullIntegrationWorkflow() {
        // 1. Add a TOTP credential
        val credential = client.saveCredential(
            OathCredential(
                issuer = testIssuer,
                displayIssuer = testIssuer,
                accountName = testAccountName,
                displayAccountName = testAccountName,
                oathType = OathType.TOTP,
                oathAlgorithm = OathAlgorithm.SHA1,
                period = 30,
                digits = 6,
                secret = testSecret
            )
        )
        
        // Verify the credential was added
        assertNotNull("Credential should not be null", credential)
        assertEquals("Issuer should match", testIssuer, credential.issuer)
        assertEquals("Account name should match", testAccountName, credential.accountName)
        
        // 2. Check that the credential exists in SharedPreferences
        val prefs = context.getSharedPreferences(testPrefsName, Context.MODE_PRIVATE)
        val metadataSet = prefs.getStringSet("oath_metadata", emptySet())
        assertNotNull("Metadata set should not be null", metadataSet)
        assertFalse("Metadata set should not be empty", metadataSet!!.isEmpty())
        assertTrue("Metadata should contain credential ID", metadataSet.contains(credential.id))
        
        // 3. Generate an OTP code
        val codeInfo = client.generateCodeWithValidity(credential.id)
        
        // Verify the code was generated
        assertNotNull("Code info should not be null", codeInfo)
        assertEquals("Code should be 6 digits", 6, codeInfo.code.length)
        assertTrue("Code should be numeric", codeInfo.code.all { it.isDigit() })
        assertTrue("Time remaining should be positive", codeInfo.timeRemaining > 0)
        assertTrue("Progress should be between 0 and 1", codeInfo.progress in 0.0..1.0)
        
        // 4. Verify the generated code
        val code = client.generateCode(credential.id)
        assertEquals("Generated code should match code info", codeInfo.code, code)
        
        // 5. Update the credential
        client.saveCredential(
            credential.copy(
                displayIssuer = "Updated Issuer",
                displayAccountName = "updated@example.com"
            )
        )
        
        // Verify the update
        val retrievedCredential = client.getCredential(credential.id)
        assertNotNull("Retrieved credential should not be null", retrievedCredential)
        assertEquals("Updated display issuer should match", "Updated Issuer", retrievedCredential!!.displayIssuer)
        assertEquals("Updated display account name should match", "updated@example.com", retrievedCredential.displayAccountName)
        
        // 6. Remove the credential
        val removed = client.deleteCredential(credential.id)
        assertTrue("Credential should be removed", removed)
        
        // Verify it's gone from both client and storage
        assertNull("Credential should not be retrievable from client", client.getCredential(credential.id))
        assertNull("Credential should not be retrievable from storage", storage.retrieveOathCredential(credential.id))
        
        // Also verify it's gone from SharedPreferences
        val updatedMetadataSet = prefs.getStringSet("oath_metadata", emptySet())
        assertFalse("Metadata should not contain credential ID anymore", updatedMetadataSet!!.contains(credential.id))
    }
    
    @Test
    fun testMultipleCredentials() {
        // Add two different types of credentials
        val totpCredential = client.saveCredential(
            OathCredential(
                issuer = "TOTP Issuer",
                displayIssuer = "TOTP Issuer",
                accountName = "totp@example.com",
                displayAccountName = "totp@example.com",
                oathType = OathType.TOTP,
                oathAlgorithm = OathAlgorithm.SHA1,
                secret = testSecret
            )
        )
        
        val hotpCredential = client.saveCredential(
            OathCredential(
                issuer = "HOTP Issuer",
                displayIssuer = "HOTP Issuer",
                accountName = "hotp@example.com",
                displayAccountName = "hotp@example.com",
                oathType = OathType.HOTP,
                oathAlgorithm = OathAlgorithm.SHA256,
                digits = 8,
                counter = 5,
                secret = testSecret
            )
        )
        
        // Verify both credentials were added
        val credentials = client.getCredentials()
        assertEquals("Should have 2 credentials", 2, credentials.size)
        
        // Generate codes for both credentials
        val totpCodeInfo = client.generateCodeWithValidity(totpCredential.id)
        val hotpCodeInfo = client.generateCodeWithValidity(hotpCredential.id)
        
        // Verify TOTP properties
        assertEquals("TOTP code should be 6 digits", 6, totpCodeInfo.code.length)
        assertTrue("TOTP time remaining should be positive", totpCodeInfo.timeRemaining > 0)
        
        // Verify HOTP properties
        assertEquals("HOTP code should be 8 digits", 8, hotpCodeInfo.code.length)
        assertEquals("HOTP counter should be incremented to 6", 6L, hotpCodeInfo.counter)
        
        // Generate a second HOTP code (counter should increment)
        val hotpCodeInfo2 = client.generateCodeWithValidity(hotpCredential.id)
        assertEquals("HOTP counter should be incremented to 7", 7L, hotpCodeInfo2.counter)
        
        // Clear all credentials
        credentials.forEach { credential ->
            client.deleteCredential(credential.id)
        }
        
        // Verify all are gone
        assertTrue("No credentials should remain", client.getCredentials().isEmpty())
    }
    
    @Test
    fun testUriImport() {
        // Create a TOTP URI
        val uri = "otpauth://totp/Test%20Issuer:testuser@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Test%20Issuer&algorithm=SHA1&digits=6&period=30"
        
        // Import the credential
        val credential = client.addCredentialFromUri(uri)
        
        // Verify credential was added
        assertNotNull("Credential should not be null", credential)
        assertEquals("Issuer should match", testIssuer, credential.issuer)
        assertEquals("Account name should match", testAccountName, credential.accountName)
        
        // Generate and verify a code
        val codeInfo = client.generateCodeWithValidity(credential.id)
        assertNotNull("Code info should not be null", codeInfo)
        
        // Verify the code
        val code = client.generateCode(credential.id)
        assertEquals("Generated code should match code info", codeInfo.code, code)
    }
    
    @Test
    fun testPersistenceAcrossClientInstances() {
        // 1. Add a credential with the first client instance
        val credential = client.saveCredential(
            OathCredential(
                issuer = testIssuer,
                displayIssuer = testIssuer,
                accountName = testAccountName,
                displayAccountName = testAccountName,
                oathType = OathType.TOTP,
                secret = testSecret
            )
        )
        
        // Verify it was added
        assertNotNull("Credential should be added", client.getCredential(credential.id))
        
        // 2. Close the first client and storage
        storage.close()
        
        // 3. Create a new storage instance with the same preferences name
        val newStorage = SharedPrefsOathStorage(context, testPrefsName)
        newStorage.initialize()
        
        // 4. Create a new client with the new storage using DSL style
        val newClient = OathClient {
            this.storage = newStorage
            encryptionEnabled = false
        }
        newClient.initialize()
        
        try {
            // 5. Verify the credential is accessible in the new client instance
            val retrievedCredential = newClient.getCredential(credential.id)
            assertNotNull("Credential should be retrievable from new client", retrievedCredential)
            assertEquals("Credential issuer should match", testIssuer, retrievedCredential!!.issuer)
            
            // 6. Generate a code with the new client
            val codeInfo = newClient.generateCodeWithValidity(credential.id)
            assertNotNull("Should be able to generate code with new client", codeInfo)
            
            // 7. Clean up
            newClient.deleteCredential(credential.id)
        } finally {
            // Ensure resources are closed
            newStorage.close()
        }
    }
    
    @Test
    fun testStorageFailureHandling() {
        // 1. Create a credential
        val credential = client.saveCredential(
            OathCredential(
                issuer = testIssuer,
                displayIssuer = testIssuer,
                accountName = testAccountName,
                displayAccountName = testAccountName,
                oathType = OathType.TOTP,
                secret = testSecret
            )
        )
        
        // Store the ID for later use
        val credentialId = credential.id
        
        // 2. Corrupt the storage by directly manipulating SharedPreferences
        val prefs = context.getSharedPreferences(testPrefsName, Context.MODE_PRIVATE)
        prefs.edit().putString("oath_credential_${credentialId}", "{corrupted data}").apply()
        
        // 3. Try to retrieve the credential from storage directly - this should throw a proper exception
        try {
            storage.retrieveOathCredential(credentialId)
            fail("Should have thrown exception for corrupted credential data")
        } catch (e: Exception) {
            // The storage implementation should throw MfaStorageException for corrupted data
            assertTrue("Exception should be MfaStorageException or its subclass", 
                e is MfaStorageException || e.cause is MfaStorageException)
            println("Got expected exception when retrieving from storage: ${e.javaClass.simpleName}: ${e.message}")
        }
        
        // 4. Try to retrieve via client - the client should catch the storage exception and throw a MfaException
        try {
            client.getCredential(credentialId)
            fail("Should have thrown exception for corrupted credential")
        } catch (e: Exception) {
            // The client might wrap the storage exception in its own exception type
            println("Got exception when retrieving from client: ${e.javaClass.simpleName}: ${e.message}")
            assertTrue("Exception should be related to storage or data corruption", 
                e.message?.contains("Failed to get credential") == true)
        }
        
        // 5. Try to generate a code - should fail for similar reasons
        try {
            client.generateCodeWithValidity(credentialId)
            fail("Should have thrown exception for corrupted credential")
        } catch (e: Exception) {
            // Expected exception
            println("Got exception when generating code: ${e.javaClass.simpleName}: ${e.message}")
            assertTrue("Exception should be related to credential failure", 
                e.message?.contains("credential") == true || 
                e.message?.contains("not found") == true || 
                e.message?.contains("Failed") == true)
        }
    }
    
    @Test
    fun testThreadSafety() {
        // Create a list to store credential IDs
        val credentialIds = Collections.synchronizedList(mutableListOf<String>())
        
        // Create multiple threads that add credentials simultaneously
        val threads = List(5) { threadIndex ->
            Thread {
                try {
                    val credential = OathCredential(
                        issuer = "Thread Issuer $threadIndex",
                        displayIssuer = "Thread Issuer $threadIndex",
                        accountName = "thread$threadIndex@example.com",
                        displayAccountName = "thread$threadIndex@example.com",
                        oathType = OathType.TOTP,
                        secret = testSecret
                    )
                    
                    val addedCredential = client.saveCredential(credential)
                    credentialIds.add(addedCredential.id)
                    
                    // Add a small delay to ensure the storage operation completes
                    Thread.sleep(50)
                    
                } catch (e: Exception) {
                    println("Error adding credential in thread $threadIndex: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        
        // Start all threads
        threads.forEach { it.start() }
        
        // Wait for all threads to complete
        threads.forEach { it.join() }
        
        // Add a short delay to ensure all storage operations are fully complete
        Thread.sleep(200)
        
        // Verify all credentials were added to the list
        assertEquals("Should have added 5 credentials", 5, credentialIds.size)
        
        // Verify all can be retrieved
        credentialIds.forEach { id ->
            val credential = client.getCredential(id)
            assertNotNull("Should be able to retrieve credential $id", credential)
        }
        
        // Get all credentials from the client
        val allCredentials = client.getCredentials()
        
        // Verify the total count
        assertEquals("Should have 5 credentials total", 5, allCredentials.size)
    }
    
    @Test
    fun testTotpCodeGeneration() {
        // Add TOTP credential with 30-second period
        val credential = client.saveCredential(
            OathCredential(
                issuer = testIssuer,
                displayIssuer = testIssuer,
                accountName = testAccountName,
                displayAccountName = testAccountName,
                oathType = OathType.TOTP,
                oathAlgorithm = OathAlgorithm.SHA1,
                period = 30,
                secret = testSecret
            )
        )
        
        // Generate first code
        val codeInfo1 = client.generateCodeWithValidity(credential.id)
        
        // Wait 2 seconds
        Thread.sleep(2000)
        
        // Generate second code - should be the same code but with less time remaining
        val codeInfo2 = client.generateCodeWithValidity(credential.id)
        
        // Verify
        assertEquals("Codes should be the same", codeInfo1.code, codeInfo2.code)
        assertTrue("Time remaining should decrease", codeInfo2.timeRemaining < codeInfo1.timeRemaining)
        assertTrue("Progress should increase", codeInfo2.progress > codeInfo1.progress)
    }
}
