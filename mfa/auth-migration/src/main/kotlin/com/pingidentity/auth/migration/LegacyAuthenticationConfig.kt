/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import org.forgerock.android.auth.StorageClient

/**
 * Configuration for the legacy FR Authenticator data migration.
 *
 * Configure via the DSL block passed to [AuthMigration.start]. All properties have sensible
 * defaults for standard FR Authenticator installations — no block is required for most apps.
 *
 * @property legacyStorageProvider Defines how the migration pipeline sources legacy data and
 *   performs cleanup. Defaults to [StorageClientProvider], which reads directly from the legacy
 *   ForgeRock [StorageClient]. Override when your application stores authenticator data in a
 *   custom backend (e.g. a different [org.forgerock.android.auth.StorageClient] implementation,
 *   an encrypted database, or a remote store).
 * @property logger Logger instance used throughout the migration pipeline.
 *   Defaults to `Logger.STANDARD`.
 * @property backup An optional callback invoked during [LegacyStorageProvider.cleanUp] to back
 *   up legacy data before it is cleared. The callback receives the Android [Context] and is
 *   invoked by [StorageClientProvider.cleanUp] unconditionally — pass a no-op (the default) to
 *   skip backup. For custom [LegacyStorageProvider] implementations, the callback is forwarded
 *   via [LegacyStorageProvider.cleanUp] and it is the implementation's responsibility to invoke it.
 * @property restore An optional callback invoked **after**
 *   [LegacyStorageProvider.isMigrationRequired] returns `true` and **before**
 *   [LegacyStorageProvider.getMigrationData] is called. Use this to reinstate backup data so the
 *   provider can find it. Defaults to a no-op.
 *
 * ## Example — default migration (no block needed)
 * ```kotlin
 * lifecycleScope.launch {
 *     AuthMigration.start(applicationContext)
 * }
 * ```
 *
 * ## Example — custom storage provider
 * ```kotlin
 * lifecycleScope.launch {
 *     AuthMigration.start(applicationContext) {
 *         legacyStorageProvider = MyCustomStorageProvider(applicationContext)
 *     }
 * }
 * ```
 *
 * ## Example — custom logger
 * ```kotlin
 * lifecycleScope.launch {
 *     AuthMigration.start(applicationContext) {
 *         logger = Logger.WARN
 *     }
 * }
 * ```
 *
 * ## Example — backup before cleanup
 * ```kotlin
 * lifecycleScope.launch {
 *     AuthMigration.start(applicationContext) {
 *         backup = { ctx -> MyBackupHelper.backup(ctx) }
 *     }
 * }
 * ```
 *
 * ## Example — restore before reading legacy data
 * ```kotlin
 * lifecycleScope.launch {
 *     AuthMigration.start(applicationContext) {
 *         backup  = { ctx -> MyBackupHelper.backup(ctx) }
 *         restore = { ctx -> MyBackupHelper.restore(ctx) }
 *     }
 * }
 * ```
 *
 * @see AuthMigration
 * @see LegacyStorageProvider
 * @see StorageClientProvider
 */
class LegacyAuthenticationConfig(context: Context) {
    var legacyStorageProvider: LegacyStorageProvider = StorageClientProvider(context)
    var logger: Logger = Logger.STANDARD
    val backup: (context: Context) -> Unit = {}
    var restore: (context: Context) -> Unit = {}
}