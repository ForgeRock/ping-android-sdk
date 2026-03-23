/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.oath.OathAlgorithm
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.oath.OathType
import com.pingidentity.mfa.oath.storage.SQLOathStorage
import com.pingidentity.mfa.push.PushCredential
import com.pingidentity.mfa.push.storage.SQLPushStorage
import com.pingidentity.migration.MigrationStep
import com.pingidentity.migration.MigrationStepResult.CONTINUE
import com.pingidentity.migration.MigrationStepResult.ABORT
import java.util.Date

private const val OTP_AUTH = "otpauth"
private const val TOTP = "totp"
private const val HOTP = "hotp"
private const val PUSH = "push"
private const val PUSH_AUTH = "pushauth"
private const val MECHANISMS = "mechanisms"
private const val OATH_STORAGE = "oathStorage"
private const val PUSH_STORAGE = "pushStorage"

/**
 * Creates the first migration step, which imports legacy authenticator data through the
 * provided [LegacyStorageProvider].
 *
 * **Execution order within this step:**
 * 1. Calls [LegacyStorageProvider.isMigrationRequired] — returns [ABORT] immediately if `false`
 *    (clean install or already migrated).
 * 2. Invokes [restore] with the current [android.content.Context] so that backup data can be
 *    reinstated before reading from storage.
 * 3. Calls [LegacyStorageProvider.getMigrationData] to load [LegacyExportedData].
 * 4. Returns [ABORT] if the mechanisms list is empty; otherwise stores the list in the
 *    migration state and returns [CONTINUE].
 *
 * Any exception thrown by [LegacyStorageProvider.getMigrationData] is wrapped in an
 * [IllegalArgumentException] and re-thrown, which causes the migration framework to emit a
 * [com.pingidentity.migration.MigrationProgress.Error] event and stop the pipeline.
 *
 * @param provider The [LegacyStorageProvider] used to check whether migration is needed
 *   and to retrieve the legacy data.
 * @param restore A callback invoked **after** [LegacyStorageProvider.isMigrationRequired]
 *   returns `true` and **before** [LegacyStorageProvider.getMigrationData] is called.
 *   Use this to restore a backup of the legacy data so the provider can find it.
 *   Defaults to a no-op in [LegacyAuthenticationConfig].
 *
 * @see LegacyStorageProvider
 * @see LegacyAuthenticationConfig.restore
 */
fun startMigrationStep(
    provider: LegacyStorageProvider,
    restore: (context: Context) -> Unit,
) = MigrationStep(
    description = "Import legacy data"
) {
    logger.i("Importing legacy data")

    try {
        if (!provider.isMigrationRequired(context)) {
            logger.i("Migration is not required as defined in the provider")
            return@MigrationStep ABORT
        }

        logger.i("Restoring backup files")
        restore(context)

        val exportedData = provider.getMigrationData(context)
        logger.i("Successfully loaded data with ${exportedData.mechanisms.size} mechanisms")

        val mechanisms = exportedData.mechanisms

        if (mechanisms.isEmpty()) {
            logger.i("No mechanisms found in data, skipping migration")
            return@MigrationStep ABORT
        }

        state[MECHANISMS] = mechanisms
        CONTINUE
    } catch (e: Exception) {
        logger.e("Failed to load legacy exported data: ${e.message}", e)
        throw IllegalArgumentException("Failed to load legacy exported data", e)
    }
}

