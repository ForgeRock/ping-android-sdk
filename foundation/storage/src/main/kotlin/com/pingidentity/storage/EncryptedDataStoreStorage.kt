/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import com.pingidentity.storage.encrypt.SecretKeyEncryptorConfig
import com.pingidentity.utils.PingDsl

inline fun <reified T : Any> EncryptedDataStoreStorage(block: EncryptedDataStoreStorageConfig.() -> Unit): Storage<T> =
    EncryptedDataStoreStorageFactory.getOrCreate(block)

/**
 * Configuration class for DataStoreStorage.
 * It extends SecretKeyEncryptorConfig to inherit encryption settings.
 */
@PingDsl
class EncryptedDataStoreStorageConfig : SecretKeyEncryptorConfig() {
    lateinit var fileName: String
    var removeFileOnDelete = false
    var cacheStrategy = CacheStrategy.NO_CACHE
}