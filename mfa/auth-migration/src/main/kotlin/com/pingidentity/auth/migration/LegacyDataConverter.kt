/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.forgerock.android.auth.StorageClient

/**
 * Utility functions for converting custom storage data to migration-ready JSON format.
 */
object LegacyDataConverter {

    /**
     * Converts legacy authenticator data from a [StorageClient] into a [LegacyExportedData]
     * object ready to be returned from [LegacyStorageProvider.getMigrationData].
     *
     * This helper uses the SDK's own [org.forgerock.android.auth.Account.toJson] and
     * [org.forgerock.android.auth.Mechanism.toJson] methods to serialize each entry, so no
     * manual field mapping is required and all mechanism types (OATH and Push) are handled
     * automatically.
     *
     * @param storageClient The ForgeRock [StorageClient] holding the legacy accounts and mechanisms.
     * @return [LegacyExportedData] containing all accounts and mechanisms ready for migration.
     *
     * ## Example
     * ```kotlin
     * class MyCustomStorageConfiguration(
     *     private val context: Context,
     *     private val storageClient: StorageClient
     * ) : CustomStorageConfiguration {
     *
     *     override suspend fun getMigrationData(context: Context): LegacyExportedData =
     *         withContext(Dispatchers.IO) {
     *             LegacyDataConverter.convertToLegacyExportedData(storageClient)
     *         }
     * }
     * ```
     */
    fun convertToLegacyExportedData(storageClient: StorageClient): LegacyExportedData {
        val mechanismsMap = mutableMapOf<String, String>()
        val accountsMap = mutableMapOf<String, String>()

        storageClient.allAccounts.forEach { account ->
            accountsMap[account.id] = account.toJson()

            storageClient.getMechanismsForAccount(account).forEach { mechanism ->
                mechanismsMap[mechanism.id] = mechanism.toJson()
            }
        }

        val mechanisms = buildMechanismsList(mechanismsMap, accountsMap)

        return LegacyExportedData(
            mechanisms = mechanisms,
            metadata = LegacyExportMetadata(
                totalMechanisms = mechanisms.size
            )
        )
    }

    /**
     * Build mechanisms list with nested account data from raw JSON strings.
     */
    fun buildMechanismsList(
        mechanismsMap: Map<String, String>,
        accountsMap: Map<String, String>
    ): List<LegacyMechanism> {
        return mechanismsMap.mapNotNull { (mechanismId, mechanismJson) ->
            try {
                val mechanismData = legacyJson.parseToJsonElement(mechanismJson).jsonObject

                // Extract mechanism fields
                val mechanismIssuer = mechanismData["issuer"]?.jsonPrimitive?.content ?: run {
                    AuthMigration.logger.w("Skipping mechanism $mechanismId: no issuer found")
                    return@mapNotNull null
                }
                val mechanismAccountName = mechanismData["accountName"]?.jsonPrimitive?.content ?: run {
                    AuthMigration.logger.w("Skipping mechanism $mechanismId: no accountName found")
                    return@mapNotNull null
                }
                val mechanismUID = mechanismData["mechanismUID"]?.jsonPrimitive?.content ?: run {
                    AuthMigration.logger.w("Skipping mechanism $mechanismId: no mechanismUID found")
                    return@mapNotNull null
                }

                // Find associated account (Account ID format: issuer + "-" + accountName)
                val accountId = "$mechanismIssuer-$mechanismAccountName"
                val accountJson = accountsMap[accountId]
                val accountData = accountJson?.let {
                    legacyJson.parseToJsonElement(it).jsonObject
                }

                // Build nested account — skip mechanism if no account is found
                if (accountData == null) {
                    AuthMigration.logger.w("Skipping mechanism $mechanismId: no associated account found for '$accountId'")
                    return@mapNotNull null
                }

                // Build nested account
                val account = LegacyAccount(
                    id = accountData["id"]?.jsonPrimitive?.content ?: accountId,
                    issuer = accountData["issuer"]?.jsonPrimitive?.content ?: mechanismIssuer,
                    displayIssuer = accountData["displayIssuer"]?.jsonPrimitive?.content,
                    accountName = accountData["accountName"]?.jsonPrimitive?.content ?: mechanismAccountName,
                    displayAccountName = accountData["displayAccountName"]?.jsonPrimitive?.content,
                    imageURL = accountData["imageURL"]?.jsonPrimitive?.content,
                    backgroundColor = accountData["backgroundColor"]?.jsonPrimitive?.content,
                    timeAdded = accountData["timeAdded"]?.jsonPrimitive?.content?.toLongOrNull(),
                    policies = accountData["policies"]?.jsonPrimitive?.content,
                    lockingPolicy = accountData["lockingPolicy"]?.jsonPrimitive?.content,
                    lock = accountData["lock"]?.jsonPrimitive?.content?.toBoolean() ?: false
                )

                // Build mechanism with all fields
                LegacyMechanism(
                    id = mechanismData["id"]?.jsonPrimitive?.content ?: mechanismId,
                    issuer = mechanismIssuer,
                    accountName = mechanismAccountName,
                    mechanismUID = mechanismUID,
                    secret = mechanismData["secret"]?.jsonPrimitive?.content ?: "",
                    type = mechanismData["type"]?.jsonPrimitive?.content ?: "",

                    // OATH fields
                    oathType = mechanismData["oathType"]?.jsonPrimitive?.content,
                    algorithm = mechanismData["algorithm"]?.jsonPrimitive?.content,
                    digits = mechanismData["digits"]?.jsonPrimitive?.content?.toIntOrNull(),
                    period = mechanismData["period"]?.jsonPrimitive?.content?.toIntOrNull(),
                    counter = mechanismData["counter"]?.jsonPrimitive?.content?.toLongOrNull(),

                    // Push fields
                    registrationEndpoint = mechanismData["registrationEndpoint"]?.jsonPrimitive?.content,
                    authenticationEndpoint = mechanismData["authenticationEndpoint"]?.jsonPrimitive?.content,
                    platform = mechanismData["platform"]?.jsonPrimitive?.content,

                    // Common fields
                    uid = mechanismData["uid"]?.jsonPrimitive?.content,
                    resourceId = mechanismData["resourceId"]?.jsonPrimitive?.content ?: mechanismUID,
                    timeAdded = mechanismData["timeAdded"]?.jsonPrimitive?.content?.toLongOrNull(),

                    // Nested account
                    account = account
                )
            } catch (e: Exception) {
                AuthMigration.logger.e("Failed to parse mechanism $mechanismId: ${e.message}", e)
                null
            }
        }
    }
}