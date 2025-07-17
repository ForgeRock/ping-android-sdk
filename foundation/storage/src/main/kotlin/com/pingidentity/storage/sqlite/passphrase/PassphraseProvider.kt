/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite.passphrase

import android.util.Base64
import java.security.SecureRandom

/**
 * Interface for providing a passphrase for encrypted storage.
 * Implementations should handle the secure storage and retrieval of the passphrase.
 */
interface PassphraseProvider {
    /**
     * Gets the passphrase for encryption/decryption.
     * If a passphrase doesn't exist yet, implementations should generate and store one.
     *
     * @return The passphrase string
     */
    suspend fun getPassphrase(): String
    
    companion object {
        private const val DEFAULT_PASSPHRASE_LENGTH_BYTES = 32 // 256 bits
        
        /**
         * Generate a secure random passphrase.
         *
         * @return A new random passphrase.
         */
        fun generateRandomPassphrase(): String {
            val random = SecureRandom()
            val bytes = ByteArray(DEFAULT_PASSPHRASE_LENGTH_BYTES)
            random.nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
}
