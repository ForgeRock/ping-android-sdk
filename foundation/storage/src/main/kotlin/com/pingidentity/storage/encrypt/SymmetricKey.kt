/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.storage.encrypt

import javax.crypto.SecretKey

data class SymmetricKey(
    var secretKey: SecretKey,
    //The encoded Secret key, only available if the key was encrypted by Asymmetric Key.
    //First 4 bytes store the length of the encrypted SecretKey
    //{length of the Encrypted SecretKey}{Encrypted SecretKey}
    val encoded: ByteArray = ByteArray(0)
)