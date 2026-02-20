/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import com.pingidentity.mfa.oath.OathAlgorithm
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.oath.OathType
import com.pingidentity.mfa.oath.storage.SQLOathStorage
import com.pingidentity.mfa.push.PushCredential
import com.pingidentity.mfa.push.PushDeviceToken
import com.pingidentity.mfa.push.PushNotification
import com.pingidentity.mfa.push.PushType
import com.pingidentity.mfa.push.storage.SQLPushStorage
import com.pingidentity.migration.MigrationStep
import com.pingidentity.migration.MigrationStepResult.CONTINUE
import com.pingidentity.migration.MigrationStepResult.ABORT
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Date

private const val OTP_AUTH = "otpauth"
private const val TOTP = "totp"
private const val HOTP = "hotp"
private const val PUSH = "push"
private const val PUSH_AUTH = "pushauth"
private const val ACCOUNTS = "accounts"
private const val MECHANISMS = "mechanisms"
private const val NOTIFICATIONS = "notifications"
private const val DEVICE_TOKEN = "deviceToken"
/** Push storage instance shared across migration steps. */
private lateinit var pushStorage: SQLPushStorage
/** OATH storage instance shared across migration steps. */
private lateinit var oathStorage: SQLOathStorage
private val json = Json { ignoreUnknownKeys = true }


/**
 * Step 1: Export legacy authenticator data from Legacy SDK.
 */
val step1 = MigrationStep(
    description = "Export legacy authenticator data"
) {
    logger.i("Checking for legacy authenticator data")
    val repo = LegacyAuthenticationRepository(context)

    if (!repo.isExists()) {
        logger.i("No legacy repository found, skipping migration")
        return@MigrationStep ABORT
    }
    logger.i("Legacy repository exists, proceeding with migration")

    val exportedData = repo.exportAllData()
    logger.i("Successfully exported ${exportedData.accounts.size} accounts, ${exportedData.mechanisms.size} mechanisms, ${exportedData.notifications.size} notifications, ${exportedData.deviceToken.size} device tokens")

    val accounts = exportedData.accounts
    val mechanisms = exportedData.mechanisms
    val notifications = exportedData.notifications
    val deviceToken = exportedData.deviceToken

    if (accounts.isEmpty() && mechanisms.isEmpty() && notifications.isEmpty() && deviceToken.isEmpty()) {
        logger.i("No data found in legacy repository, skipping migration")
        return@MigrationStep ABORT
    }

    state[ACCOUNTS] = accounts
    state[MECHANISMS] = mechanisms
    state[NOTIFICATIONS] = notifications
    state[DEVICE_TOKEN] = deviceToken

    CONTINUE
}

/**
 * Step 2: Migrate OATH and Push mechanisms to new storage.
 */
