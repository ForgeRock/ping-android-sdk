/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.pingidentity.android.ContextProvider
import com.pingidentity.mfa.commons.storage.TestPassphraseProvider
import com.pingidentity.mfa.oath.OathAlgorithm
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.oath.OathType
import com.pingidentity.storage.sqlite.passphrase.FixedPassphraseProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
import java.util.Date
import java.util.UUID

/**
 * Instrumented test specifically for SQLOathStorage to verify its
 * database operations in a real Android environment.
 *
 * This test focuses on directly testing the database layer functionality
 * including table creation, credential CRUD operations, and error handling.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SQLOathStorageTest {

    private lateinit var appContext: Context
    private lateinit var storage: SQLOathStorage
    private val testDbName = "test_oath_storage_${System.currentTimeMillis()}.db"
    private val testPassphraseProvider = TestPassphraseProvider()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() = runTest {
        // Get the application context
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Initialize ContextProvider with the actual app context
        ContextProvider.init(appContext)

        // Create storage instance with a unique test database name and encryption disabled for testing
        storage = SQLOathStorage {
            context = appContext
            databaseName = testDbName
            encryptionEnabled = false
            passphraseProvider = testPassphraseProvider
        }

        // Initialize the storage to ensure tables are created
        storage.initialize()
        
        // Clean up any existing test data
        cleanupTestData()
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() = runTest {
        // Ensure storage is closed after each test
        try {
            storage.close()
        } catch (e: Exception) {
            println("Warning: Failed to close storage: ${e.message}")
        }
    }
    
    /**
     * Helper method to clean up any test data
     */
    private suspend fun cleanupTestData() {
        try {
            // Clear all OATH credentials from the database
            storage.clearOathCredentials()
        } catch (e: Exception) {
            println("Warning: Failed to clean up test data: ${e.message}")
        }
    }
    
    /**
     * Helper method to create a test credential with unique ID
     */
    private fun createTestCredential(
        id: String = UUID.randomUUID().toString(),
        issuer: String = "Test Issuer",
        accountName: String = "test@example.com"
    ): OathCredential {
        return OathCredential(
            id = id,
            userId = "test-user",
            resourceId = "test-resource",
            issuer = issuer,
            displayIssuer = issuer,
            accountName = accountName,
            displayAccountName = accountName,
            oathType = OathType.TOTP,
            secret = "JBSWY3DPEHPK3PXP",
            oathAlgorithm = OathAlgorithm.SHA1,
            digits = 6,
            period = 30,
            counter = 0,
            createdAt = Date(),
            imageURL = null,
            backgroundColor = null,
            policies = null,
            lockingPolicy = null,
            isLocked = false
        )
    }

    /**
     * Test that database and tables are created successfully during initialization
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testDatabaseInitialization() = runTest {
        // Create a new storage instance with a different database name
        val initDbName = "init_test_db_${System.currentTimeMillis()}.db"
        val initStorage = SQLOathStorage {
            context = appContext
            databaseName = initDbName
            encryptionEnabled = false
            passphraseProvider = testPassphraseProvider
        }
        
        try {
            // Initialize the storage - this should create the database and tables
            initStorage.initialize()
            
            // If no exception was thrown, the initialization was successful
            // Attempt a basic operation to verify tables were created correctly
            val testCred = createTestCredential()
            
            // Store the credential - this will fail if tables weren't created properly
            initStorage.storeOathCredential(testCred)
            
            // Retrieve the credential to verify it was stored correctly
            val retrievedCred = initStorage.retrieveOathCredential(testCred.id)
            
            // Verify the credential was stored and retrieved correctly
            assertNotNull("Retrieved credential should not be null", retrievedCred)
            assertEquals("Retrieved credential ID should match original", testCred.id, retrievedCred?.id)
            
            // Clean up
            initStorage.clearOathCredentials()
            
            // Test passed if we got here without exceptions
            assertTrue("Database initialization successful", true)
        } finally {
            // Ensure the test storage is closed
            initStorage.close()
        }
    }
    
    /**
     * Test storing and retrieving a credential
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testStoreAndRetrieveCredential() = runTest {
        // Create a test credential
        val testCred = createTestCredential()
        
        // Store the credential
        storage.storeOathCredential(testCred)
        
        // Retrieve the credential
        val retrievedCred = storage.retrieveOathCredential(testCred.id)
        
        // Verify the credential was retrieved correctly
        assertNotNull("Retrieved credential should not be null", retrievedCred)
        assertEquals("ID should match", testCred.id, retrievedCred?.id)
        assertEquals("Issuer should match", testCred.issuer, retrievedCred?.issuer)
        assertEquals("Account name should match", testCred.accountName, retrievedCred?.accountName)
        assertEquals("OTP type should match", testCred.oathType, retrievedCred?.oathType)
        assertEquals("Algorithm should match", testCred.oathAlgorithm, retrievedCred?.oathAlgorithm)
        assertEquals("Digits should match", testCred.digits, retrievedCred?.digits)
        assertEquals("Period should match", testCred.period, retrievedCred?.period)
    }
    
    /**
     * Test retrieving all credentials
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetAllCredentials() = runTest {
        // Ensure we start with an empty database
        val initialCredentials = storage.getAllOathCredentials()
        assertEquals("Database should be empty initially", 0, initialCredentials.size)
        
        // Create and store multiple test credentials
        val credential1 = createTestCredential(issuer = "Issuer1", accountName = "user1@example.com")
        val credential2 = createTestCredential(issuer = "Issuer2", accountName = "user2@example.com")
        val credential3 = createTestCredential(issuer = "Issuer3", accountName = "user3@example.com")
        
        // Store all credentials
        storage.storeOathCredential(credential1)
        storage.storeOathCredential(credential2)
        storage.storeOathCredential(credential3)
        
        // Retrieve all credentials
        val allCredentials = storage.getAllOathCredentials()
        
        // Verify all credentials were retrieved
        assertEquals("Should retrieve all 3 credentials", 3, allCredentials.size)
        
        // Verify each credential was retrieved correctly
        assertTrue("Should contain credential 1", 
            allCredentials.any { it.id == credential1.id && it.issuer == "Issuer1" })
        assertTrue("Should contain credential 2", 
            allCredentials.any { it.id == credential2.id && it.issuer == "Issuer2" })
        assertTrue("Should contain credential 3", 
            allCredentials.any { it.id == credential3.id && it.issuer == "Issuer3" })
    }
    
    /**
     * Test removing a credential
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRemoveCredential() = runTest {
        // Create and store a test credential
        val testCred = createTestCredential()
        storage.storeOathCredential(testCred)
        
        // Verify credential exists
        val retrievedBefore = storage.retrieveOathCredential(testCred.id)
        assertNotNull("Credential should exist before removal", retrievedBefore)
        
        // Remove the credential
        val removed = storage.removeOathCredential(testCred.id)
        
        // Verify removal was successful
        assertTrue("Removal should be successful", removed)
        
        // Verify credential no longer exists
        val retrievedAfter = storage.retrieveOathCredential(testCred.id)
        assertNull("Credential should not exist after removal", retrievedAfter)
    }
    
    /**
     * Test removing a credential that doesn't exist
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testRemoveNonExistentCredential() = runTest {
        // Attempt to remove a credential with a non-existent ID
        val removed = storage.removeOathCredential("non_existent_id")
        
        // Verify removal was not successful
        assertFalse("Removal of non-existent credential should return false", removed)
    }
    
    /**
     * Test clearing all credentials
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testClearCredentials() = runTest {
        // Create and store multiple test credentials
        val credential1 = createTestCredential()
        val credential2 = createTestCredential()
        
        storage.storeOathCredential(credential1)
        storage.storeOathCredential(credential2)
        
        // Verify credentials exist
        val beforeClear = storage.getAllOathCredentials()
        assertEquals("Should have 2 credentials before clearing", 2, beforeClear.size)
        
        // Clear all credentials
        storage.clearOathCredentials()
        
        // Verify all credentials were removed
        val afterClear = storage.getAllOathCredentials()
        assertEquals("Should have 0 credentials after clearing", 0, afterClear.size)
    }
    
    /**
     * Test updating a credential
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testUpdateCredential() = runTest {
        // Create and store a test credential
        val originalCred = createTestCredential(issuer = "Original Issuer")
        storage.storeOathCredential(originalCred)
        
        // Create an updated version of the credential with the same ID
        val updatedCred = createTestCredential(
            id = originalCred.id,
            issuer = "Updated Issuer",
            accountName = "updated@example.com"
        )
        
        // Store the updated credential (this should overwrite the original)
        storage.storeOathCredential(updatedCred)
        
        // Retrieve the credential
        val retrievedCred = storage.retrieveOathCredential(originalCred.id)
        
        // Verify the credential was updated
        assertNotNull("Retrieved credential should not be null", retrievedCred)
        assertEquals("ID should remain the same", originalCred.id, retrievedCred?.id)
        assertEquals("Issuer should be updated", "Updated Issuer", retrievedCred?.issuer)
        assertEquals("Account name should be updated", "updated@example.com", retrievedCred?.accountName)
    }
    
    /**
     * Test different oath types
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testDifferentOathTypes() = runTest {
        // Create TOTP and HOTP credentials
        val totpCred = createTestCredential(issuer = "TOTP Test")
        
        val hotpCred = createTestCredential(issuer = "HOTP Test").copy(
            oathType = OathType.HOTP,
            counter = 42 // Set a non-zero counter for HOTP
        )
        
        // Store both credentials
        storage.storeOathCredential(totpCred)
        storage.storeOathCredential(hotpCred)
        
        // Retrieve both credentials
        val retrievedTotp = storage.retrieveOathCredential(totpCred.id)
        val retrievedHotp = storage.retrieveOathCredential(hotpCred.id)
        
        // Verify TOTP credential
        assertNotNull("TOTP credential should be retrieved", retrievedTotp)
        assertEquals("TOTP type should be preserved", OathType.TOTP, retrievedTotp?.oathType)
        assertEquals("TOTP period should be preserved", 30, retrievedTotp?.period)
        
        // Verify HOTP credential
        assertNotNull("HOTP credential should be retrieved", retrievedHotp)
        assertEquals("HOTP type should be preserved", OathType.HOTP, retrievedHotp?.oathType)
        assertEquals("HOTP counter should be preserved", 42L, retrievedHotp?.counter)
    }
    
    /**
     * Test database behavior with different OATH algorithms
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testDifferentAlgorithms() = runTest {
        // Create credentials with different algorithms
        val sha1Cred = createTestCredential(issuer = "SHA1 Test")
        
        val sha256Cred = createTestCredential(issuer = "SHA256 Test").copy(
            oathAlgorithm = OathAlgorithm.SHA256
        )
        
        val sha512Cred = createTestCredential(issuer = "SHA512 Test").copy(
            oathAlgorithm = OathAlgorithm.SHA512
        )
        
        // Store all credentials
        storage.storeOathCredential(sha1Cred)
        storage.storeOathCredential(sha256Cred)
        storage.storeOathCredential(sha512Cred)
        
        // Retrieve all credentials
        val retrievedSha1 = storage.retrieveOathCredential(sha1Cred.id)
        val retrievedSha256 = storage.retrieveOathCredential(sha256Cred.id)
        val retrievedSha512 = storage.retrieveOathCredential(sha512Cred.id)
        
        // Verify algorithms were preserved
        assertEquals("SHA1 algorithm should be preserved", OathAlgorithm.SHA1, retrievedSha1?.oathAlgorithm)
        assertEquals("SHA256 algorithm should be preserved", OathAlgorithm.SHA256, retrievedSha256?.oathAlgorithm)
        assertEquals("SHA512 algorithm should be preserved", OathAlgorithm.SHA512, retrievedSha512?.oathAlgorithm)
    }
    
    /**
     * Test reopening the database works correctly
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testReopenDatabase() = runTest {
        // Create and store a test credential
        val testCred = createTestCredential()
        storage.storeOathCredential(testCred)
        
        // Close the storage
        storage.close()
        
        // Create a new storage instance with the same database name
        val reopenedStorage = SQLOathStorage {
            context = appContext
            databaseName = testDbName
            encryptionEnabled = false
            passphraseProvider = testPassphraseProvider
        }
        
        try {
            // Initialize the storage
            reopenedStorage.initialize()
            
            // Retrieve the credential from the reopened database
            val retrievedCred = reopenedStorage.retrieveOathCredential(testCred.id)
            
            // Verify the credential was retrieved correctly
            assertNotNull("Retrieved credential should not be null after reopening", retrievedCred)
            assertEquals("ID should match after reopening", testCred.id, retrievedCred?.id)
            assertEquals("Issuer should match after reopening", testCred.issuer, retrievedCred?.issuer)
        } finally {
            // Ensure the reopened storage is closed
            reopenedStorage.close()
        }
    }
    
    /**
     * Test direct table creation with raw SQL
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testDirectTableCreation() = runTest {
        // This test verifies that SQLOathStorage can directly create its tables
        println("Starting testDirectTableCreation")
        
        // Get application context
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Create a unique test database name
        val testDbName = "direct_table_test_${System.currentTimeMillis()}.db"
        
        // Delete any existing database with this name
        context.deleteDatabase(testDbName)
        
        // Create a storage instance with the test database name
        var storage: SQLOathStorage? = null
        
        try {
            // Create the storage instance
            storage = SQLOathStorage {
                this.context = context
                databaseName = testDbName
                encryptionEnabled = false
                passphraseProvider = testPassphraseProvider
            }
            
            // Initialize the storage (this should create tables)
            storage.initialize()
            
            // Just verify that initialization completes without exception
            assertTrue("Successfully initialized SQLOathStorage with tables", true)
        } catch (e: Exception) {
            e.printStackTrace()
            fail("Failed to create tables: ${e.message}")
        } finally {
            // Clean up
            try {
                storage?.close()
            } catch (e: Exception) {
                println("Warning: Error closing storage: ${e.message}")
            }
            
            // Delete the test database
            context.deleteDatabase(testDbName)
        }
    }
    
    /**
     * Test minimal operations to verify database functionality
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testMinimalDatabaseOperations() = runTest {
        // This test performs a minimal database operation after initializing
        // SQLOathStorage to verify the tables are properly created
        println("Starting testMinimalDatabaseOperations")
        
        // Get application context
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Create a unique test database name
        val testDbName = "minimal_ops_test_${System.currentTimeMillis()}.db"
        println("Using test database: $testDbName")
        
        // Delete any existing database with this name
        context.deleteDatabase(testDbName)
        println("Deleted any existing database")
        
        // Create a storage instance with the test database name
        var storage: SQLOathStorage? = null
        
        try {
            // Create the storage instance
            println("Creating SQLOathStorage instance")
            storage = SQLOathStorage {
                this.context = context
                databaseName = testDbName
                encryptionEnabled = false
                passphraseProvider = testPassphraseProvider
            }
            
            // Initialize the storage (this should create tables)
            println("Initializing SQLOathStorage")
            storage.initialize()
            println("SQLOathStorage initialized successfully")
            
            // Create a simple test credential
            println("Creating test credential")
            val testCred = OathCredential(
                id = "minimal-test-id",
                userId = "test-user",
                resourceId = "test-resource",
                issuer = "MinimalTest",
                displayIssuer = "MinimalTest",
                accountName = "minimal@test.com",
                displayAccountName = "minimal@test.com",
                oathType = OathType.TOTP,
                secret = "JBSWY3DPEHPK3PXP",
                oathAlgorithm = OathAlgorithm.SHA1,
                digits = 6,
                period = 30,
                counter = 0,
                createdAt = Date(), // Explicitly set creation date
                imageURL = null,     // Explicitly set null for optional fields
                backgroundColor = null,
                policies = null,
                lockingPolicy = null,
                isLocked = false    // Explicitly set boolean field
            )
            
            println("OathCredential created: $testCred")
            println("OathCredential fields: id=${testCred.id}, type=${testCred.oathType}, algorithm=${testCred.oathAlgorithm}, createdAt=${testCred.createdAt}")
            
            // Try to store the credential
            println("Storing credential")
            storage.storeOathCredential(testCred)
            println("Successfully stored credential in initialized database")
            
            // Verify we can retrieve all credentials
            println("Retrieving all credentials")
            val allCreds = storage.getAllOathCredentials()
            println("Found ${allCreds.size} credentials")
            assertEquals("Should have 1 credential", 1, allCreds.size)
            
            // Verify we can find the specific credential
            println("Retrieving specific credential")
            val retrievedCred = storage.retrieveOathCredential("minimal-test-id")
            assertNotNull("Should be able to retrieve the stored credential", retrievedCred)
            assertEquals("Issuer should match", "MinimalTest", retrievedCred?.issuer)
            
            // Test passed
            println("Test passed")
            assertTrue("Minimal database operations successful", true)
        } catch (e: Exception) {
            println("Test failed with exception: ${e.message}")
            e.printStackTrace()
            fail("Failed to perform minimal database operations: ${e.message}")
        } finally {
            // Clean up
            try {
                println("Closing storage")
                storage?.close()
            } catch (e: Exception) {
                println("Warning: Error closing storage: ${e.message}")
            }
            
            // Delete the test database
            println("Deleting test database")
            context.deleteDatabase(testDbName)
        }
    }
    
    /**
     * Test storage builder pattern
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testStorageBuilder() = runTest {
        // Create a new storage instance with the builder
        val builderStorage = SQLOathStorage {
            context = appContext
            databaseName = "builder_test_db_${System.currentTimeMillis()}.db"
            encryptionEnabled = false
            initialPassphrase = "test-secret-key"
            passphraseProvider = testPassphraseProvider
        }
        
        try {
            // Initialize and verify
            builderStorage.initialize()
            assertTrue("Builder-based storage initialization successful", true)
            
            // Store a credential to verify functionality
            val testCred = createTestCredential()
            builderStorage.storeOathCredential(testCred)
            
            // Retrieve and verify
            val retrievedCred = builderStorage.retrieveOathCredential(testCred.id)
            assertNotNull("Retrieved credential should not be null", retrievedCred)
            assertEquals("ID should match", testCred.id, retrievedCred?.id)
        } finally {
            try {
                builderStorage.clearOathCredentials()
                builderStorage.close()
            } catch (e: Exception) {
                println("Warning: Failed to clean up builder storage: ${e.message}")
            }
        }
    }
    
    /**
     * Test creating storage with a custom passphrase
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testStorageWithCustomPassphrase() = runTest {
        // Create a new storage instance with a custom passphrase
        val customPassphrase = "my-custom-passphrase-for-testing"
        val passphraseStorage = SQLOathStorage {
            context = appContext
            databaseName = "passphrase_test_db_${System.currentTimeMillis()}.db"
            encryptionEnabled = true
            // Use a custom fixed passphrase provider instead of the default test provider
            passphraseProvider = FixedPassphraseProvider(customPassphrase)
        }
        
        try {
            // Initialize and verify
            passphraseStorage.initialize()
            assertTrue("Custom passphrase storage initialization successful", true)
            
            // Store a credential to verify functionality
            val testCred = createTestCredential()
            passphraseStorage.storeOathCredential(testCred)
            
            // Retrieve and verify
            val retrievedCred = passphraseStorage.retrieveOathCredential(testCred.id)
            assertNotNull("Retrieved credential should not be null", retrievedCred)
            assertEquals("ID should match", testCred.id, retrievedCred?.id)
        } finally {
            try {
                passphraseStorage.clearOathCredentials()
                passphraseStorage.close()
            } catch (e: Exception) {
                println("Warning: Failed to clean up passphrase storage: ${e.message}")
            }
        }
    }
}