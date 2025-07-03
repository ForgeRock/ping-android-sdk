/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pingidentity.mfa.commons.exception.MfaStorageException
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.oath.OathType
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

@RunWith(AndroidJUnit4::class)
class SharedPrefsOathStorageTest {

    private lateinit var storage: SharedPrefsOathStorage
    private val testPrefsName = "test_oath_prefs"
    
    // Test data
    private val testSecret = "JBSWY3DPEHPK3PXP"
    private val testIssuer = "Test Issuer"
    private val testAccountName = "testuser@example.com"
    
    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Clear any existing preferences
        appContext.getSharedPreferences(testPrefsName, 0).edit().clear().apply()
        
        // Initialize storage
        storage = SharedPrefsOathStorage(appContext, testPrefsName)
        storage.initialize()
    }
    
    @After
    fun tearDown() {
        // Clean up any test data
        storage.clearOathCredentials()
        
        // Close resources
        storage.close()
    }
    
    @Test
    fun testStoreAndRetrieveCredential() {
        // Create a test credential
        val credential = OathCredential(
            id = UUID.randomUUID().toString(),
            issuer = testIssuer,
            displayIssuer = testIssuer,
            accountName = testAccountName,
            displayAccountName = testAccountName,
            oathType = OathType.TOTP,
            secret = testSecret
        )
        
        // Store the credential
        storage.storeOathCredential(credential)
        
        // Retrieve the credential
        val retrievedCredential = storage.retrieveOathCredential(credential.id)
        
        // Verify
        assertNotNull("Retrieved credential should not be null", retrievedCredential)
        assertEquals("Credential ID should match", credential.id, retrievedCredential?.id)
        assertEquals("Issuer should match", credential.issuer, retrievedCredential?.issuer)
        assertEquals("Account name should match", credential.accountName, retrievedCredential?.accountName)
        assertEquals("OATH type should match", credential.oathType, retrievedCredential?.oathType)
        assertEquals("Secret should match", credential.secret, retrievedCredential?.secret)
    }
    
    @Test
    fun testRetrieveNonExistentCredential() {
        // Try to retrieve a non-existent credential
        val nonExistentId = "non-existent-id"
        val retrievedCredential = storage.retrieveOathCredential(nonExistentId)
        
        // Verify
        assertNull("Retrieved non-existent credential should be null", retrievedCredential)
    }
    
    @Test
    fun testGetAllCredentials_Empty() {
        // Get all credentials when none exist
        val credentials = storage.getAllOathCredentials()
        
        // Verify
        assertTrue("Credential list should be empty", credentials.isEmpty())
    }
    
    @Test
    fun testGetAllCredentials_Multiple() {
        // Create and store multiple credentials
        val credential1 = OathCredential(
            id = "test-id-1",
            issuer = "Issuer 1",
            displayIssuer = "Issuer 1",
            accountName = "user1@example.com",
            displayAccountName = "user1@example.com",
            oathType = OathType.TOTP,
            secret = testSecret
        )
        
        val credential2 = OathCredential(
            id = "test-id-2",
            issuer = "Issuer 2",
            displayIssuer = "Issuer 2",
            accountName = "user2@example.com",
            displayAccountName = "user2@example.com",
            oathType = OathType.HOTP,
            secret = testSecret,
            counter = 5
        )
        
        storage.storeOathCredential(credential1)
        storage.storeOathCredential(credential2)
        
        // Get all credentials
        val credentials = storage.getAllOathCredentials()
        
        // Verify
        assertEquals("Should have 2 credentials", 2, credentials.size)
        assertTrue("Should contain credential 1", credentials.any { it.id == credential1.id })
        assertTrue("Should contain credential 2", credentials.any { it.id == credential2.id })
    }
    
    @Test
    fun testRemoveCredential_Existing() {
        // Create and store a credential
        val credential = OathCredential(
            id = "test-id-to-remove",
            issuer = testIssuer,
            displayIssuer = testIssuer,
            accountName = testAccountName,
            displayAccountName = testAccountName,
            oathType = OathType.TOTP,
            secret = testSecret
        )
        
        storage.storeOathCredential(credential)
        
        // Verify it exists
        assertNotNull(storage.retrieveOathCredential(credential.id))
        
        // Remove the credential
        val result = storage.removeOathCredential(credential.id)
        
        // Verify
        assertTrue("Remove should return true for existing credential", result)
        assertNull("Credential should no longer exist", storage.retrieveOathCredential(credential.id))
    }
    
    @Test
    fun testRemoveCredential_NonExisting() {
        // Try to remove a non-existent credential
        val nonExistentId = "non-existent-id"
        val result = storage.removeOathCredential(nonExistentId)
        
        // Verify
        assertFalse("Remove should return false for non-existent credential", result)
    }
    
    @Test
    fun testClearCredentials() {
        // Create and store multiple credentials
        val credential1 = OathCredential(
            id = "test-id-1",
            issuer = "Issuer 1",
            displayIssuer = "Issuer 1",
            accountName = "user1@example.com",
            displayAccountName = "user1@example.com",
            oathType = OathType.TOTP,
            secret = testSecret
        )
        
        val credential2 = OathCredential(
            id = "test-id-2",
            issuer = "Issuer 2",
            displayIssuer = "Issuer 2",
            accountName = "user2@example.com",
            displayAccountName = "user2@example.com",
            oathType = OathType.HOTP,
            secret = testSecret,
            counter = 5
        )
        
        storage.storeOathCredential(credential1)
        storage.storeOathCredential(credential2)
        
        // Verify they exist
        assertEquals(2, storage.getAllOathCredentials().size)
        
        // Clear all credentials
        storage.clearOathCredentials()
        
        // Verify
        assertTrue("Credential list should be empty after clear", storage.getAllOathCredentials().isEmpty())
    }
    
    @Test
    fun testUpdateCredential() {
        // Create and store a credential
        val credential = OathCredential(
            id = "test-id-to-update",
            issuer = testIssuer,
            displayIssuer = testIssuer,
            accountName = testAccountName,
            displayAccountName = testAccountName,
            oathType = OathType.TOTP,
            secret = testSecret
        )
        
        storage.storeOathCredential(credential)
        
        // Update the credential
        val updatedCredential = credential.copy(
            displayIssuer = "Updated Issuer",
            displayAccountName = "Updated Account"
        )
        
        storage.storeOathCredential(updatedCredential)
        
        // Retrieve and verify
        val retrievedCredential = storage.retrieveOathCredential(credential.id)
        
        assertNotNull("Retrieved credential should not be null", retrievedCredential)
        assertEquals("Updated issuer should match", "Updated Issuer", retrievedCredential?.displayIssuer)
        assertEquals("Updated account name should match", "Updated Account", retrievedCredential?.displayAccountName)
    }

    @Test(expected = MfaStorageException::class)
    fun testCredentialWithMalformedJson() {
        // Get direct access to SharedPreferences
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(testPrefsName, 0)
        
        // Manually insert a malformed JSON
        val credentialId = "malformed-json-id"
        prefs.edit()
            .putString("oath_credential_$credentialId", "{malformed json}")
            .putStringSet("oath_metadata", setOf(credentialId))
            .apply()
        
        // This should throw an MfaStorageException
        storage.retrieveOathCredential(credentialId)
    }
    
    @Test
    fun testInitialize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Create a new storage instance without initializing
        val uninitializedStorage = SharedPrefsOathStorage(context, "uninitialized_test_prefs")
        
        try {
            // This should throw MfaStorageException
            uninitializedStorage.storeOathCredential(
                OathCredential(
                    issuer = testIssuer,
                    displayIssuer = testIssuer,
                    accountName = testAccountName,
                    displayAccountName = testAccountName,
                    oathType = OathType.TOTP,
                    secret = testSecret
                )
            )
            assertTrue("Should have thrown MfaStorageException", false)
        } catch (e: MfaStorageException) {
            // Expected exception
            assertTrue(e.message?.contains("not initialized") ?: false)
        }
        
        // Now initialize the storage
        uninitializedStorage.initialize()
        
        // This should work now
        uninitializedStorage.storeOathCredential(
            OathCredential(
                issuer = testIssuer,
                displayIssuer = testIssuer,
                accountName = testAccountName,
                displayAccountName = testAccountName,
                oathType = OathType.TOTP,
                secret = testSecret
            )
        )
        
        // Clean up
        uninitializedStorage.clear()
        uninitializedStorage.close()
    }
    
    @Test
    fun testClear() {
        // Store some credentials
        val credential = OathCredential(
            issuer = testIssuer,
            displayIssuer = testIssuer,
            accountName = testAccountName,
            displayAccountName = testAccountName,
            oathType = OathType.TOTP,
            secret = testSecret
        )
        
        storage.storeOathCredential(credential)
        
        // Verify it exists
        assertNotNull(storage.retrieveOathCredential(credential.id))
        
        // Clear all data
        storage.clear()
        
        // Verify it's gone
        assertTrue(storage.getAllOathCredentials().isEmpty())
        
        // Storage should still be usable after clear
        storage.storeOathCredential(credential)
        assertNotNull(storage.retrieveOathCredential(credential.id))
    }

}
