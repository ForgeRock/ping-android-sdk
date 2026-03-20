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
 * Creates a migration step that imports data using the provided [LegacyStorageProvider].
 * @param provider The [LegacyStorageProvider] to use for data import.
 * @param restore Allows the option to do a restore of the backup files.
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
 * Step 2: Migrate OATH and Push mechanisms to new storage.
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
 * Step 3: Cleanup legacy authenticator data via the provided [LegacyStorageProvider].
 * @param allowBackup Allows the backup of the SharedPreferences files.
 */
fun cleanupLegacyDataStep(provider: LegacyStorageProvider, allowBackup: Boolean) = MigrationStep(
    description = "Cleanup legacy authenticator data"
) {
    logger.i("Cleaning up legacy authenticator data")

    try {
        provider.cleanUp(context, allowBackup)
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
