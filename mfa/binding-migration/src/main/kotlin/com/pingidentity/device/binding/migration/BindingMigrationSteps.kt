/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.migration

import com.pingidentity.device.binding.CryptoKey
import com.pingidentity.device.binding.UserKey
import com.pingidentity.device.binding.UserKeysStorage
import com.pingidentity.device.binding.authenticator.AppPinConfig
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType
import com.pingidentity.migration.MigrationStep
import com.pingidentity.migration.MigrationStepResult
import com.pingidentity.migration.MigrationStepResult.CONTINUE


val step1 = MigrationStep(
    description = "Check for legacy encrypted file keystore"
) {
    logger.i("Checking for legacy encrypted file keystore")
    val repo = LegacyLocalDeviceBindingRepository(context)

    if (!repo.exists(context)) {
        // No legacy repo found, nothing to migrate
        logger.i("No legacy repository found, skipping migration")
        return@MigrationStep MigrationStepResult.ABORT
    }
    logger.i("Legacy repository exists, proceeding with migration")

    val keys = repo.getAllKeys()
    logger.i("Retrieved ${keys.size} keys from legacy repository")

    if (keys.isEmpty()) {
        // No keys found, nothing to migrate
        logger.i("No keys found in legacy repository, skipping migration")
        return@MigrationStep MigrationStepResult.ABORT
    }

    logger.i("Found ${keys.size} keys to migrate: ${keys.map { "${it.userId}(${it.authType})" }}")

    state["keys"] = keys
    state["repo"] = repo

    CONTINUE
}

val step2 = MigrationStep(
    description = "Migrate user keys to new user keys storage"
) {
    logger.i("Starting user keys migration to new storage")

    val keys: List<UserKey> = getValue("keys")
        ?: return@MigrationStep MigrationStepResult.ABORT
    logger.i("Retrieved ${keys.size} keys from migration state")

    val userKeysStorage = UserKeysStorage()
    logger.i("Created UserKeysStorage instance")

    userKeysStorage.saveAll(keys)
    logger.i("Successfully migrated ${keys.size} user keys to new storage")

    //Delete the file
    getValue<LegacyLocalDeviceBindingRepository>("repo")?.let {
        logger.i("Deleting legacy repository")
        it.delete(context)
        logger.i("Successfully deleted legacy repository")
    }

    CONTINUE
}

val step3 = MigrationStep(
    description = "Migrate App PIN keystore file to new encrypted keystore file"
) {
    logger.i("Starting App PIN keys migration to encrypted data store")

    val keys: List<UserKey> = getValue("keys")
        ?: return@MigrationStep MigrationStepResult.ABORT
    val appPinKeys = keys.filter { it.authType == DeviceBindingAuthenticationType.APPLICATION_PIN }

    logger.i("Found ${appPinKeys.size} APP_PIN keys out of ${keys.size} total keys")

    if (appPinKeys.isEmpty()) {
        logger.i("No APP_PIN keys found, skipping encrypted data store migration")
        return@MigrationStep CONTINUE
    }

    for (key in appPinKeys) {
        logger.i("Processing APP_PIN key for user: ${key.userId}")

        val keyAlias = CryptoKey(key.userId).keyAlias
        logger.i("Generated key alias: $keyAlias")

        val repo = LegacyEncryptedFileKeyStore(keyAlias)
        logger.i("Created LegacyEncryptedFileKeyStore for alias: $keyAlias")

        try {
            // Read input stream into byte array
            val inputStream = repo.getInputStream(context)

            val byteArray = inputStream.readBytes()
            logger.i("Read ${byteArray.size} bytes from input stream")

            val appPinConfig = AppPinConfig().apply {
                storage {
                    this.fileName = "$fileName.$keyAlias"
                }
            }

            appPinConfig.storage().save(byteArray)
            logger.i("Successfully migrated ${byteArray.size} bytes for user ${key.userId} to new encrypted storage")

            repo.delete(context)
            logger.i("Deleted legacy encrypted file for user: ${key.userId}")

        } catch (e: Exception) {
            logger.e("Failed to migrate APP_PIN key for user ${key.userId}: ${e.message}", e)
            // Continue with other keys even if one fails
        }
    }

    logger.i("Completed APP_PIN keys migration for ${appPinKeys.size} keys")
    CONTINUE
}

