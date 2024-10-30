/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pingidentity.android.ContextProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * A repository for storing serializable objects in encrypted shared preferences.
 *
 * @param T The type of the object to be stored. Must be serializable.
 * @param context The context to use for creating the MasterKey and SharedPreferences. Defaults to ContextProvider.context.
 * @param filename The name of the shared preferences file.
 * @param key The key to use for storing the object in the shared preferences.
 * @param serializer The serializer to use for serializing the object.
 */
class EncryptedSharedPreferencesStorage<T : @Serializable Any>(
    context: Context = ContextProvider.context,
    filename: String,
    private val key: String,
    private val serializer: KSerializer<T>,
) : Storage<T> {
    private var masterKey: MasterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private var sharedPreferences: SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            filename,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    /**
     * Saves the given item in the shared preferences.
     *
     * @param item The item to save.
     */
    override suspend fun save(item: T) {
        sharedPreferences.edit().putString(key, json.encodeToString(serializer, item)).apply()
    }

    /**
     * Retrieves the item from the shared preferences.
     *
     * @return The item if it exists, null otherwise.
     */
    override suspend fun get(): T? {
        return sharedPreferences.getString(key, null)?.let {
            return json.decodeFromString(serializer, it)
        }
    }

    /**
     * Deletes the item from the shared preferences.
     */
    override suspend fun delete() {
        sharedPreferences.edit().remove(key).apply()
    }
}

/**
 * Creates a new Storage instance for storing serializable objects in encrypted shared preferences.
 *
 * @param T The type of the object to be stored. Must be serializable.
 * @param filename The name of the shared preferences file.
 * @param key The key to use for storing the object in the shared preferences. Defaults to the name of the class of T.
 * @param context The context to use for creating the MasterKey and SharedPreferences. Defaults to ContextProvider.context.
 * @param cacheable Whether the storage shokuld cache the object in memory.
 *
 * @return A new Storage instance.
 */
inline fun <reified T : @Serializable Any> EncryptedSharedPreferencesStorage(
    filename: String,
    key: String = T::class.java.name,
    context: Context = ContextProvider.context,
    cacheable: Boolean = false,
): StorageDelegate<T> =
    StorageDelegate(
        EncryptedSharedPreferencesStorage(
            filename = filename,
            context = context,
            key = key,
            serializer = Json.serializersModule.serializer(),
        ),
        cacheable,
    )
