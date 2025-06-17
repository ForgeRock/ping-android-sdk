/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.app.env

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.pingidentity.android.ContextProvider
import com.pingidentity.oidc.Token
import com.pingidentity.orchestrate.module.Cookies
import com.pingidentity.storage.DataStoreStorage
import com.pingidentity.storage.EncryptedDataToJsonSerializer
import com.pingidentity.storage.Storage
import com.pingidentity.storage.encrypt.SecretKeyEncryptor
import java.io.File

private val Context.MyCustomOidcTokenDataStore: DataStore<Token?> by dataStore(
    "com.pingidentity.sdk.v1.tokens",
    EncryptedDataToJsonSerializer(SecretKeyEncryptor {
        keyAlias = "com.example.app.v1.tokens.key1"
        strongBoxPreferred = false
    }),
    ReplaceFileCorruptionHandler { null }
)

private val Context.MyCustomCookieDataStore: DataStore<Cookies?> by dataStore(
    "com.pingidentity.sdk.v1.cookies",
    EncryptedDataToJsonSerializer(SecretKeyEncryptor {
        keyAlias = "com.example.app.v1.cookies.key1"
        strongBoxPreferred = false
    }),
    ReplaceFileCorruptionHandler { null }
)

object CustomStore {

    val MyCustomOidcTokenDataStore by lazy {
        DataStoreStorage(ContextProvider.context.MyCustomOidcTokenDataStore)
    }
    val MyCustomCookieDataStore by lazy {
        DataStoreStorage(ContextProvider.context.MyCustomCookieDataStore)
    }

    val tokenDataStore: Storage<Token> by lazy {
        DataStoreStorage(
            DataStoreFactory.create(
                serializer = EncryptedDataToJsonSerializer(SecretKeyEncryptor {
                    keyAlias = "com.example.app.v1.tokens.key"
                    strongBoxPreferred = false
                }),
                produceFile = {
                    File(
                        ContextProvider.context.filesDir,
                        "datastore/com.example.app.v1.tokens"
                    )
                },
                corruptionHandler = ReplaceFileCorruptionHandler { null }


            )
        )
    }

    val cookiesDataStore: Storage<Cookies> by lazy {
        DataStoreStorage(
            DataStoreFactory.create(
                serializer = EncryptedDataToJsonSerializer(SecretKeyEncryptor {
                    keyAlias = "com.example.app.v1.cookies.key"
                    strongBoxPreferred = false
                }),
                produceFile = {
                    File(
                        ContextProvider.context.filesDir,
                        "datastore/com.example.app.v1.cookies"
                    )
                },
                corruptionHandler = ReplaceFileCorruptionHandler { null }
            )
        )
    }


}