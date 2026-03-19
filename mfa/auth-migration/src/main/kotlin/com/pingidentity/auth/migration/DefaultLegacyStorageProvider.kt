/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context

/**
 * Defines how the migration pipeline sources and cleans up legacy authenticator data.
 *
 * Implement this interface when your application stores authenticator data in a custom
 * backend (e.g., a different [org.forgerock.android.auth.StorageClient] implementation,
 * an encrypted database, or a remote store) so that [AuthMigration] can migrate it to
 * the new OATH and Push credential storage.
 *
 * The default implementation, [DefaultLegacyStorageProvider], reads from the legacy
 * ForgeRock encrypted SharedPreferences and requires no additional configuration for
 * standard FR Authenticator installations.
 *
 * ## Implementing a custom configuration
 * ```kotlin
 * class MyCustomStorageConfiguration(
 *     private val context: Context,
 *     private val storageClient: StorageClient
 * ) : CustomStorageConfiguration {
 *
 *     override suspend fun isMigrationRequired(context: Context): Boolean =
 *         !storageClient.isEmpty
 *
 *     override suspend fun getMigrationData(context: Context): LegacyExportedData =
 *         withContext(Dispatchers.IO) {
 *             LegacyDataConverter.convertToLegacyExportedData(storageClient)
 *         }
 *
 *     override suspend fun cleanUp(context: Context, allowBackup: Boolean) {
 *         if (allowBackup) {
 *             // perform your own backup logic here
 *         }
 *         storageClient.clear()
 *     }
 * }
 * ```
 *
 * @see DefaultLegacyStorageProvider
 * @see LegacyDataConverter
 */
interface LegacyStorageProvider {

    /**
     * Returns `true` if legacy authenticator data exists and migration should proceed.
     *
     * This is called as the first check in the migration pipeline. Return `false` to
     * skip migration entirely (e.g. on a fresh install or after a previous successful migration).
     *
     * @param context The Android application context.
     * @return `true` if migration is needed, `false` to abort the migration early.
     */
    suspend fun isMigrationRequired(context: Context): Boolean

    /**
     * Returns all legacy authenticator data as a [LegacyExportedData] object.
     *
     * This method is called after [isMigrationRequired] returns `true`. The returned
     * object is fed directly into the migration pipeline where OATH and Push mechanisms
     * are extracted and stored in the new credential storage.
     *
     * Use [LegacyDataConverter.convertToLegacyExportedData] if your backend implements
     * [org.forgerock.android.auth.StorageClient], or construct [LegacyExportedData]
     * manually for fully custom backends.
     *
     * @param context The Android application context.
     * @return [LegacyExportedData] containing all mechanisms and their associated account data.
     * @throws Exception if the data cannot be read or deserialised.
     */
    suspend fun getMigrationData(context: Context): LegacyExportedData

    /**
     * Removes legacy authenticator data after a successful migration.
     *
     * Called as the final step of the migration pipeline. Implement this to delete
     * SharedPreferences files, database rows, remote records, or any other storage
     * your backend uses.
     *
     * @param context The Android application context.
     * @param allowBackup Allows the backup of the SharedPreferences files. Default is `false`. For
     * custom implementations of the [org.forgerock.android.auth.StorageClient], the
     * [LegacyAuthenticationConfig.restore] can be used to restore the data.
     */
    suspend fun cleanUp(context: Context, allowBackup: Boolean = false)
}

/**
 * Default implementation of [LegacyStorageProvider] for standard FR Authenticator installations.
 *
 * Reads from the legacy ForgeRock encrypted SharedPreferences
 * (`org.forgerock.android.authenticator.DATA.ACCOUNT` and
 * `org.forgerock.android.authenticator.DATA.MECHANISM`), decrypts the data using the
 * AndroidKeyStore key identified by [DEFAULT_KEY_ALIAS], and always
 * creates a backup before deleting the original files.
 *
 * This implementation is used automatically when no custom configuration is supplied to
 * [LegacyAuthenticationConfig].
 */
class DefaultLegacyStorageProvider(
    context: Context,
) : LegacyStorageProvider {

    private val legacyAuthenticationRepository = LegacyAuthenticationRepository(context)

    /**
     * Returns `true` if legacy ForgeRock SharedPreferences files (encrypted or plain) exist.
     */
    override suspend fun isMigrationRequired(context: Context): Boolean {
        return legacyAuthenticationRepository.isExists()
    }

    /**
     * Decrypts and returns all legacy OATH and Push mechanism data from SharedPreferences.
     */
    override suspend fun getMigrationData(context: Context): LegacyExportedData {
        return legacyAuthenticationRepository.exportAllData()
    }

    /**
     * Backs up and then permanently deletes the legacy SharedPreferences files.
     * @param context The Android application context.
     * @param allowBackup Allows the backup of the SharedPreferences files.
     */
    override suspend fun cleanUp(context: Context, allowBackup: Boolean) {
        if (allowBackup) {
            // We will back up the preferences in default mode.
            legacyAuthenticationRepository.backupSharedPreferences()
        }
        legacyAuthenticationRepository.deleteLegacyData(allowBackup)
    }
}
