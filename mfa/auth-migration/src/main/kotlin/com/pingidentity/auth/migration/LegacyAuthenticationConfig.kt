/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD

/**
 * Configuration for the legacy FR Authenticator data migration.
 *
 * Configure via the DSL block passed to [AuthMigration.start]. All properties have sensible
 * defaults for standard FR Authenticator installations — no block is required for most apps.
 *
 * @property legacyStorageProvider Defines how the migration pipeline sources legacy data and
 *   performs cleanup. If not set, [DefaultLegacyStorageProvider] is used, which reads directly
 *   from the legacy encrypted SharedPreferences. Override when your application stores
 *   authenticator data in a custom backend (e.g. a different
 *   [org.forgerock.android.auth.StorageClient], an encrypted database, or a remote store).
 * @property logger Logger instance to use for logging. Defaults to [Logger.Companion.STANDARD].
 * @property allowBackup Allows the backup of the SharedPreferences files. Defaults to `false`.
 * @property restore Allows the option to do a restore of the backup files. Defaults to `{}`.
 *
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
 * ## Example — logger
 *  ```kotlin
 * lifecycleScope.launch {
 *     AuthMigration.start(applicationContext) {
 *        logger = Logger.WARN
 *    }
 * }
 * ```
 *
 * ### Example - Allow backup
 * ```kotlin
 * lifecycleScope.launch {
 *     AuthMigration.start(applicationContext) {
 *         allowBackup = true
 *     }
 * }
 * ```
 *
 * @see AuthMigration
 * @see LegacyStorageProvider
 * @see DefaultLegacyStorageProvider
 */
class LegacyAuthenticationConfig {
    var legacyStorageProvider: LegacyStorageProvider? = null
    var logger: Logger = Logger.STANDARD
    var allowBackup: Boolean = false
    var restore: (context: Context) -> Unit = {}
}