/**
 * The second migration step, which converts legacy [LegacyMechanism] entries (loaded by
 * [startMigrationStep]) into modern OATH and Push credentials and persists them to SQLite.
 *
 * **Execution order within this step:**
 * 1. Retrieves the mechanisms list from the migration state; returns [CONTINUE] immediately
 *    if the list is absent (defensive guard).
 * 2. Calls `prepareDatabase` to inspect any pre-existing OATH (`pingidentity_oath.db`) and
 *    Push (`pingidentity_push.db`) SQLite databases:
 *    - Databases with existing credentials are **preserved**.
 *    - Empty databases are **deleted** to prevent passphrase conflicts.
 *    - Databases that cannot be opened (e.g., encrypted with a different passphrase) are
 *      **deleted** so migration can create fresh ones.
 * 3. Initialises [com.pingidentity.mfa.oath.storage.SQLOathStorage] and
 *    [com.pingidentity.mfa.push.storage.SQLPushStorage] with `allowDestructiveRecovery = true`,
 *    `backupOnError = true`, and `autoRestoreFromBackup = true` for resilience against
 *    force-closes during migration.
 * 4. Iterates each mechanism and routes it by [LegacyMechanism.type]:
 *    - `"otpauth"`, `"totp"`, `"hotp"` → [com.pingidentity.mfa.oath.OathCredential] stored via
 *      [com.pingidentity.mfa.oath.storage.SQLOathStorage.storeOathCredential].
 *    - `"push"`, `"pushauth"` → [com.pingidentity.mfa.push.PushCredential] stored via
 *      [com.pingidentity.mfa.push.storage.SQLPushStorage.storePushCredential]. Query parameters
 *      are stripped from `authenticationEndpoint` to produce the `serverEndpoint`.
 *    - Unknown types are logged as warnings and skipped.
 *    - Per-mechanism exceptions are caught and logged; remaining mechanisms continue.
 * 5. Stores the storage instances in the migration state for [cleanupLegacyDataStep].
 *
 * @see startMigrationStep
 * @see cleanupLegacyDataStep
 */
