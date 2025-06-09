/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.pingidentity.android.ContextProvider
import com.pingidentity.journey.SSOToken
import com.pingidentity.storage.DataStoreStorage
import com.pingidentity.storage.EncryptedDataToJsonSerializer
import com.pingidentity.storage.Storage
import com.pingidentity.storage.encrypt.SecretKeyEncryptor
import com.pingidentity.utils.PingDsl

private const val COM_PING_SDK_V_1_SESSION = "com.pingidentity.sdk.v1.session"

//Default
private val Context.defaultSessionDataStore: DataStore<SSOToken?> by dataStore(
    COM_PING_SDK_V_1_SESSION,
    EncryptedDataToJsonSerializer(SecretKeyEncryptor {
        keyAlias = COM_PING_SDK_V_1_SESSION
    }), ReplaceFileCorruptionHandler { null }
)

@PingDsl
class SessionConfig {
    /**
     * Storage for storing SSOToken.
     */
    lateinit var storage: Storage<SSOToken>
    internal fun init() {
        if (!::storage.isInitialized) {
            storage = DataStoreStorage(ContextProvider.context.defaultSessionDataStore, false)
        }
    }
}