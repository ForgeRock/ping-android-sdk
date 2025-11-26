/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import com.pingidentity.journey.SSOToken
import com.pingidentity.logger.Logger
import com.pingidentity.storage.EncryptedDataStoreStorage
import com.pingidentity.storage.EncryptedDataStoreStorageConfig
import com.pingidentity.storage.Storage
import com.pingidentity.utils.PingDsl

private const val COM_PING_SDK_V_1_SESSION = "com.pingidentity.sdk.v1.session"

@PingDsl
class SessionConfig {

    /**
     * Logger instance for logging.
     */
    var logger: Logger = Logger.logger

    /**
     * Storage for storing SSOToken.
     */
    internal lateinit var tokenStorage: Storage<SSOToken>

    /**
     * storage function to create a DataStoreStorage instance.
     */
    var storage: () -> Storage<SSOToken> = {
        EncryptedDataStoreStorage(storageOption)
    }

    /**
     * Default DataStore for SSOToken.
     */
    internal var storageOption: EncryptedDataStoreStorageConfig.() -> Unit = {
        fileName = COM_PING_SDK_V_1_SESSION
        keyAlias = COM_PING_SDK_V_1_SESSION
        logger = this@SessionConfig.logger
    }

    /**
     * Configures the storage for SSOToken.
     * @param block A lambda to configure the DataStoreStorageConfig.
     */
    fun storage(block: EncryptedDataStoreStorageConfig.() -> Unit) {
        val previous = storageOption
        storageOption = {
            previous()
            block()
        }
    }

    /**
     * Initializes the token storage.
     * This should be called before using the SessionConfig.
     */
    internal fun init() {
        if (!::tokenStorage.isInitialized) {
            tokenStorage = storage()
        }
    }
}

