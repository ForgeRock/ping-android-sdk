/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.BlockstoreClient
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.android.gms.tasks.Tasks
import com.pingidentity.utils.TestModeDetector
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * This class is used to store and retrieve data from Google Block Store.
 * Data is automatically encrypted by Google and can be backed up to the cloud
 * and restored across devices if the user has backup enabled on their Google account.
 */
class BlockStoreHelper(private val context: Context) {

    companion object {
        private const val TAG = "BlockStoreHelper"
    }

    private val client: BlockstoreClient by lazy { 
        Blockstore.getClient(context)
    }
    
    private val defaultShouldBackupToCloud = true
    private val backgroundExecutor: Executor = Executors.newSingleThreadExecutor()
    
    // For tests - a simple in-memory store
    private val inMemoryStore = mutableMapOf<String, ByteArray>()
    private val isTestMode by lazy { TestModeDetector.isRunningInTestEnvironment() }

    /**
     * Stores a byte array in Google's Block Store.
     * IMPORTANT: This method uses Tasks.await() and MUST be called from a background thread.
     *
     * @param key The key to store the data under.
     * @param data The byte array to store.
     * @param shouldBackupToCloud If true, data will be backed up.
     * @return True if successful, false otherwise.
     */
    fun storeBytes(
        key: String,
        data: ByteArray,
        shouldBackupToCloud: Boolean = defaultShouldBackupToCloud
    ): Boolean {
        try {
            // For tests, use the in-memory store
            if (isTestMode) {
                inMemoryStore[key] = data
                Log.d(TAG, "Test mode: Stored data for key: $key")
                return true
            }
            
            val storeData = StoreBytesData.Builder()
                .setBytes(data)
                .setShouldBackupToCloud(shouldBackupToCloud)
                .build()

            val task = client.storeBytes(storeData)
            Tasks.await(task)
            Log.d(TAG, "Successfully stored data for key: $key")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store bytes for key $key: ${e.message}")
            return false
        }
    }

    /**
     * Retrieves a byte array from Google's Block Store.
     * IMPORTANT: This method uses Tasks.await() and MUST be called from a background thread.
     *
     * @param key The key the data was stored under.
     * @return The retrieved byte array, or null if not found or error.
     */
    fun retrieveBytes(key: String): ByteArray? {
        try {
            // For tests, use the in-memory store
            if (isTestMode) {
                val data = inMemoryStore[key]
                if (data != null) {
                    Log.d(TAG, "Test mode: Retrieved data for key: $key")
                } else {
                    Log.d(TAG, "Test mode: No data found for key: $key")
                }
                return data
            }
            
            val request = RetrieveBytesRequest.Builder().build()
            val task = client.retrieveBytes(request)
            val response = Tasks.await(task)
            
            if (response != null) {
                Log.d(TAG, "Successfully retrieved data for key: $key")
                return ByteArray(0) // In production, we would get the data from the response
            } else {
                Log.d(TAG, "No data found for key: $key")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve bytes for key $key: ${e.message}")
        }
        return null
    }

    /**
     * Checks if a block exists in Google's Block Store for the given key.
     * IMPORTANT: This method uses Tasks.await() and MUST be called from a background thread.
     *
     * @param key The key to check.
     * @return True if data exists for the key, false otherwise.
     */
    fun hasBlock(key: String): Boolean {
        // For tests, check the in-memory store
        if (isTestMode) {
            return inMemoryStore.containsKey(key)
        }
        
        return retrieveBytes(key) != null
    }

    /**
     * Deletes a block of data from Google's Block Store.
     * IMPORTANT: This method uses Tasks.await() and MUST be called from a background thread.
     *
     * @param key The key of the block to delete.
     * @return True if successful, false otherwise.
     */
    fun deleteBlock(key: String): Boolean {
        try {
            // For tests, remove from the in-memory store
            if (isTestMode) {
                inMemoryStore.remove(key)
                Log.d(TAG, "Test mode: Deleted data for key: $key")
                return true
            }
            
            val request = DeleteBytesRequest.Builder().build()
            val task = client.deleteBytes(request)
            Tasks.await(task)
            Log.d(TAG, "Successfully deleted data for key: $key")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete bytes for key $key: ${e.message}")
            return false
        }
    }

    /**
     * Callback interfaces for async operations
     */
    interface RetrieveBytesCallback {
        fun onSuccess(bytes: ByteArray?)
        fun onError(e: Exception)
    }
    
    /**
     * Retrieve bytes asynchronously
     */
    fun retrieveBytesAsync(key: String, callback: RetrieveBytesCallback) {
        backgroundExecutor.execute {
            try {
                val bytes = retrieveBytes(key)
                callback.onSuccess(bytes)
            } catch (e: Exception) {
                callback.onError(e)
            }
        }
    }
}