/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.passphrase

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pingidentity.storage.sqlite.passphrase.KeyStorePassphraseProvider.Companion.dataStore
import com.pingidentity.storage.sqlite.passphrase.BlockStorePassphraseProvider
import com.pingidentity.storage.sqlite.passphrase.DataStorePassphraseProvider
import com.pingidentity.storage.sqlite.passphrase.FixedPassphraseProvider
import com.pingidentity.storage.sqlite.passphrase.KeyStorePassphraseProvider
import com.pingidentity.storage.sqlite.passphrase.NonePassphraseProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class PassphraseProviderTest {
    
    private lateinit var context: Context
    private val testPassphrase = "test-passphrase-123456"
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clean up KeyStore
        cleanupKeyStore()
    }
    
    @After
    fun tearDown() {
        // Clean up after tests
        cleanupKeyStore()
    }
    
    // Helper method to clean up KeyStore data
    private fun cleanupKeyStore() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias("ping_identity_db_passphrase_key")) {
                keyStore.deleteEntry("ping_identity_db_passphrase_key")
            }

            // Clear DataStore
            try {
                runBlocking {
                    context.dataStore.edit { preferences ->
                        preferences.clear()
                    }
                }
            } catch (e: Exception) {
                // Ignore DataStore exceptions during cleanup
            }
        } catch (e: Exception) {
            // Ignore exceptions during cleanup
        }
    }
    
    @Test
    fun testFixedPassphraseProvider() = runTest {
        // Test with a specified passphrase
        val provider1 = FixedPassphraseProvider(testPassphrase)
        assertEquals(testPassphrase, provider1.getPassphrase())
        
        // Verify that a FixedPassphraseProvider is created with the correct passphrase
        val fixedProvider = FixedPassphraseProvider(testPassphrase)
        assertEquals(testPassphrase, fixedProvider.getPassphrase())
        
        // Test with auto-generated passphrase
        val provider2 = FixedPassphraseProvider()
        val generatedPassphrase = provider2.getPassphrase()
        assertNotNull(generatedPassphrase)
        assertTrue(generatedPassphrase.isNotEmpty())
        
        // Verify that the passphrase is consistent across calls
        assertEquals(generatedPassphrase, provider2.getPassphrase())
        
        // Verify that different instances with auto-generated passphrases get different values
        val provider3 = FixedPassphraseProvider()
        assertNotEquals(provider2.getPassphrase(), provider3.getPassphrase())
    }
    
    @Test
    fun testKeyStorePassphraseProvider() = runTest {
        // Create a provider with no initial passphrase
        val provider1 = KeyStorePassphraseProvider(context)
        val passphrase1 = provider1.getPassphrase()
        assertNotNull(passphrase1)
        assertTrue(passphrase1.isNotEmpty())
        
        // Create a new instance and verify it returns the same passphrase
        val provider2 = KeyStorePassphraseProvider(context)
        assertEquals(passphrase1, provider2.getPassphrase())
        
        // Clean up
        cleanupKeyStore()
        
        // Test with an initial passphrase
        val provider3 = KeyStorePassphraseProvider(context, testPassphrase)
        assertEquals(testPassphrase, provider3.getPassphrase())
        
        // Create a new instance without an initial passphrase
        // It should retrieve the previously stored passphrase
        val provider4 = KeyStorePassphraseProvider(context)
        assertEquals(testPassphrase, provider4.getPassphrase())
    }
    
    @Test
    fun testInitialPassphraseOverrideForKeyStore() = runTest {
        // First initialize with a random passphrase
        cleanupKeyStore()
        val ksProvider1 = KeyStorePassphraseProvider(context)
        val ksPassphrase1 = ksProvider1.getPassphrase()
        
        // Then try to override with an initial passphrase - it should NOT override
        val ksProvider2 = KeyStorePassphraseProvider(context, "override-attempt")
        assertEquals(ksPassphrase1, ksProvider2.getPassphrase())
        assertNotEquals("override-attempt", ksProvider2.getPassphrase())
    }
    
    @Test
    fun testDataStorePassphraseProvider() = runTest {
        // Make sure we start with a clean state
        cleanupKeyStore()
        
        // Test with an initial passphrase
        val provider1 = DataStorePassphraseProvider(context, testPassphrase)
        val passphrase1 = provider1.getPassphrase()
        assertEquals(testPassphrase, passphrase1)
        
        // Create a new instance and verify it returns the same passphrase
        val provider2 = DataStorePassphraseProvider(context)
        assertEquals(testPassphrase, provider2.getPassphrase())
        
        // Clean up
        cleanupKeyStore()
        
        // Test without an initial passphrase (generates random)
        val provider3 = DataStorePassphraseProvider(context)
        val generatedPassphrase = provider3.getPassphrase()
        assertNotNull(generatedPassphrase)
        assertTrue(generatedPassphrase.isNotEmpty())
        
        // Create a new instance without an initial passphrase
        // It should retrieve the previously stored passphrase
        val provider4 = DataStorePassphraseProvider(context)
        assertEquals(generatedPassphrase, provider4.getPassphrase())
    }
    
    @Test
    fun testInitialPassphraseOverrideForDataStore() = runTest {
        // Make sure we start with a clean state
        cleanupKeyStore()
        
        // First initialize with a specific passphrase
        val dsProvider1 = DataStorePassphraseProvider(context, testPassphrase)
        val dsPassphrase1 = dsProvider1.getPassphrase()
        assertEquals(testPassphrase, dsPassphrase1)
        
        // Then try to override with a different initial passphrase - it should NOT override
        val dsProvider2 = DataStorePassphraseProvider(context, "override-attempt")
        assertEquals(testPassphrase, dsProvider2.getPassphrase())
        assertNotEquals("override-attempt", dsProvider2.getPassphrase())
    }
    
    @Test
    fun testBlockStorePassphraseProvider() = runTest {
        // Skip test if API level is below 30 (Android 11)
        Assume.assumeTrue("BlockStore requires API 30+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        
        try {
            // Clean up any existing data
            cleanupKeyStore()
            
            // Test with an initial passphrase
            val provider1 = BlockStorePassphraseProvider(context, testPassphrase)
            val passphrase1 = provider1.getPassphrase()
            assertEquals(testPassphrase, passphrase1)
            
            // Create a new instance and verify it returns the same passphrase
            val provider2 = BlockStorePassphraseProvider(context)
            assertEquals(testPassphrase, provider2.getPassphrase())
            
            // Clean up
            cleanupKeyStore()
            
            // Test without an initial passphrase (generates random)
            val provider3 = BlockStorePassphraseProvider(context)
            val generatedPassphrase = provider3.getPassphrase()
            assertNotNull(generatedPassphrase)
            assertTrue(generatedPassphrase.isNotEmpty())
            
            // Create a new instance without an initial passphrase
            // It should retrieve the previously stored passphrase
            val provider4 = BlockStorePassphraseProvider(context)
            assertEquals(generatedPassphrase, provider4.getPassphrase())
            
            // Test that initial passphrase doesn't override existing one
            val provider5 = BlockStorePassphraseProvider(context, "override-attempt")
            assertEquals(generatedPassphrase, provider5.getPassphrase())
            assertNotEquals("override-attempt", provider5.getPassphrase())
        } catch (e: Exception) {
            // This test might fail on emulators or devices without proper
            // Google Play Services or BlockStore support
            // We'll assume the test is skipped in these cases
            Assume.assumeNoException("BlockStore not available on this device", e)
        }
    }
    
    @Test
    fun testNonePassphraseProvider() = runTest {
        val provider = NonePassphraseProvider()
        
        // Verify that it always returns an empty string
        assertEquals("", provider.getPassphrase())
        
        // Create a new instance and verify it also returns an empty string
        val provider2 = NonePassphraseProvider()
        assertEquals("", provider2.getPassphrase())
        
        // Verify both instances return the same value
        assertEquals(provider.getPassphrase(), provider2.getPassphrase())
    }
}
