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
/** Push storage instance shared across migration steps. */
private lateinit var pushStorage: SQLPushStorage
/** OATH storage instance shared across migration steps. */
private lateinit var oathStorage: SQLOathStorage

/**
 * Creates a migration step that imports data using the provided [LegacyStorageProvider].
 */
fun startMigrationStep(provider: LegacyStorageProvider) = MigrationStep(
    description = "Import legacy data"
) {
    logger.i("Importing legacy data")

    try {
        if (!provider.isMigrationRequired(context)) {
            logger.i("Migration is not required as defined in the provider")
            return@MigrationStep ABORT
        }

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
    oathStorage = SQLOathStorage {
        context = this@MigrationStep.context
        allowDestructiveRecovery = true
        backupOnError = true
        autoRestoreFromBackup = true
    }
    oathStorage.initializeDatabase()
    pushStorage = SQLPushStorage {
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
        oathStorage.closeDatabase()
        logger.i("Successfully closed oath storage database")
        pushStorage.closeDatabase()
        logger.i("Successfully closed push storage database")
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

    // Check OATH database
    val oathDbPath = context.getDatabasePath("pingidentity_oath.db")
    if (oathDbPath.exists()) {
        logger.i("Found existing OATH database at: ${oathDbPath.absolutePath}")

        val shouldDeleteOath = try {
            // Try to open the database with current passphrase to check if it's valid
            val testOathStorage = SQLOathStorage {
                this.context = context
                allowDestructiveRecovery = false // Don't allow auto-recovery during check
            }

            testOathStorage.initializeDatabase()
            val existingCredentials = testOathStorage.getAllOathCredentials()
            testOathStorage.closeDatabase()

            if (existingCredentials.isNotEmpty()) {
                logger.i("OATH database contains ${existingCredentials.size} existing credentials - preserving database")
                false // Keep database with existing data
            } else {
                logger.i("OATH database is empty - deleting to avoid passphrase conflicts")
                true // Delete empty database to avoid conflicts
            }
        } catch (e: Exception) {
            // Database exists but cannot be opened with current passphrase
            logger.w("OATH database exists but cannot be opened (likely encrypted with different passphrase): ${e.message}")
            logger.i("Will delete incompatible OATH database to allow migration to proceed")
            true // Delete incompatible database
        }

        if (shouldDeleteOath) {
            try {
                context.deleteDatabase("pingidentity_oath.db")
                logger.i("Deleted incompatible OATH database")
            } catch (e: Exception) {
                logger.e("Failed to delete OATH database: ${e.message}", e)
            }
        }
    } else {
        logger.i("No existing OATH database found - will create fresh database")
    }

    // Check Push database
    val pushDbPath = context.getDatabasePath("pingidentity_push.db")
    if (pushDbPath.exists()) {
        logger.i("Found existing Push database at: ${pushDbPath.absolutePath}")

        val shouldDeletePush = try {
            // Try to open the database with current passphrase to check if it's valid
            val testPushStorage = SQLPushStorage {
                this.context = context
                allowDestructiveRecovery = false // Don't allow auto-recovery during check
            }

            testPushStorage.initializeDatabase()
            val existingCredentials = testPushStorage.getAllPushCredentials()
            val existingNotifications = testPushStorage.getAllPushNotifications()
            val existingTokens = testPushStorage.getAllPushDeviceTokens()
            testPushStorage.closeDatabase()

            val totalItems = existingCredentials.size + existingNotifications.size + existingTokens.size

            if (totalItems > 0) {
                logger.i("Push database contains existing data (${existingCredentials.size} credentials, ${existingNotifications.size} notifications, ${existingTokens.size} tokens) - preserving database")
                false // Keep database with existing data
            } else {
                logger.i("Push database is empty - deleting to avoid passphrase conflicts")
                true // Delete empty database to avoid conflicts
            }
        } catch (e: Exception) {
            // Database exists but cannot be opened with current passphrase
            logger.w("Push database exists but cannot be opened (likely encrypted with different passphrase): ${e.message}")
            logger.i("Will delete incompatible Push database to allow migration to proceed")
            true // Delete incompatible database
        }

        if (shouldDeletePush) {
            try {
                context.deleteDatabase("pingidentity_push.db")
                logger.i("Deleted incompatible Push database")
            } catch (e: Exception) {
                logger.e("Failed to delete Push database: ${e.message}", e)
            }
        }
    } else {
        logger.i("No existing Push database found - will create fresh database")
    }
}