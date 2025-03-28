/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.encrypt

interface Encryptor {
    /**
     * Encrypts the given data.
     *
     * @param data The data to encrypt.
     *
     * @return The encrypted data.
     */
    suspend fun encrypt(data: ByteArray): ByteArray

    /**
     * Decrypts the given data.
     *
     * @param data The data to decrypt.
     *
     * @return The decrypted data.
     */
    suspend fun decrypt(data: ByteArray): ByteArray
}