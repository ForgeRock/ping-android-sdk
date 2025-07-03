/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.pingidentity.mfa.commons.exception.MfaStorageException
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.oath.OathStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.core.content.edit

/**
 * Android's SharedPreferences implementation of [OathStorage]. This implementation is useful to
 * showcase how to implement a custom storage solution rather than using the default encrypted
 * SQLite storage provided.
 *
 * Please, note SharedPreferences stores data as plaintext JSON. This is not secure and should not
 * be used for highly sensitive applications.
 */
class SharedPrefsOathStorage(context: Context, prefName: String = DEFAULT_PREFS_NAME) : OathStorage {
    
    companion object {
        private const val TAG = "SharedPrefsOathStorage"
        private const val DEFAULT_PREFS_NAME = "com.pingidentity.mfa.oath.prefs"
        private const val KEY_CREDENTIALS_PREFIX = "oath_credential_"
        private const val KEY_METADATA = "oath_metadata"
        private const val KEY_INITIALIZED = "storage_initialized"
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        prefName, Context.MODE_PRIVATE
    )
    
    private val json = Json { 
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private var isInitialized = false
    
    /**
     * Get all credential IDs from the metadata.
     */
    private fun getCredentialIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_METADATA, emptySet()) ?: emptySet()
    }
    
    /**
     * Update the credential IDs in the metadata.
     */
    private fun updateCredentialIds(ids: Set<String>) {
        sharedPreferences.edit { putStringSet(KEY_METADATA, ids) }
    }
    
    /**
     * Generate the key for a credential in SharedPreferences.
     */
    private fun getCredentialKey(credentialId: String): String {
        return "$KEY_CREDENTIALS_PREFIX$credentialId"
    }
    
    /**
     * Check if the storage is initialized and throw an exception if not.
     */
    private fun checkInitialized() {
        if (!isInitialized) {
            throw MfaStorageException("Storage not initialized. Call initialize() first.")
        }
    }
    
    override fun initialize() {
        try {
            // Check if already initialized
            if (sharedPreferences.contains(KEY_INITIALIZED)) {
                isInitialized = true
                return
            }
            
            // Create the metadata set if it doesn't exist
            if (!sharedPreferences.contains(KEY_METADATA)) {
                updateCredentialIds(emptySet())
            }
            
            // Mark as initialized
            sharedPreferences.edit { putBoolean(KEY_INITIALIZED, true) }
            isInitialized = true
            
            Log.i(TAG, "Storage initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize storage", e)
            throw MfaStorageException("Failed to initialize storage: ${e.message}", e)
        }
    }
    
    override fun clear() {
        try {
            checkInitialized()
            
            // Clear all data
            sharedPreferences.edit { clear() }
            
            // Re-initialize
            sharedPreferences.edit { putBoolean(KEY_INITIALIZED, true) }
            updateCredentialIds(emptySet())
            
            Log.i(TAG, "Storage cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear storage", e)
            throw MfaStorageException("Failed to clear storage: ${e.message}", e)
        }
    }
    
    override fun storeOathCredential(credential: OathCredential) {
        try {
            checkInitialized()
            
            val json = credential.toJson()
            val credentialKey = getCredentialKey(credential.id)
            
            // Store the credential
            sharedPreferences.edit { putString(credentialKey, json) }
            
            // Update metadata
            val ids = getCredentialIds().toMutableSet()
            ids.add(credential.id)
            updateCredentialIds(ids)
            
            Log.d(TAG, "Stored credential with ID: ${credential.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store credential", e)
            throw MfaStorageException("Failed to store credential: ${e.message}", e)
        }
    }
    
    override fun retrieveOathCredential(credentialId: String): OathCredential? {
        try {
            checkInitialized()
            
            val credentialKey = getCredentialKey(credentialId)
            val json = sharedPreferences.getString(credentialKey, null) ?: return null
            
            return OathCredential.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve credential", e)
            throw MfaStorageException("Failed to retrieve credential: ${e.message}", e)
        }
    }
    
    override fun getAllOathCredentials(): List<OathCredential> {
        try {
            checkInitialized()
            
            val ids = getCredentialIds()
            return ids.mapNotNull { retrieveOathCredential(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all credentials", e)
            throw MfaStorageException("Failed to get all credentials: ${e.message}", e)
        }
    }
    
    override fun removeOathCredential(credentialId: String): Boolean {
        try {
            checkInitialized()
            
            val credentialKey = getCredentialKey(credentialId)
            
            // Check if credential exists
            if (!sharedPreferences.contains(credentialKey)) {
                return false
            }
            
            // Remove the credential
            sharedPreferences.edit { remove(credentialKey) }
            
            // Update metadata
            val ids = getCredentialIds().toMutableSet()
            ids.remove(credentialId)
            updateCredentialIds(ids)
            
            Log.d(TAG, "Removed credential with ID: $credentialId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove credential", e)
            throw MfaStorageException("Failed to remove credential: ${e.message}", e)
        }
    }
    
    override fun clearOathCredentials() {
        try {
            checkInitialized()
            
            val ids = getCredentialIds()
            
            // Remove all credentials
            sharedPreferences.edit {
                for (id in ids) {
                    remove(getCredentialKey(id))
                }

                // Clear metadata but keep initialization flag
                remove(KEY_METADATA)
            }
            
            // Reset metadata
            updateCredentialIds(emptySet())
            
            Log.i(TAG, "Cleared all OATH credentials")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear credentials", e)
            throw MfaStorageException("Failed to clear credentials: ${e.message}", e)
        }
    }
    
    override fun close() {
        // No resources to close for SharedPreferences
        isInitialized = false
    }
}
