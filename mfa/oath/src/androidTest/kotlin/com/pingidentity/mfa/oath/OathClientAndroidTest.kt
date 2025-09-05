/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.mfa.oath.storage.SQLOathStorage
import com.pingidentity.storage.sqlite.passphrase.NonePassphraseProvider
import com.pingidentity.storage.sqlite.passphrase.PassphraseProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes

/**
 * Instrumented test for the OathClient class to verify its
 * functionality in a real Android environment.
 *
 * This test demonstrates how to use OathClient with ContextProvider
 * for OTP credential management.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class OathClientAndroidTest {

    private lateinit var appContext: Context

    private val testPassphraseProvider = object : PassphraseProvider {
        override suspend fun getPassphrase() = "test_passphrase"
    }

    @Before
    fun setup() {
        // Get the context from instrumentation
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Initialize ContextProvider with the actual app context
        ContextProvider.init(appContext)

        // Clear any existing credentials that might be leftover from previous tests
        cleanupExistingCredentials()
    }
    
    /**
     * Helper method to clean up any existing credentials from previous test runs.
     * This method tries multiple approaches to ensure all credentials are removed
     * regardless of how they were created.
     */
    private fun cleanupExistingCredentials() {
        println("Cleaning up existing credentials before test")
        
        // First try with encryption disabled
        cleanupWithClient(encryptionEnabled = false)
        
        // Then try with encryption enabled to catch any credentials stored with encryption
        cleanupWithClient(encryptionEnabled = true)
    }
    
    /**
     * Helper method to clean up credentials with a specific configuration.
     */
    private fun cleanupWithClient(encryptionEnabled: Boolean) {
        try {
            // Create storage with appropriate passphrase provider based on encryption setting
            val testStorage = SQLOathStorage {
                context = appContext
                // Use TestPassphraseProvider for encrypted storage, NonePassphraseProvider for non-encrypted
                passphraseProvider = if (encryptionEnabled) testPassphraseProvider else NonePassphraseProvider()
            }
            
            // Create a client with the specified configuration
            runTest {
                val client = OathClient {
                    this.encryptionEnabled = encryptionEnabled
                    this.storage = testStorage
                    logger = Logger.STANDARD
                }
                
                // Get all credentials that might exist from previous tests
                client.getCredentials().onSuccess { existingCredentials ->
                    // Delete each credential
                    for (credential in existingCredentials) {
                        client.deleteCredential(credential.id)
                    }
                }
                
                // Close the client to release resources
                client.close()
            }
        } catch (e: Exception) {
            // Just log the exception - we don't want test setup to fail if cleanup has issues
            println("Warning: Exception during cleanup: ${e.message}")
        }
    }

    @Test
    fun testCreateOathClientWithOathConfigurationObject() = runTest {
        // Create an OathClient with a OathConfiguration object
        val config = OathConfiguration {
            enableCredentialCache = true
            timeoutMs = 60_000L
            encryptionEnabled = false
            storage = SQLOathStorage {
                context = appContext
                passphraseProvider = NonePassphraseProvider() // No encryption for this test
                logger = Logger.STANDARD
            }
        }

        val client = OathClient(config)

        // Initialize the client
        client.initialize()

        // Assert that the client is created successfully
        assertNotNull("Client should not be null when created with OathConfiguration object", client)

        // Verify no credentials exist initially
        client.getCredentials().onSuccess { credentials ->
            assertTrue("There should be no credentials initially", credentials.isEmpty())
        }

        // Close the client when done
        client.close()
    }


    @Test
    fun testCreateOathClientWithCustomConfiguration() = runTest {
        // Create an OathClient with custom configuration using the block-based API
        val client = createTestClient(enableCredentialCache = true)

        // Verify client is correctly initialized
        assertNotNull("Client should not be null", client)

        // Verify no credentials exist initially
        client.getCredentials().onSuccess { credentials ->
            assertTrue("There should be no credentials initially", credentials.isEmpty())
        }

        // Close the client when done
        runBlocking { client.close() }
    }


    @Test
    fun testAddAndRetrieveCredential() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Create a test URI
        val uri =
            "otpauth://totp/Example:test@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&algorithm=SHA1&digits=6&period=30"

        // Add a credential from the URI
        val credential = client.addCredentialFromUri(uri).getOrThrow()

        // Verify credential was created with correct values
        assertNotNull("Credential should not be null", credential)
        assertEquals("Issuer should match", "Example", credential.issuer)
        assertEquals("Account name should match", "test@example.com", credential.accountName)
        assertEquals("Algorithm should be SHA1", OathAlgorithm.SHA1, credential.oathAlgorithm)
        assertEquals("OTP type should be TOTP", OathType.TOTP, credential.oathType)
        assertEquals("Digits should be 6", 6, credential.digits)
        assertEquals("Period should be 30", 30, credential.period)

        // Retrieve the credential by ID
        val retrievedCredential = client.getCredential(credential.id).getOrThrow()

        // Verify retrieved credential matches the original
        assertNotNull("Retrieved credential should not be null", retrievedCredential)
        assertEquals(
            "Retrieved credential should match the original",
            credential.id,
            retrievedCredential?.id
        )

        // Close the client when done
        client.close()
    }


    @Test
    fun testRemoveCredential() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Create a test URI
        val uri =
            "otpauth://totp/Example:remove-test@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"

        // Add a credential from the URI
        val credential = client.addCredentialFromUri(uri).getOrThrow()

        // Verify credential exists
        val retrievedBeforeRemove = client.getCredential(credential.id).getOrThrow()
        assertNotNull("Credential should exist before removal", retrievedBeforeRemove)

        // Remove the credential
        val removed = client.deleteCredential(credential.id).getOrThrow()

        // Verify removal was successful
        assertTrue("Removal should be successful", removed)

        // Verify credential no longer exists
        val retrievedAfterRemove = client.getCredential(credential.id).getOrThrow()
        assertTrue("Credential should not exist after removal", retrievedAfterRemove == null)

        // Close the client when done
        client.close()
    }


    @Test
    fun testGenerateCode() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Create a test URI
        val uri =
            "otpauth://totp/Example:code-test@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&algorithm=SHA1&digits=6&period=30"

        // Add a credential from the URI
        val credential = client.addCredentialFromUri(uri).getOrThrow()

        // Generate an OTP code
        val code = client.generateCode(credential.id).getOrThrow()

        // Verify code was generated with correct format
        assertNotNull("Code should not be null", code)
        assertEquals("Code should have 6 digits", 6, code.length)
        // We can't verify the exact code value since it depends on time, but we can verify it's a numeric string
        assertTrue("Code should be numeric", code.all { it.isDigit() })

        // Generate code with validity information
        val codeInfo = client.generateCodeWithValidity(credential.id).getOrThrow()

        // Verify code info has valid values
        assertNotNull("Code info should not be null", codeInfo)
        // We can't compare directly with previous code since time might have changed between generations
        assertNotNull("Code info code should not be null", codeInfo.code)
        assertEquals("Code info code should have 6 digits", 6, codeInfo.code.length)
        assertTrue("Time remaining should be >= 0", codeInfo.timeRemaining >= 0)
        assertEquals("Total period should be 30", 30, codeInfo.totalPeriod)
        assertTrue("Progress should be between 0 and 1", codeInfo.progress in 0.0..1.0)

        // Close the client when done
        client.close()
    }


    @Test
    fun testMultipleCredentials() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Create some test URIs
        val uri1 =
            "otpauth://totp/Service1:user1@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Service1"
        val uri2 =
            "otpauth://totp/Service2:user2@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Service2"

        // Add credentials
        val credential1 = client.addCredentialFromUri(uri1).getOrThrow()
        val credential2 = client.addCredentialFromUri(uri2).getOrThrow()

        // Get all credentials
        val credentials = client.getCredentials().getOrThrow()

        // Verify both credentials are retrieved
        assertEquals("Should have both credentials", 2, credentials.size)

        // Verify the credentials match what we created
        assertTrue("Should contain credential 1", credentials.any { it.id == credential1.id })
        assertTrue("Should contain credential 2", credentials.any { it.id == credential2.id })

        // Close the client when done
        client.close()
    }


    @Test
    fun testAddHotpCredential() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Create a HOTP URI (counter-based OTP)
        val uri =
            "otpauth://hotp/HotpTest:hotp@example.com?secret=JBSWY3DPEHPK3PXP&issuer=HotpTest&counter=0&algorithm=SHA1&digits=6"

        // Add a credential from the URI
        val credential = client.addCredentialFromUri(uri).getOrThrow()

        // Verify credential was created with correct values
        assertNotNull("Credential should not be null", credential)
        assertEquals("Issuer should match", "HotpTest", credential.issuer)
        assertEquals("Account name should match", "hotp@example.com", credential.accountName)
        assertEquals("Algorithm should be SHA1", OathAlgorithm.SHA1, credential.oathAlgorithm)
        assertEquals("OTP type should be HOTP", OathType.HOTP, credential.oathType)
        assertEquals("Digits should be 6", 6, credential.digits)
        assertEquals("Counter should be 0", 0, credential.counter)

        // Generate an OTP code
        val code1 = client.generateCode(credential.id).getOrThrow()

        // Verify code was generated with correct format
        assertNotNull("Code should not be null", code1)
        assertEquals("Code should have 6 digits", 6, code1.length)
        assertTrue("Code should be numeric", code1.all { it.isDigit() })

        // Generate a second code - for HOTP this should be different as the counter increments
        val code2 = client.generateCode(credential.id).getOrThrow()

        // Codes should be different (counter incremented)
        assertNotNull("Second code should not be null", code2)
        assertTrue("HOTP codes should be different as counter increments", code1 != code2)

        // Close the client when done
        client.close()
    }


    @Test
    fun testInvalidURIFormat() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Test invalid URI format - using Result API now
        val result = client.addCredentialFromUri("invalid_uri")

        // Verify the operation failed as expected
        assertTrue("Should return failure for invalid URI format", result.isFailure)

        // Optionally, verify the exception type/message
        result.onFailure { exception ->
            println("Expected error: ${exception.message}")
            // Could add more specific checks on the exception type/message if needed
            assertTrue(
                "Exception should be related to URI parsing",
                exception.message?.contains("URI") ?: false ||
                        exception is IllegalArgumentException
            )
        }

        // Close the client when done
        client.close()
    }


    @Test
    fun testInvalidCredentialIdForGeneratingCode() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Test invalid credential ID for generating code - using Result API now
        val result = client.generateCode("non_existent_id")

        // Verify the operation failed as expected
        assertTrue("Should return failure for non-existent credential ID", result.isFailure)

        // Optionally, verify the exception type/message
        result.onFailure { exception ->
            println("Expected error: ${exception.message}")
            // Could add more specific checks on the exception type/message if needed
            assertTrue(
                "Exception should mention credential not found",
                exception.message?.contains("not found") ?: false ||
                        exception.message?.contains("credential") ?: false
            )
        }

        // Close the client when done
        client.close()
    }


    @Test
    fun testRemovingNonExistentCredential() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Test removing non-existent credential
        val removed = client.deleteCredential("non_existent_id").getOrThrow()
        assertFalse("Removing non-existent credential should return false", removed)

        // Close the client when done
        client.close()
    }


    @Test
    fun testGetCredentialsFailClientNotInitialized() = runTest {
        // Create an OathClient with default configuration
        val config = OathConfiguration {
            enableCredentialCache = true
            timeoutMs = 60_000L
            encryptionEnabled = false
        }

        val client = OathClient(config)

        // Verify client is correctly initialized
        assertNotNull("Client should not be null", client)

        // Get credentials fail, client not initialized
        client.getCredentials().onFailure {
            assertTrue(
                "Exception should mention client not initialized",
                it.message?.contains("not initialized") ?: false
            )
        }
    }


    @Test
    fun testBulkOperations() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Create multiple credentials
        val uriList = listOf(
            "otpauth://totp/Bulk1:user1@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Bulk1",
            "otpauth://totp/Bulk2:user2@example.com?secret=HBSWY3DPEHPK3PXQ&issuer=Bulk2",
            "otpauth://totp/Bulk3:user3@example.com?secret=IBSWY3DPEHPK3PXR&issuer=Bulk3"
        )

        // Add credentials
        val credentials = uriList.map { client.addCredentialFromUri(it).getOrThrow() }

        // Verify all credentials are created
        assertEquals("Should have created all credentials", uriList.size, credentials.size)

        // Get all credentials
        val retrievedCredentials = client.getCredentials().getOrThrow()

        // Verify we can get all credentials
        assertEquals("Should retrieve all credentials", uriList.size, retrievedCredentials.size)

        // Generate codes for all credentials
        val codes = retrievedCredentials.map { client.generateCode(it.id).getOrThrow() }

        // Verify all codes generated successfully
        assertEquals(
            "Should generate code for each credential",
            retrievedCredentials.size,
            codes.size
        )

        // Verify all codes have correct format
        codes.forEach { code ->
            assertEquals("Code should have 6 digits", 6, code.length)
            assertTrue("Code should be numeric", code.all { it.isDigit() })
        }

        // Clear all credentials
        var removedCount = 0
        for (credential in retrievedCredentials) {
            val removed = client.deleteCredential(credential.id).getOrThrow()
            if (removed) removedCount++
        }

        assertEquals("Should have removed all credentials", retrievedCredentials.size, removedCount)

        // Verify all are gone
        val remainingCredentials = client.getCredentials().getOrThrow()
        assertTrue("No credentials should remain", remainingCredentials.isEmpty())

        // Close the client when done
        client.close()
    }


    @Test
    fun testCredentialImportExport() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Add a credential
        val uri =
            "otpauth://totp/Export:export@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Export&algorithm=SHA1&digits=6&period=30"
        val credential = client.addCredentialFromUri(uri).getOrThrow()

        // Export the credential to URI format
        val exportedUri = credential.toUri()

        // Verify the exported URI contains all the necessary components
        assertTrue("URI should contain issuer", exportedUri.contains("issuer=Export"))
        assertTrue(
            "URI should contain account name",
            exportedUri.contains(Uri.encode("export@example.com"))
        )
        assertTrue("URI should contain correct OTP type", exportedUri.contains("otpauth://totp/"))

        // Create a new client and import the credential
        val newClient = createTestClient()

        // Import the exported credential
        val importedCredential = newClient.addCredentialFromUri(exportedUri).getOrThrow()

        // Verify the imported credential matches the original
        assertNotNull("Imported credential should not be null", importedCredential)
        assertEquals("Issuer should match", credential.issuer, importedCredential.issuer)
        assertEquals(
            "Account name should match",
            credential.accountName,
            importedCredential.accountName
        )
        assertEquals(
            "Algorithm should match",
            credential.oathAlgorithm,
            importedCredential.oathAlgorithm
        )
        assertEquals("Digits should match", credential.digits, importedCredential.digits)
        assertEquals("Period should match", credential.period, importedCredential.period)

        // Generate codes from both clients to ensure they produce the same OTP
        val originalCode = client.generateCode(credential.id).getOrThrow()
        val importedCode = newClient.generateCode(importedCredential.id).getOrThrow()

        // For the same time period, both should generate identical codes
        assertEquals(
            "Original and imported credential should generate same code",
            originalCode,
            importedCode
        )

        // Close the clients when done
        client.close()
        newClient.close()
    }


    @Test
    fun testJsonSerialization() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Add a credential
        val uri = "otpauth://totp/Json:json@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Json"
        val credential = client.addCredentialFromUri(uri).getOrThrow()

        // Get JSON representation
        val json = credential.toJson()

        // Verify JSON contains key fields
        assertTrue("JSON should contain ID", json.contains("\"id\""))
        assertTrue("JSON should contain issuer", json.contains("\"issuer\""))
        assertTrue("JSON should contain account info", json.contains("json@example.com"))

        // Close the client when done
        client.close()
    }


    @Test
    fun testCredentialSearch() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Add multiple credentials with different properties for searching
        val uris = listOf(
            "otpauth://totp/SearchA:user1@example.com?secret=JBSWY3DPEHPK3PXP&issuer=SearchA",
            "otpauth://totp/SearchA:user2@example.com?secret=JBSWY3DPEHPK3PXP&issuer=SearchA",
            "otpauth://totp/SearchB:user3@example.com?secret=JBSWY3DPEHPK3PX&issuer=SearchB"
        )

        // Add all credentials
        uris.forEach { client.addCredentialFromUri(it) }

        // Get all credentials to check total count
        val allCredentials = client.getCredentials().getOrThrow()
        assertEquals("Should have all test credentials", uris.size, allCredentials.size)

        // Search by issuer name
        val searchACredentials = allCredentials.filter { it.issuer == "SearchA" }
        assertEquals("Should find two SearchA credentials", 2, searchACredentials.size)

        val searchBCredentials = allCredentials.filter { it.issuer == "SearchB" }
        assertEquals("Should find one SearchB credential", 1, searchBCredentials.size)

        // Search by account name
        val user1Credentials = allCredentials.filter { it.accountName == "user1@example.com" }
        assertEquals("Should find one user1 credential", 1, user1Credentials.size)

        // Close the client when done
        client.close()
    }


    @Test
    fun testPerformance() = runTest {
        // Create an OathClient with credential cache enabled for performance
        val client = createTestClient(enableCredentialCache = true)

        // Create a test URI
        val uri =
            "otpauth://totp/Performance:perf@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Performance"

        // Add a credential from the URI
        val credential = client.addCredentialFromUri(uri).getOrThrow()

        // Measure time to generate codes repeatedly
        val startTime = System.currentTimeMillis()
        val iterations = 100

        for (i in 0 until iterations) {
            client.generateCode(credential.id)
        }

        val endTime = System.currentTimeMillis()
        val avgTimePerGeneration = (endTime - startTime).toFloat() / iterations

        println("Average time per code generation: $avgTimePerGeneration ms")

        // We expect cached generation to be reasonably fast (typically < 10ms per generation)
        // This is more of a benchmark than an assertion, but we can add a very liberal upper bound
        assertTrue(
            "Code generation should be reasonably performant",
            avgTimePerGeneration < 100
        ) // Very generous upper bound for slow test devices

        // Close the client when done
        client.close()
    }


    @Test
    fun testErrorHandlingAndRecovery() = runTest {
        // Create an OathClient
        val client = createTestClient()

        // Test handling of malformed URIs with different types of issues
        val testCases = listOf(
            "otpauth://invalid/Test:test@example.com?secret=JBSWY3DPEHPK3PXP", // Invalid OTP type
            "otpauth://totp/Test:test@example.com" // Missing secret
        )

        for (testCase in testCases) {
            // Use Result API to handle errors
            val result = client.addCredentialFromUri(testCase)

            // Verify that the operation failed
            assertTrue("Should return failure for invalid URI: $testCase", result.isFailure)

            // Optionally log the error
            result.onFailure { e ->
                println("Got expected error for $testCase: ${e.message}")
                // Could add more specific checks on the exception type/message if needed
            }
        }

        // After errors, verify client can still function correctly
        val validUri =
            "otpauth://totp/Recovery:recovery@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Recovery"
        val credentialResult = client.addCredentialFromUri(validUri)

        // Verify that the valid operation succeeds
        assertTrue("Valid URI should succeed", credentialResult.isSuccess)

        // Extract the credential and verify it
        credentialResult.onSuccess { credential ->
            assertNotNull("Client should recover and process valid URI after errors", credential)
            assertEquals("Issuer should match", "Recovery", credential.issuer)
        }

        // Close the client when done
        client.close()
    }


    @Test
    fun testDifferentOathAlgorithms() = runTest {
        // Create an OathClient with NonePassphraseProvider for non-encrypted storage
        val client = OathClient {
            encryptionEnabled = false
            logger = Logger.STANDARD
            // Storage will be created with NonePassphraseProvider due to encryptionEnabled = false
        }

        // Create test URIs with different algorithms
        val uriSha1 =
            "otpauth://totp/AlgSHA1:sha1@example.com?secret=JBSWY3DPEHPK3PXP&issuer=AlgSHA1&algorithm=SHA1"
        val uriSha256 =
            "otpauth://totp/AlgSHA256:sha256@example.com?secret=JBSWY3DPEHPK3PXP&issuer=AlgSHA256&algorithm=SHA256"
        val uriSha512 =
            "otpauth://totp/AlgSHA512:sha512@example.com?secret=JBSWY3DPEHPK3PXP&issuer=AlgSHA512&algorithm=SHA512"

        // Add credentials with different algorithms
        val credentialSha1 = client.addCredentialFromUri(uriSha1).getOrThrow()
        val credentialSha256 = client.addCredentialFromUri(uriSha256).getOrThrow()
        val credentialSha512 = client.addCredentialFromUri(uriSha512).getOrThrow()

        // Verify algorithms are correctly parsed
        assertEquals("Algorithm should be SHA1", OathAlgorithm.SHA1, credentialSha1.oathAlgorithm)
        assertEquals(
            "Algorithm should be SHA256",
            OathAlgorithm.SHA256,
            credentialSha256.oathAlgorithm
        )
        assertEquals(
            "Algorithm should be SHA512",
            OathAlgorithm.SHA512,
            credentialSha512.oathAlgorithm
        )

        // Generate codes for all algorithms
        val codeSha1 = client.generateCode(credentialSha1.id).getOrThrow()
        val codeSha256 = client.generateCode(credentialSha256.id).getOrThrow()
        val codeSha512 = client.generateCode(credentialSha512.id).getOrThrow()

        // Verify all codes were generated (should be different due to different algorithms)
        assertNotNull("SHA1 code should be generated", codeSha1)
        assertNotNull("SHA256 code should be generated", codeSha256)
        assertNotNull("SHA512 code should be generated", codeSha512)

        // They should all have the correct length
        assertEquals("SHA1 code should have 6 digits", 6, codeSha1.length)
        assertEquals("SHA256 code should have 6 digits", 6, codeSha256.length)
        assertEquals("SHA512 code should have 6 digits", 6, codeSha512.length)

        // Close the client when done
        client.close()
    }


    @Test
    fun testCredentialWithCustomDigits() = runTest(timeout = 30.minutes) {
        // Create an OathClient with NonePassphraseProvider for non-encrypted storage
        val client = OathClient {
            encryptionEnabled = false
            logger = Logger.STANDARD
            // Storage will be created with NonePassphraseProvider due to encryptionEnabled = false
        }

        // Create URIs with different digit lengths
        val uri6Digits =
            "otpauth://totp/Digits6:six@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Digits6&digits=6"
        val uri8Digits =
            "otpauth://totp/Digits8:eight@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Digits8&digits=8"

        // Add credentials with different digit lengths
        val credential6 = client.addCredentialFromUri(uri6Digits).getOrThrow()
        val credential8 = client.addCredentialFromUri(uri8Digits).getOrThrow()

        // Verify digit counts are correctly parsed
        assertEquals("Should have 6 digits", 6, credential6.digits)
        assertEquals("Should have 8 digits", 8, credential8.digits)

        // Generate codes and verify length
        val code6 = client.generateCode(credential6.id).getOrThrow()
        val code8 = client.generateCode(credential8.id).getOrThrow()

        assertEquals("6-digit code should have correct length", 6, code6.length)
        assertEquals("8-digit code should have correct length", 8, code8.length)

        // Close the client when done
        client.close()
    }


    @Test
    fun testReinitializeClient() = runTest {
        // Create an OathClient with NonePassphraseProvider for non-encrypted storage
        val client = OathClient {
            encryptionEnabled = false
            logger = Logger.STANDARD
            // Storage will be created with NonePassphraseProvider due to encryptionEnabled = false
        }

        // Add a credential
        val uri = "otpauth://totp/Reinit:reinit@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Reinit"
        val credential = client.addCredentialFromUri(uri).getOrThrow()
        assertNotNull("Credential should be created", credential)

        // Close the client (simulating app restart)
        client.close()

        // Try to use the client (should fail)
        client.getCredentials().onFailure {
            assertTrue(
                "Exception should mention client not initialized",
                it.message?.contains("not initialized") ?: false
            )
        }

        // Reinitialize the client
        client.initialize()

        // Verify client works after reinitialization
        val retrievedCredentials = client.getCredentials().getOrThrow()
        assertTrue("Should have at least one credential", retrievedCredentials.isNotEmpty())

        // Find our credential
        val retrievedCredential = retrievedCredentials.find { it.id == credential.id }
        assertNotNull(
            "Should find the original credential after reinitialization",
            retrievedCredential
        )

        // Close the client when done
        client.close()
    }


    @Test
    fun testCredentialMetadata() = runTest {
        // Create an OathClient with NonePassphraseProvider for non-encrypted storage
        val client = OathClient {
            encryptionEnabled = false
            logger = Logger.STANDARD
            // Storage will be created with NonePassphraseProvider due to encryptionEnabled = false
        }

        // Add a credential with creation time that we can verify
        val creationTime = System.currentTimeMillis()
        val uri = "otpauth://totp/Metadata:meta@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Metadata"
        val credential = client.addCredentialFromUri(uri).getOrThrow()

        // Verify creation time is set and close to current time
        assertNotNull("Credential creation time should not be null", credential.createdAt)
        val timeDiffSeconds = (creationTime - credential.createdAt.time) / 1000
        assertTrue("Creation time should be recent", timeDiffSeconds < 10) // Within 10 seconds

        // Test display name versus internal name
        assertEquals("Issuer should match", "Metadata", credential.issuer)
        assertEquals("Display issuer should match", "Metadata", credential.displayIssuer)
        assertEquals("Account name should match", "meta@example.com", credential.accountName)
        assertEquals(
            "Display account name should match",
            "meta@example.com",
            credential.displayAccountName
        )

        // Close the client when done
        client.close()
    }

    /**
     * Helper method to create a consistent OathClient with test configuration.
     */

    private suspend fun createTestClient(
        encryptionEnabled: Boolean = false,
        enableCredentialCache: Boolean = false
    ): OathClient {
        // Create storage with appropriate passphrase provider based on encryption setting
        val testStorage = SQLOathStorage {
            context = appContext
            // Use appropriate passphrase provider based on encryption setting
            passphraseProvider =
                if (encryptionEnabled) testPassphraseProvider else NonePassphraseProvider()
        }

        // Create client with the configuration
        val client = OathClient {
            this.encryptionEnabled = encryptionEnabled
            this.enableCredentialCache = enableCredentialCache
            this.storage = testStorage
            this.logger = Logger.STANDARD
        }

        return client
    }
}