val migrateMechanismsStep = MigrationStep(
    description = "Migrate mechanisms to OATH and Push storage"
) {
    logger.i("Starting mechanisms migration to new storage")

    val mechanisms = getValue<List<LegacyMechanism>>(MECHANISMS) ?: run {
        logger.w("No mechanisms found in state, skipping")
        return@MigrationStep CONTINUE
    }

    // Safely check and prepare database environment before migration
    prepareDatabase(
        context = this@MigrationStep.context,
        logger = logger,
    )

    // Create storage instances with destructive recovery enabled for migration
    // This allows recovery from corrupted databases that may occur if app is force-closed during migration
    val oathStorage = SQLOathStorage {
        context = this@MigrationStep.context
        allowDestructiveRecovery = true
        backupOnError = true
        autoRestoreFromBackup = true
    }
    oathStorage.initializeDatabase()
    val pushStorage = SQLPushStorage {
        context = this@MigrationStep.context
        allowDestructiveRecovery = true
        backupOnError = true
        autoRestoreFromBackup = true
    }
    pushStorage.initializeDatabase()


    var oathCount = 0
    var pushCount = 0

    mechanisms.forEach { mechanism ->
        try {
            when (mechanism.type) {
                OTP_AUTH, TOTP, HOTP -> {
                    // Migrate OATH/TOTP mechanism
                    val account = mechanism.account

                    val oathCredential = OathCredential(
                        userId = mechanism.uid,
                        resourceId = mechanism.resourceId,
                        issuer = account.issuer,
                        displayIssuer = account.displayIssuer ?: account.issuer,
                        accountName = account.accountName,
                        displayAccountName = account.displayAccountName ?: account.accountName,
                        oathType = OathType.fromString(mechanism.oathType ?: TOTP),
                        secret = mechanism.secret,
                        oathAlgorithm = when (mechanism.algorithm) {
                            "sha256", "SHA256" -> OathAlgorithm.SHA256
                            "sha512", "SHA512" -> OathAlgorithm.SHA512
                            else -> OathAlgorithm.SHA1
                        },
                        digits = mechanism.digits ?: 6,
                        period = mechanism.period ?: 30,
                        counter = mechanism.counter ?: 0L,
                        createdAt = account.timeAdded?.let { Date(it) }
                            ?: mechanism.timeAdded?.let { Date(it) }
                            ?: Date(),
                        imageURL = account.imageURL,
                        backgroundColor = account.backgroundColor,
                        policies = account.policies,
                        lockingPolicy = account.lockingPolicy,
                        isLocked = account.lock
                    )

                    oathStorage.storeOathCredential(oathCredential)
                    oathCount++
                    logger.d("Migrated OATH credential: ${oathCredential.issuer} - ${oathCredential.accountName}")
                }

                PUSH, PUSH_AUTH -> {
                    // Migrate Push mechanism
                    val account = mechanism.account

                    // Extract base endpoint (remove query parameters like ?_action=authenticate)
                    val rawEndpoint = mechanism.authenticationEndpoint
                        ?: mechanism.registrationEndpoint
                        ?: ""
                    val serverEndpoint = if (rawEndpoint.contains("?")) {
                        rawEndpoint.substringBefore("?")
                    } else {
                        rawEndpoint
                    }

                    val pushCredential = PushCredential(
                        id = mechanism.mechanismUID,
                        userId = mechanism.uid,
                        resourceId = mechanism.resourceId ?: "",
                        issuer = account.issuer,
                        displayIssuer = account.displayIssuer ?: account.issuer,
                        accountName = account.accountName,
                        displayAccountName = account.displayAccountName ?: account.accountName,
                        serverEndpoint = serverEndpoint,
                        sharedSecret = mechanism.secret,
                        createdAt = account.timeAdded?.let { Date(it) }
                            ?: mechanism.timeAdded?.let { Date(it) }
                            ?: Date(),
                        imageURL = account.imageURL,
                        backgroundColor = account.backgroundColor,
                        policies = account.policies,
                        lockingPolicy = account.lockingPolicy,
                        isLocked = account.lock,
                        platform = mechanism.platform ?: "PING_AM"
                    )

                    pushStorage.storePushCredential(pushCredential)
                    pushCount++
                    logger.d("Migrated Push credential: ${pushCredential.issuer} - ${pushCredential.accountName}")
                }

                else -> {
                    logger.w("Unknown mechanism type: ${mechanism.type}")
                }
            }
        } catch (e: Exception) {
            logger.e("Failed to migrate mechanism ${mechanism.id}", e)
        }
    }

    logger.i("Migrated $oathCount OATH credentials and $pushCount Push credentials")

    state[OATH_STORAGE] = oathStorage
    state[PUSH_STORAGE] = pushStorage

    CONTINUE
}

/**
 * Creates the third and final migration step, which removes legacy authenticator data via the
 * provided [LegacyStorageProvider] and closes the SQLite storage connections.
 *
 * **Execution order within this step:**
 * 1. Calls [LegacyStorageProvider.cleanUp] passing the [backup] callback.
 *    [StorageClientProvider] always invokes the callback first, then iterates and removes
 *    all accounts and mechanisms via the [org.forgerock.android.auth.StorageClient] API.
 *    Pass a no-op callback (the default) to skip backup.
 * 2. Retrieves `SQLOathStorage` from the migration state and calls `closeDatabase()`.
 * 3. Retrieves `SQLPushStorage` from the migration state and calls `closeDatabase()`.
 *
 * Any exception during cleanup is caught and logged — cleanup failures are **non-critical**
 * and do not abort the migration or prevent the pipeline from emitting a success event.
 *
 * @param provider The same [LegacyStorageProvider] passed to [startMigrationStep], used here
 *   to perform the actual cleanup of the legacy storage backend.
 * @param backup Forwarded from [LegacyAuthenticationConfig.backup]. Passed directly to
 *   [LegacyStorageProvider.cleanUp] so the provider can invoke it before clearing data.
 *   Defaults to a no-op, which skips backup.
 *
 * @see startMigrationStep
 * @see migrateMechanismsStep
 * @see LegacyStorageProvider.cleanUp
 */
