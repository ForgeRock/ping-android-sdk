/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Exported authenticator data from Legacy SDK.
 */
@Serializable
data class ExportedData(
    val accounts: Map<String, String>,
    val mechanisms: Map<String, String>,
    val notifications: Map<String, String>,
    val deviceToken: Map<String, String>,
    val metadata: ExportMetadata
)

/**
 * Metadata about exported authenticator data.
 */
@Serializable
data class ExportMetadata(
    val totalAccounts: Int,
    val totalMechanisms: Int,
    val totalNotifications: Int,
    val hasDeviceToken: Boolean
)

private const val AUTH_DATA_ACCOUNT = "org.forgerock.android.authenticator.DATA.ACCOUNT"
private const val AUTH_DATA_MECHANISM = "org.forgerock.android.authenticator.DATA.MECHANISM"
private const val AUTH_DATA_NOTIFICATIONS = "org.forgerock.android.authenticator.DATA.NOTIFICATIONS"
private const val AUTH_DATA_DEVICE_TOKEN = "org.forgerock.android.authenticator.DATA.DEVICE_TOKEN"


/**
 * Repository for reading and exporting Legacy SDK authenticator data.
 */
class LegacyAuthenticationRepository(private val context: Context) {

    private val decryptor = LegacyAuthenticationDecryptor(
        context,
        "org.forgerock.android.authenticator.KEYS"
    )

    /**
     * Checks if legacy encryption key exists in KeyStore.
     */
    suspend fun isExists(): Boolean = withContext(Dispatchers.IO) {
        decryptor.keyExists()
    }

    /**
     * Export all FR Authenticator data from Legacy SDK.
     * Uses standalone decryptor - NO Legacy SDK dependency required!
     */
    suspend fun exportAllData(): ExportedData = withContext(Dispatchers.IO) {
        // Check if legacy data exists
        if (!decryptor.keyExists()) {
            return@withContext emptyExport()
        }

        try {
            // Decrypt all 4 files
            val accounts = decryptIfExists(AUTH_DATA_ACCOUNT)
            val mechanisms = decryptIfExists(AUTH_DATA_MECHANISM)
            val notifications = decryptIfExists(AUTH_DATA_NOTIFICATIONS)
            val deviceToken = decryptIfExists(AUTH_DATA_DEVICE_TOKEN)

            ExportedData(
                accounts = accounts,
                mechanisms = mechanisms,
                notifications = notifications,
                deviceToken = deviceToken,
                metadata = ExportMetadata(
                    totalAccounts = accounts.size,
                    totalMechanisms = mechanisms.size,
                    totalNotifications = notifications.size,
                    hasDeviceToken = deviceToken.isNotEmpty()
                )
            )
        } catch (e: Exception) {
            AuthMigration.logger.e("Failed to export legacy data: ${e.message}", e)
            throw e
        }
    }

    /**
     * Decrypts SharedPreferences file if it exists on disk.
     */
    private fun decryptIfExists(preferenceName: String): Map<String, String> {
        // Check if file exists
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val prefsFile = File(prefsDir, "$preferenceName.xml")

        if (!prefsFile.exists()) {
            return emptyMap()
        }

        return try {
            decryptor.decryptAll(preferenceName)
        } catch (e: Exception) {
            AuthMigration.logger.e("Failed to decrypt $preferenceName: ${e.message}", e)
            emptyMap()
        }
    }

    /**
     * Delete legacy data after successful migration.
     */
    suspend fun deleteLegacyData() = withContext(Dispatchers.IO) {
        context.deleteSharedPreferences(AUTH_DATA_ACCOUNT)
        context.deleteSharedPreferences(AUTH_DATA_MECHANISM)
        context.deleteSharedPreferences(AUTH_DATA_NOTIFICATIONS)
        context.deleteSharedPreferences(AUTH_DATA_DEVICE_TOKEN)

        // Delete encryption key
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry("org.forgerock.android.authenticator.KEYS")
    }

    /**
     * Creates an empty export data object.
     */
    private fun emptyExport() = ExportedData(
        accounts = emptyMap(),
        mechanisms = emptyMap(),
        notifications = emptyMap(),
        deviceToken = emptyMap(),
        metadata = ExportMetadata(0, 0, 0, false)
    )
}