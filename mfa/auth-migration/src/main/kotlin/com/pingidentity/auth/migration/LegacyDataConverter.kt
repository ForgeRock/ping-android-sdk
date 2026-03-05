/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Utility functions for converting custom storage data to migration-ready JSON format.
 */
object LegacyDataConverter {

    /**
     * Builds legacy export JSON from raw mechanism and account JSON strings.
     *
     * This function is designed for custom storage implementations where the data
     * is stored similarly to ForgeRock's default storage but may use different encryption
     * or storage mechanisms.
     *
     * @param mechanismsMap Map of mechanism ID to mechanism JSON string
     * @param accountsMap Map of account ID to account JSON string
     * @return JSON string ready for migration using AuthMigration.startWithJson()
     *
     * ## Example - Convert Custom Storage Data
     *
     * ```kotlin
     * val customStorage = CustomStorageClient(context)
     *
     * // Get raw data from custom storage
     * val mechanismsMap = mutableMapOf<String, String>()
     * customStorage.getAllMechanisms().forEach { mechanism ->
     *     mechanismsMap[mechanism.id] = mechanism.toJson()
     * }
     *
     * val accountsMap = mutableMapOf<String, String>()
     * customStorage.getAllAccounts().forEach { account ->
     *     accountsMap[account.id] = account.toJson()
     * }
     *
     * // Convert to migration format
     * val exportJson = LegacyDataConverter.buildExportJson(mechanismsMap, accountsMap)
     *
     * // Migrate
     * lifecycleScope.launch {
     *     AuthMigration.startWithJson(applicationContext, exportJson)
     * }
     * ```
     */
    fun buildExportJson(
        mechanismsMap: Map<String, String>,
        accountsMap: Map<String, String>
    ): String {
        val mechanisms = buildMechanismsList(mechanismsMap, accountsMap)

        val exportedData = LegacyExportedData(
            mechanisms = mechanisms,
            metadata = LegacyExportMetadata(
                totalMechanisms = mechanisms.size
            )
        )

        return legacyJson.encodeToString(LegacyExportedData.serializer(), exportedData)
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

