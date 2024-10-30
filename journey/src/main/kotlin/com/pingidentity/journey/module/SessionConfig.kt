/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.pingidentity.android.ContextProvider
import com.pingidentity.storage.DataStoreStorage
import com.pingidentity.storage.EncryptedDataToJsonSerializer
import com.pingidentity.storage.StorageDelegate
import com.pingidentity.storage.encrypt.SecretKeyEncryptor
import com.pingidentity.utils.PingDsl

private const val COM_PING_SDK_V_1_SESSION = "com.pingidentity.sdk.v1.session"

//Default
private val Context.defaultSessionDataStore: DataStore<String?> by dataStore(
    COM_PING_SDK_V_1_SESSION,
    EncryptedDataToJsonSerializer(SecretKeyEncryptor {
        keyAlias = COM_PING_SDK_V_1_SESSION
    })
)

@PingDsl
class SessionConfig {
    lateinit var storage: StorageDelegate<String>
    internal fun init() {
        if (!::storage.isInitialized) {
            storage = DataStoreStorage(ContextProvider.context.defaultSessionDataStore, false)
        }
    }
}