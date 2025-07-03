/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import android.content.Context
import com.pingidentity.storage.sqlite.SQLiteStorage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * A Storage implementation that uses SQLite for persisting data.
 * This implementation can store single objects in an encrypted SQLite database.
 *
 * @param T The type of object to store.
 * @param context The Android context.
 * @param serializer The serializer to use for the objects.
 * @param type The type identifier for the stored objects.
 * @param idProvider A function that provides an ID for each item in collections.
 * @param databaseName The name of the database file.
 * @param tableName The name of the table to use.
 * @param encryptionEnabled Whether to encrypt the database.
 */
class SQLStorage<T : Any>(
    context: Context,
    private val serializer: KSerializer<T>,
    private val type: String,
    private val idProvider: (T) -> String = { it.hashCode().toString() },
    databaseName: String = "pingidentity_storage.db",
    private val tableName: String = "storage_items",
    encryptionEnabled: Boolean = true
) : SQLiteStorage(context, databaseName, encryptionEnabled), Storage<T> {
    
    init {
        // Initialize the database when the storage is created
        initializeDatabase()
        
        // Register the storage table
        registerTableCreator { db ->
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $tableName (
                    type TEXT NOT NULL,
                    id TEXT NOT NULL,
                    data TEXT NOT NULL,
                    PRIMARY KEY (type, id)
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_type ON $tableName (type)")
        }
    }
    
    /**
     * Saves a single object.
     *
     * @param item The item to save.
     */
    override suspend fun save(item: T) {
        val id = idProvider(item)
        val data = json.encodeToString(serializer, item)
        storeItem(tableName, type, id, data)
    }
    
    /**
     * Retrieves a single object.
     *
     * @return The stored object, or null if no object is stored.
     */
    override suspend fun get(): T? {
        // For compatibility with the Storage interface, we'll retrieve
        // the first item of this type
        val items = getAll()
        return if (items.isNotEmpty()) items.first() else null
    }
    
    /**
     * Retrieves all objects of this type.
     *
     * @return A list of all stored objects.
     */
    suspend fun getAll(): List<T> {
        val dataList = retrieveAllItems(tableName, type)
        return dataList.mapNotNull { data ->
            try {
                json.decodeFromString(serializer, data)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Retrieves an object by its ID.
     *
     * @param id The ID of the object to retrieve.
     * @return The object, or null if not found.
     */
    suspend fun getById(id: String): T? {
        val data = retrieveItem(tableName, type, id)
        return data?.let { json.decodeFromString(serializer, it) }
    }
    
    /**
     * Deletes a single object.
     */
    override suspend fun delete() {
        // For compatibility with the Storage interface, we'll delete
        // all items of this type
        deleteAll()
    }
    
    /**
     * Deletes an object by its ID.
     *
     * @param id The ID of the object to delete.
     * @return True if the object was deleted, false if it wasn't found.
     */
    suspend fun deleteById(id: String): Boolean {
        return deleteItem(tableName, type, id)
    }
    
    /**
     * Deletes all objects of this type.
     */
    suspend fun deleteAll() {
        clearItems(tableName, type)
    }
}

/**
 * Creates a new SQLStorage instance for storing objects.
 *
 * @param T The type of object to store.
 * @param context The Android context.
 * @param type The type identifier for the stored objects. If not provided, the class name will be used.
 * @param idProvider A function that provides an ID for each item. If not provided, the hash code will be used.
 * @param databaseName The name of the database file.
 * @param tableName The name of the table to use.
 * @param encryptionEnabled Whether to encrypt the database.
 * @return A new SQLStorage instance.
 */
inline fun <reified T : Any> SQLStorage(
    context: Context,
    type: String = T::class.java.simpleName,
    noinline idProvider: (T) -> String = { it.hashCode().toString() },
    databaseName: String = "pingidentity_storage.db",
    tableName: String = "storage_items",
    encryptionEnabled: Boolean = true
): Storage<T> {
    return SQLStorage(
        context = context,
        serializer = Json.serializersModule.serializer(),
        type = type,
        idProvider = idProvider,
        databaseName = databaseName,
        tableName = tableName,
        encryptionEnabled = encryptionEnabled
    )
}
