/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate.module

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.pingidentity.storage.EncryptedDataToJsonSerializer
import com.pingidentity.storage.encrypt.SecretKeyEncryptor

private const val COM_PING_SDK_V_1_COOKIES = "com.pingidentity.sdk.v1.cookies"

/**
 * Default cookie data store
 */
internal val Context.defaultCookieDataStore: DataStore<Cookies?> by dataStore(
    COM_PING_SDK_V_1_COOKIES,
    EncryptedDataToJsonSerializer(SecretKeyEncryptor {
        keyAlias = COM_PING_SDK_V_1_COOKIES
    })
)