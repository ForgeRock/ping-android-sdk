/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite.passphrase

import android.content.Context
import android.os.Build
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.BlockstoreClient
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesResponse
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.pingidentity.logger.Logger
import com.pingidentity.storage.sqlite.passphrase.PassphraseProvider.Companion.generateRandomPassphrase
import kotlinx.coroutines.tasks.await

/**
 * PassphraseProvider implementation that uses Android Block Store to securely store the passphrase.
 * Throws exception if Block Store is not available.
 */
class BlockStorePassphraseProvider(
    private val context: Context,
    private val initialPassphrase: String? = null,
    private val logger: Logger = Logger.logger
) : PassphraseProvider {

    companion object {
        private const val PASSPHRASE_KEY = "ping_identity_db_passphrase_blockstore"
    }

    override suspend fun getPassphrase(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw UnsupportedOperationException("Block Store requires API 30+")
        }
        val client = Blockstore.getClient(context)

        try {
            val retrieveRequest = RetrieveBytesRequest.Builder()
                .setKeys(listOf(PASSPHRASE_KEY))
                .build()
            val response: RetrieveBytesResponse = client.retrieveBytes(retrieveRequest).await()
            val dataMap = response.blockstoreDataMap
            val data = dataMap[PASSPHRASE_KEY]?.bytes
            if (data != null && data.isNotEmpty()) {
                val passphrase = String(data, Charsets.UTF_8)
                logger.d("Retrieved passphrase from Block Store")
                return passphrase
            } else {
                logger.d("No passphrase found in Block Store")
            }
        } catch (e: Exception) {
            logger.e("Failed to retrieve passphrase from Block Store: ${e.message}", e)
            // Will handle below
        }

        // If an initial passphrase was provided, use and store it
        if (initialPassphrase != null) {
            logger.d("Using provided initial passphrase for Block Store")
            storePassphrase(client, initialPassphrase)
            return initialPassphrase
        }

        // Generate and store new passphrase
        val newPassphrase = generateRandomPassphrase()
        storePassphrase(client, newPassphrase)
        logger.d("Generated and stored new passphrase in Block Store")
        return newPassphrase
    }

    private suspend fun storePassphrase(client: BlockstoreClient, passphrase: String) {
        try {
            val storeRequest = StoreBytesData.Builder()
                .setKey(PASSPHRASE_KEY)
                .setBytes(passphrase.toByteArray(Charsets.UTF_8))
                .setShouldBackupToCloud(true)
                .build()
            client.storeBytes(storeRequest).await()
        } catch (e: Exception) {
            logger.e("Failed to store passphrase in Block Store: ${e.message}", e)
            throw e
        }
    }
}
