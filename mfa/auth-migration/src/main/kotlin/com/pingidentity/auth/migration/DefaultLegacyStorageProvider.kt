/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import org.forgerock.android.auth.DefaultStorageClient
import org.forgerock.android.auth.StorageClient

/**
 * Defines how the migration pipeline sources and cleans up legacy authenticator data.
 *
 * Implement this interface when your application stores authenticator data in a custom
 * backend (e.g., a different [org.forgerock.android.auth.StorageClient] implementation,
 * an encrypted database, or a remote store) so that [AuthMigration] can migrate it to
 * the new OATH and Push credential storage.
 *
 * The default implementation, [StorageClientProvider], wraps a ForgeRock [StorageClient]
 * and requires no additional configuration for standard FR Authenticator installations.
 *
 * ## Implementing a custom provider
 * ```kotlin
 * class MyCustomStorageProvider(
 *     private val context: Context,
 *     private val storageClient: StorageClient
 * ) : LegacyStorageProvider {
 *
 *     override suspend fun isMigrationRequired(context: Context): Boolean =
 *         storageClient.allAccounts.isNotEmpty()
 *
 *     override suspend fun getMigrationData(context: Context): LegacyExportedData =
 *         withContext(Dispatchers.IO) {
 *             LegacyDataConverter.convertToLegacyExportedData(storageClient)
 *         }
 *
 *     override suspend fun cleanUp(context: Context, backup: (context: Context) -> Unit) {
 *         backup(context) // invoke before clearing to allow caller to persist data
 *         storageClient.allAccounts.forEach { account ->
 *             storageClient.getMechanismsForAccount(account).forEach { storageClient.removeMechanism(it) }
 *             storageClient.removeAccount(account)
 *         }
 *     }
 * }
 * ```
 *
 * @see StorageClientProvider
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
     * SharedPreferences entries, database rows, remote records, or any other storage your
     * backend uses.
     *
     * The [backup] callback is forwarded from [LegacyAuthenticationConfig.backup]. Invoke it
     * before clearing the storage to persist a copy of the data. Pass a no-op (the default)
     * to skip backup. For [StorageClientProvider], this callback is invoked unconditionally
     * so the caller fully controls whether backup happens.
     *
     * @param context The Android application context.
     * @param backup A callback to invoke before clearing data. Defaults to a no-op.
     */
    suspend fun cleanUp(context: Context, backup: (context: Context) -> Unit = {})
}

/**
 * [LegacyStorageProvider] implementation that sources and cleans up legacy authenticator data
 * via a ForgeRock [StorageClient].
 *
 * This is the default provider used by [AuthMigration] when no custom [LegacyStorageProvider]
 * is configured in [LegacyAuthenticationConfig]. It delegates data reading to
 * [LegacyDataConverter.convertToLegacyExportedData] and removes accounts and mechanisms
 * directly through the [StorageClient] API during cleanup.
 *
 * @param context The Android application context, used to initialize the default
 *   [org.forgerock.android.auth.DefaultStorageClient] when no custom [storageClient] is provided.
 * @param storageClient The ForgeRock [StorageClient] that holds legacy accounts and mechanisms.
 *   Defaults to `DefaultStorageClient(context)`, which reads from the standard legacy encrypted
 *   SharedPreferences used by the FR Authenticator SDK.
 *
 * ## Example — default usage (no configuration needed)
 * ```kotlin
 * lifecycleScope.launch {
 *     AuthMigration.start(applicationContext)
 * }
 * ```
 *
 * ## Example — custom StorageClient
 * ```kotlin
 * lifecycleScope.launch {
 *     AuthMigration.start(applicationContext) {
 *         legacyStorageProvider = StorageClientProvider(
 *             context = applicationContext,
 *             storageClient = MyCustomStorageClient(applicationContext)
 *         )
 *     }
 * }
 * ```
 *
 * ## Example — backup before cleanup (via LegacyAuthenticationConfig)
 * ```kotlin
 * lifecycleScope.launch {
 *     AuthMigration.start(applicationContext) {
 *         backup = { ctx -> MyBackupHelper.backup(ctx) }
 *     }
 * }
 * ```
 *
 * @see LegacyStorageProvider
 * @see LegacyDataConverter
 * @see LegacyAuthenticationConfig
 * @see DefaultStorageClient
 */
class StorageClientProvider(
    context: Context,
    private val storageClient: StorageClient = DefaultStorageClient(context),
) : LegacyStorageProvider {

    /**
     * Returns `true` if the [storageClient] contains at least one legacy account,
     * indicating that migration is required.
     *
     * @param context The Android application context.
     * @return `true` if legacy accounts exist, `false` to skip migration.
     */
    override suspend fun isMigrationRequired(context: Context): Boolean {
        return storageClient.allAccounts.isNotEmpty()
    }

    /**
     * Converts all accounts and mechanisms held by the [storageClient] into a
     * [LegacyExportedData] object ready for the migration pipeline.
     *
     * Delegates to [LegacyDataConverter.convertToLegacyExportedData], which serialises
     * each account and mechanism using the ForgeRock SDK's own `toJson()` methods so
     * that all mechanism types (OATH and Push) are handled automatically.
     *
     * @param context The Android application context.
     * @return [LegacyExportedData] containing all legacy mechanisms with nested account data.
     */
    override suspend fun getMigrationData(context: Context): LegacyExportedData {
        return LegacyDataConverter.convertToLegacyExportedData(storageClient)
    }

    /**
     * Invokes [backup] and then removes all legacy accounts and mechanisms from the [storageClient].
     *
     * The [backup] callback is always called first — the caller controls whether it is a real
     * backup operation or a no-op. Cleanup then iterates every account, removes all of its
     * associated mechanisms, and finally removes the account itself.
     *
     * Cleanup failures do not propagate — the migration framework treats Step 3 as best-effort.
     *
     * @param context The Android application context.
     * @param backup Callback invoked before data is cleared. Receives the [context].
     *   Pass a no-op (the default in [LegacyAuthenticationConfig]) to skip backup.
     */
    override suspend fun cleanUp(context: Context, backup: (context: Context) -> Unit) {
        backup(context)
        storageClient.allAccounts.forEach { account ->
            storageClient.getMechanismsForAccount(account).forEach { mechanism ->
                storageClient.removeMechanism(mechanism)
            }
            storageClient.removeAccount(account)
        }
    }
}