fun cleanupLegacyDataStep(provider: LegacyStorageProvider, backup: (context: Context) -> Unit = {}) = MigrationStep(
    description = "Cleanup legacy authenticator data"
) {
    logger.i("Cleaning up legacy authenticator data")

    try {
        provider.cleanUp(context, backup)
        logger.i("Successfully cleaned up legacy authenticator data")
        val oathStorage = getValue<SQLOathStorage>(OATH_STORAGE)
        if (oathStorage != null) {
            oathStorage.closeDatabase()
            logger.i("Successfully closed oath storage database")
        }
        val pushStorage = getValue<SQLPushStorage>(PUSH_STORAGE)
        if (pushStorage != null) {
            pushStorage.closeDatabase()
            logger.i("Successfully closed push storage database")
        }
    } catch (e: Exception) {
        logger.e("Failed to cleanup legacy data", e)
        // Don't fail the migration if cleanup fails
    }

    CONTINUE
}

/**
 * Prepares the database environment for migration by safely handling any existing database files.
 *
 * @param context The Android context used to access database paths and storage APIs.
 * @param logger The logger instance for tracking database operations and decisions.
 *
 * @see SQLOathStorage
 * @see SQLPushStorage
 */
private suspend fun prepareDatabase(
    context: Context,
    logger: Logger,
) {
    logger.i("Checking existing databases before migration")
    prepareDatabaseFile(context, logger, "pingidentity_oath.db", "OATH") { dbContext ->
        val storage = SQLOathStorage {
            this.context = dbContext
            allowDestructiveRecovery = false
        }
        storage.initializeDatabase()
        val count = storage.getAllOathCredentials().size
        storage.closeDatabase()
        count
    }
    prepareDatabaseFile(context, logger, "pingidentity_push.db", "Push") { dbContext ->
        val storage = SQLPushStorage {
            this.context = dbContext
            allowDestructiveRecovery = false
        }
        storage.initializeDatabase()
        val count = storage.getAllPushCredentials().size +
                storage.getAllPushNotifications().size +
                storage.getAllPushDeviceTokens().size
        storage.closeDatabase()
        count
    }
}

/**
 * Prepares a specific database file for migration by checking if it exists, reading its contents,
 * and deleting it if it is empty or incompatible (e.g., encrypted with a different passphrase).
 *
 * @param context The Android context used to access database paths and storage APIs.
 * @param logger The logger instance for tracking database operations and decisions.
 * @param dbName The name of the database file to prepare.
 * @param label A human-readable label for the database type (e.g., "OATH" or "Push"), used in log messages.
 * @param countItems A suspending function that opens the database and returns the number of existing items.
 *                   If this throws an exception, the database is considered incompatible and will be deleted.
 */
private suspend fun prepareDatabaseFile(
    context: Context,
    logger: Logger,
    dbName: String,
    label: String,
    countItems: suspend (Context) -> Int,
) {
    val dbPath = context.getDatabasePath(dbName)
    if (!dbPath.exists()) {
        logger.i("No existing $label database found - will create fresh database")
        return
    }

    logger.i("Found existing $label database at: ${dbPath.absolutePath}")

    val shouldDelete = try {
        val count = countItems(context)
        if (count > 0) {
            logger.i("$label database contains $count existing item(s) - preserving database")
            false
        } else {
            logger.i("$label database is empty - deleting to avoid passphrase conflicts")
            true
        }
    } catch (e: Exception) {
        logger.w("$label database exists but cannot be opened (likely encrypted with different passphrase): ${e.message}")
        logger.i("Will delete incompatible $label database to allow migration to proceed")
        true
    }

    if (shouldDelete) {
        try {
            context.deleteDatabase(dbName)
            logger.i("Deleted incompatible $label database")
        } catch (e: Exception) {
            logger.e("Failed to delete $label database: ${e.message}", e)
        }
    }
}
