/*
 * Copyright (c) 2022 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.migration

import android.content.Context
import android.content.SharedPreferences
import com.pingidentity.device.binding.UserKey
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File

/**
 * The SharedPreferences file name and master key alias used by the Legacy SDK
 * for storing device binding repository data.
 */
const val ORG_FORGEROCK_V_1_DEVICE_REPO = "org.forgerock.v1.DEVICE_REPO"

/**
 * JSON key for the unique identifier of a user key.
 */
const val idKey = "id"

/**
 * JSON key for the user identifier associated with a user key.
 */
const val userIdKey = "userId"

/**
 * JSON key for the key identifier (kid) of a user key.
 */
const val kidKey = "kid"

/**
 * JSON key for the authentication type of a user key.
 */
const val authTypeKey = "authType"

/**
 * JSON key for the username associated with a user key.
 */
const val userNameKey = "username"

/**
 * JSON key for the creation timestamp of a user key.
 */
const val createdAtKey = "createdAt"

/**
 * Repository for accessing device binding data stored by the Legacy SDK.
 *
 * This class provides a bridge to read user key metadata that was stored by the Legacy SDK
 * in encrypted SharedPreferences. It's used during the migration process to retrieve all
 * user keys and migrate them to the new storage format.
 *
 * The Legacy SDK stored [UserKey] objects as JSON strings in encrypted SharedPreferences
 * with the following structure:
 * ```json
 * {
 *   "id": "unique-id",
 *   "userId": "user-identifier",
 *   "username": "user-name",
 *   "kid": "key-identifier",
 *   "authType": "BIOMETRIC_ONLY",
 *   "createdAt": 1234567890
 * }
 * ```
 *
 * ## Usage in Migration
 *
 * This class is primarily used in [step2] of the device binding migration to:
 * 1. Check if legacy user key data exists
 * 2. Retrieve all user keys from the legacy repository
 * 3. Migrate the keys to the new storage format
 * 4. Clean up the legacy data after successful migration
 *
 * @property context The Android context used to access SharedPreferences and the file system.
 *
 * @see UserKey
 * @see step3
 * @see LegacyEncryptedSharedPreferences
 */
internal class LegacyLocalDeviceBindingRepository(context: Context) {

    /**
     * Lazy-initialized SharedPreferences instance for accessing legacy device binding data.
     *
     * This property provides access to the encrypted SharedPreferences file used by the
     * Legacy SDK to store user key metadata. It uses [LegacyEncryptedSharedPreferences]
     * to ensure compatibility with the Legacy SDK's encryption scheme.
     */
    private val sharedPreferences: SharedPreferences by lazy {
        LegacyEncryptedSharedPreferences.getInstance(
            context,
            ORG_FORGEROCK_V_1_DEVICE_REPO
        )
    }

    /**
     * Retrieves all user keys stored in the legacy SharedPreferences repository.
     *
     * This suspending function reads all entries from the legacy encrypted SharedPreferences,
     * parses each JSON string into a [UserKey] object, and returns them as a list. The operation
     * is performed on the IO dispatcher to avoid blocking the main thread.
     *
     * The method performs the following steps:
     * 1. Reads all entries from the encrypted SharedPreferences
     * 2. Parses each entry's JSON string value
     * 3. Extracts the user key properties (id, userId, username, kid, authType, createdAt)
     * 4. Constructs [UserKey] objects from the parsed data
     * 5. Returns a list of all successfully parsed user keys
     *
     * ## JSON Structure
     *
     * Each entry in the SharedPreferences is expected to have a JSON string value with the format:
     * ```json
     * {
     *   "id": "unique-id",
     *   "userId": "user-identifier",
     *   "username": "user-name",
     *   "kid": "key-identifier",
     *   "authType": "BIOMETRIC_ONLY",
     *   "createdAt": 1234567890
     * }
     * ```
     *
     * @return A list of [UserKey] objects parsed from the legacy repository.
     *         Returns an empty list if no keys are found or if all entries fail to parse.
     *
     * @see UserKey
     * @see DeviceBindingAuthenticationType
     *
     */
    suspend fun getAllKeys(): List<UserKey> = withContext(Dispatchers.IO) {
        sharedPreferences.all?.mapNotNull {
            val json = Json.parseToJsonElement(it.value as String).jsonObject
            UserKey(
                json[idKey]?.jsonPrimitive?.content ?: "",
                json[userIdKey]?.jsonPrimitive?.content ?: "",
                json[userNameKey]?.jsonPrimitive?.content ?: "",
                json[kidKey]?.jsonPrimitive?.content ?: "",
                DeviceBindingAuthenticationType.valueOf(
                    json[authTypeKey]?.jsonPrimitive?.content ?: "NONE"
                ),
                json[createdAtKey]?.jsonPrimitive?.long ?: 0L
            )
        }?.toMutableList() ?: mutableListOf()
    }

    /**
     * Checks if the legacy SharedPreferences file exists on the filesystem.
     *
     * This suspending function verifies whether the legacy device binding repository file
     * exists in the app's shared_prefs directory. It's used to determine whether migration
     * is necessary before attempting to read or migrate data.
     *
     * The method checks for the existence of a file with the pattern:
     * `{dataDir}/shared_prefs/{ORG_FORGEROCK_V_1_DEVICE_REPO}.xml`
     *
     * This is useful for:
     * - Determining if the Legacy SDK was ever used in this app installation
     * - Skipping migration if no legacy data exists
     * - Avoiding unnecessary error handling when legacy data is not present
     *
     * @param context The Android context used to access the application data directory.
     * @return `true` if the legacy SharedPreferences file exists, `false` otherwise.
     *         Also returns `false` if any exception occurs during the check.
     */
    suspend fun exists(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            val prefsFile = File(prefsDir, "$ORG_FORGEROCK_V_1_DEVICE_REPO.xml")
            prefsFile.exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes the legacy SharedPreferences file and associated AndroidKeyStore key alias.
     *
     * This suspending function performs a complete cleanup of legacy device binding data
     * after successful migration. It removes both the encrypted SharedPreferences file and
     * the master key used to encrypt it from the AndroidKeyStore.
     *
     * The cleanup operation consists of two steps:
     * 1. **Delete SharedPreferences file**: Removes the XML file containing the encrypted
     *    user key metadata from the shared_prefs directory.
     * 2. **Delete KeyStore entry**: Removes the master key with alias
     *    [ORG_FORGEROCK_V_1_DEVICE_REPO] from the AndroidKeyStore.
     *
     * This method should only be called after successfully migrating all user keys to
     * the new storage format. It ensures that legacy data is properly cleaned up and
     * won't interfere with future operations.
     *
     * @param context The Android context used to access SharedPreferences and the KeyStore.
     *
     * @see exists
     * @see getAllKeys
     */
    suspend fun delete(context: Context) = withContext(Dispatchers.IO) {
        // Clear all SharedPreferences file
        context.deleteSharedPreferences(ORG_FORGEROCK_V_1_DEVICE_REPO)

        // Delete the key alias from Android KeyStore
        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(ORG_FORGEROCK_V_1_DEVICE_REPO)
        } catch (e: Exception) {
            // Ignore if key doesn't exist or deletion fails
            BindingMigration.logger.w("Failed to delete key alias from Android KeyStore", e)
        }
    }

}
