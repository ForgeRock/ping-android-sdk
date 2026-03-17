/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import kotlinx.serialization.json.Json
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
    private fun buildMechanismsList(
        mechanismsMap: Map<String, String>,
        accountsMap: Map<String, String>
    ): List<LegacyMechanism> {
        val parseJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        return mechanismsMap.mapNotNull { (mechanismId, mechanismJson) ->
            try {
                val mechanismData = parseJson.parseToJsonElement(mechanismJson).jsonObject

                // Extract mechanism fields
                val mechanismIssuer = mechanismData["issuer"]?.jsonPrimitive?.content ?: ""
                val mechanismAccountName = mechanismData["accountName"]?.jsonPrimitive?.content ?: ""

                // Find associated account (Account ID format: issuer + "-" + accountName)
                val accountId = "$mechanismIssuer-$mechanismAccountName"
                val accountJson = accountsMap[accountId]
                val accountData = accountJson?.let {
                    parseJson.parseToJsonElement(it).jsonObject
                }

                // Build nested account
                val account = accountData?.let {
                    LegacyAccount(
                        id = it["id"]?.jsonPrimitive?.content ?: accountId,
                        issuer = it["issuer"]?.jsonPrimitive?.content ?: mechanismIssuer,
                        displayIssuer = it["displayIssuer"]?.jsonPrimitive?.content,
                        accountName = it["accountName"]?.jsonPrimitive?.content ?: mechanismAccountName,
                        displayAccountName = it["displayAccountName"]?.jsonPrimitive?.content,
                        imageURL = it["imageURL"]?.jsonPrimitive?.content,
                        backgroundColor = it["backgroundColor"]?.jsonPrimitive?.content,
                        timeAdded = it["timeAdded"]?.jsonPrimitive?.content?.toLongOrNull(),
                        policies = it["policies"]?.jsonPrimitive?.content,
                        lockingPolicy = it["lockingPolicy"]?.jsonPrimitive?.content,
                        lock = it["lock"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    )
                }

                // Build mechanism with all fields
                LegacyMechanism(
                    id = mechanismData["id"]?.jsonPrimitive?.content ?: mechanismId,
                    issuer = mechanismIssuer,
                    accountName = mechanismAccountName,
                    mechanismUID = mechanismData["mechanismUID"]?.jsonPrimitive?.content
                        ?: mechanismData["id"]?.jsonPrimitive?.content
                        ?: mechanismId,
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
                    resourceId = mechanismData["resourceId"]?.jsonPrimitive?.content,
                    timeAdded = mechanismData["timeAdded"]?.jsonPrimitive?.content?.toLongOrNull(),

                    // Nested account
                    account = account
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}