val step2 = MigrationStep(
    description = "Migrate mechanisms to OATH and Push storage"
) {
    logger.i("Starting mechanisms migration to new storage")

    val mechanisms = getValue<Map<String, String>>(MECHANISMS) ?: run {
        logger.w("No mechanisms found in state, skipping")
        return@MigrationStep CONTINUE
    }

    val accounts = getValue<Map<String, String>>(ACCOUNTS) ?: emptyMap()

    // Create storage instances
    oathStorage = SQLOathStorage { context = this@MigrationStep.context }
    oathStorage.initializeDatabase()
    pushStorage = SQLPushStorage { context = this@MigrationStep.context }
    pushStorage.initializeDatabase()


    var oathCount = 0
    var pushCount = 0

    mechanisms.forEach { (mechanismId, mechanismJson) ->
        try {
            val mechanismData = json.parseToJsonElement(mechanismJson).jsonObject
            when (val type = mechanismData["type"]?.jsonPrimitive?.content) {
                OTP_AUTH, TOTP, HOTP -> {
                    // Migrate OATH/TOTP mechanism
                    val accountId = mechanismData["mechanismUID"]?.jsonPrimitive?.content
                        ?: mechanismData["id"]?.jsonPrimitive?.content
                        ?: mechanismId

                    // Find associated account
                    val accountJson = accounts[accountId]
                    val accountData = accountJson?.let { json.parseToJsonElement(it).jsonObject }

                    val issuer = accountData?.get("issuer")?.jsonPrimitive?.content
                        ?: mechanismData["issuer"]?.jsonPrimitive?.content
                        ?: "Unknown"
                    val accountName = accountData?.get("accountName")?.jsonPrimitive?.content
                        ?: mechanismData["accountName"]?.jsonPrimitive?.content
                        ?: "Unknown"

                    val oathCredential = OathCredential(
                        id = accountId,
                        issuer = issuer,
                        accountName = accountName,
                        oathType = when (mechanismData["oathType"]?.jsonPrimitive?.content?.uppercase()) {
                            HOTP -> OathType.HOTP
                            else -> OathType.TOTP
                        },
                        secret = mechanismData["secret"]?.jsonPrimitive?.content ?: "",
                        oathAlgorithm = when (mechanismData["algorithm"]?.jsonPrimitive?.content) {
                            "sha256", "SHA256" -> OathAlgorithm.SHA256
                            "sha512", "SHA512" -> OathAlgorithm.SHA512
                            else -> OathAlgorithm.SHA1
                        },
                        digits = mechanismData["digits"]?.jsonPrimitive?.content?.toIntOrNull() ?: 6,
                        period = mechanismData["period"]?.jsonPrimitive?.content?.toIntOrNull() ?: 30,
                        counter = mechanismData["counter"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        imageURL = accountData?.get("imageURL")?.jsonPrimitive?.content,
                        backgroundColor = accountData?.get("backgroundColor")?.jsonPrimitive?.content
                    )

                    oathStorage.storeOathCredential(oathCredential)
                    oathCount++
                    logger.d("Migrated OATH credential: $issuer - $accountName")
                }

                PUSH, PUSH_AUTH -> {
                    // Migrate Push mechanism
                    val accountId = mechanismData["mechanismUID"]?.jsonPrimitive?.content
                        ?: mechanismData["id"]?.jsonPrimitive?.content
                        ?: mechanismId

                    // Find associated account
                    val accountJson = accounts[accountId]
                    val accountData = accountJson?.let { json.parseToJsonElement(it).jsonObject }

                    val issuer = accountData?.get("issuer")?.jsonPrimitive?.content
                        ?: mechanismData["issuer"]?.jsonPrimitive?.content
                        ?: "Unknown"
                    val accountName = accountData?.get("accountName")?.jsonPrimitive?.content
                        ?: mechanismData["accountName"]?.jsonPrimitive?.content
                        ?: "Unknown"

                    val pushCredential = PushCredential(
                        id = accountId,
                        issuer = issuer,
                        accountName = accountName,
                        serverEndpoint = mechanismData["authenticationEndpoint"]?.jsonPrimitive?.content
                            ?: mechanismData["endpoint"]?.jsonPrimitive?.content
                            ?: "",
                        sharedSecret = mechanismData["secret"]?.jsonPrimitive?.content ?: "",
                        imageURL = accountData?.get("imageURL")?.jsonPrimitive?.content,
                        backgroundColor = accountData?.get("backgroundColor")?.jsonPrimitive?.content
                    )

                    pushStorage.storePushCredential(pushCredential)
                    pushCount++
                    logger.d("Migrated Push credential: $issuer - $accountName")
                }

                else -> {
                    logger.w("Unknown mechanism type: $type")
                }
            }
        } catch (e: Exception) {
            logger.e("Failed to migrate mechanism $mechanismId", e)
        }
    }

    logger.i("Migrated $oathCount OATH credentials and $pushCount Push credentials")

    CONTINUE
}

/**
 * Step 3: Migrate push notifications to Push storage.
 */
val step3 = MigrationStep(
    description = "Migrate push notifications to Push storage"
) {
    logger.i("Starting push notifications migration")

    val notifications = getValue<Map<String, String>>(NOTIFICATIONS) ?: run {
        logger.w("No notifications found in state, skipping")
        return@MigrationStep CONTINUE
    }

    var count = 0

    notifications.forEach { (notificationId, notificationJson) ->
        try {
            val notificationData = json.parseToJsonElement(notificationJson).jsonObject

            val pushNotification = PushNotification(
                id = notificationId,
                credentialId = notificationData["credentialId"]?.jsonPrimitive?.content ?: "",
                messageId = notificationData["messageId"]?.jsonPrimitive?.content ?: notificationId,
                challenge = notificationData["challenge"]?.jsonPrimitive?.content ?: "",
                ttl = notificationData["ttl"]?.jsonPrimitive?.content?.toIntOrNull() ?: 120,
                approved = notificationData["approved"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                pending = notificationData["pending"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                createdAt = Date(notificationData["timeAdded"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()),
                pushType = PushType.DEFAULT,
            )

            pushStorage.storePushNotification(pushNotification)
            count++
            logger.d("Migrated push notification: $notificationId")
        } catch (e: Exception) {
            logger.e("Failed to migrate notification $notificationId", e)
        }
    }

    logger.i("Migrated $count push notifications")

    CONTINUE
}

/**
 * Step 4: Migrate device token to Push storage.
 */
val step4 = MigrationStep(
    description = "Migrate device token to Push storage"
) {
    logger.i("Starting device token migration")

    val deviceTokenMap = getValue<Map<String, String>>(DEVICE_TOKEN) ?: run {
        logger.w("No device token found in state, skipping")
        return@MigrationStep CONTINUE
    }

    deviceTokenMap.forEach { (_, tokenJson) ->
        try {
            val tokenData = json.parseToJsonElement(tokenJson).jsonObject

            val pushDeviceToken = PushDeviceToken(
                id = tokenData["id"]?.jsonPrimitive?.content ?: "",
                tokenId = tokenData["deviceToken"]?.jsonPrimitive?.content
                    ?: tokenData["token"]?.jsonPrimitive?.content
                    ?: "",
                createdAt = Date(tokenData["timeAdded"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()),
            )

            pushStorage.storePushDeviceToken(pushDeviceToken)
            logger.i("Migrated device token successfully")
        } catch (e: Exception) {
            logger.e("Failed to migrate device token", e)
        }
    }

    CONTINUE
}

/**
 * Step 5: Cleanup legacy authenticator data.
 */
val step5 = MigrationStep(
    description = "Cleanup legacy authenticator data"
) {
    logger.i("Cleaning up legacy authenticator data")

    try {
        val repo = LegacyAuthenticationRepository(context)
        repo.deleteLegacyData()
        logger.i("Successfully deleted legacy authenticator data")
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