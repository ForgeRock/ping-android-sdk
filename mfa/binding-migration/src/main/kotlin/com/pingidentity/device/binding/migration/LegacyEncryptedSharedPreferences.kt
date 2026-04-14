/*
 * Copyright (c) 2023 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.device.binding.migration

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

/**
 * Constant for the AndroidKeyStore provider name.
 */
private const val ANDROID_KEY_STORE = "AndroidKeyStore"

/**
 * Provides access to encrypted SharedPreferences created by the Legacy SDK.
 *
 * This class manages encrypted SharedPreferences that were created using the Legacy SDK's
 * encryption scheme with AndroidKeyStore-backed master keys. It's used during the migration process
 * to decrypt and read legacy application PIN keys and other sensitive data.
 *
 * @see step2
 * @see androidx.security.crypto.EncryptedSharedPreferences
 */
@Suppress("DEPRECATION")
class LegacyEncryptedSharedPreferences {
    companion object {

        /**
         * Creates or retrieves an [EncryptedSharedPreferences] instance configured to access
         * Legacy SDK encrypted SharedPreferences.
         *
         * This method recreates the exact encryption configuration used by the Legacy SDK,
         * allowing the migration process to decrypt and read legacy data.
         *
         * @param context The Android context used to access SharedPreferences and KeyStore.
         * @param fileName The name of the SharedPreferences file. Defaults to
         *                 "secret_shared_prefs" + the application package name.
         * @param aliasName The alias name for the master key in the AndroidKeyStore.
         *                  Defaults to the same value as [fileName].
         *                  Common values from the Legacy SDK:
         *                  - "ORG_FORGEROCK_V_1_DEVICE_REPO" for device repository data
         *                  - Custom identifiers for application-specific storage
         *
         * @return A [SharedPreferences] instance that can read/write encrypted data.
         *
         * @throws java.security.KeyStoreException if the AndroidKeyStore is unavailable
         * @throws java.io.IOException if the SharedPreferences file cannot be accessed
         *
         */
        fun getInstance(
            context: Context,
            fileName: String = "secret_shared_prefs" + context.packageName,
            aliasName: String = fileName
        ): SharedPreferences {

            return try {
                // Creates or gets the key to encrypt and decrypt.
                createPreferencesFile(context, fileName, aliasName)

            } catch (e: Exception) {
                // This step is to recover keys for WebAuthN from beta4 to production. this is a throwaway code in future versions.
                val cache = recoverFromDefaultMasterKey(context, fileName)
                // This is the workaround code when the file got corrupted. Google should provide a fix.
                // Issue - https://github.com/google/tink/issues/535
                deleteKeyEntry(aliasName)
                deletePreferencesFile(context, fileName)
                val sharedPref = createPreferencesFile(context, fileName, aliasName)
                // This step is to add keys for new webauthn preference this is a throwaway code in future versions.
                sharedPref.edit().apply {
                    cache.forEach {
                        this.putStringSet(it.key, it.value)
                    }
                }.apply()
                return sharedPref
            }
        }

        /**
         * Deletes a master key entry from the AndroidKeyStore.
         *
         * This method is part of the error recovery process and is used to remove
         * corrupted or invalid master key entries from the AndroidKeyStore before
         * recreating the encrypted SharedPreferences file.
         *
         * Failures are logged but not propagated to allow the recovery process to continue.
         *
         * @param masterKeyAlias The alias of the master key to delete from the KeyStore.
         */
        private fun deleteKeyEntry(masterKeyAlias: String) {
            try {
                KeyStore.getInstance(ANDROID_KEY_STORE).apply {
                    load(null)
                    deleteEntry(masterKeyAlias)
                }
            }
            catch (e: Exception) {
                BindingMigration.logger.w("Failed to delete key entry: $masterKeyAlias", e)
            }
        }

        /**
         * Deletes the SharedPreferences file from the file system.
         *
         * This method is part of the error recovery process and performs a complete cleanup
         * of corrupted SharedPreferences data. It:
         * 1. Clears all data from the SharedPreferences
         * 2. Deletes the underlying XML file from the file system
         *
         * @param context The Android context used to access SharedPreferences and the file system.
         * @param fileName The name of the SharedPreferences file to delete.
         * @return `true` if the file was successfully deleted, `false` otherwise.
         */
        private fun deletePreferencesFile(context: Context, fileName: String): Boolean {
            // Clear the content of the file
            context.getSharedPreferences(fileName, MODE_PRIVATE).edit().clear().apply()
            // Delete the file
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(fileName)
            } else {
                val dir = File(context.applicationInfo.dataDir, "shared_prefs")
                File(dir, "$fileName.xml").delete()
            }
        }

        /**
         * Creates an [EncryptedSharedPreferences] instance with the specified encryption configuration.
         *
         * If no alias name is provided, the default AndroidX Security master key alias
         * will be used.
         *
         * @param context The Android context used to access SharedPreferences.
         * @param fileName The name of the SharedPreferences file to create.
         * @param aliasName The alias name for the master key in the AndroidKeyStore.
         *                  If null, uses the default master key.
         * @return An [EncryptedSharedPreferences] instance configured with Legacy SDK encryption.
         *
         * @throws java.security.GeneralSecurityException if encryption initialization fails
         */
        private fun createPreferencesFile(
            context: Context,
            fileName: String,
            aliasName: String? = null
        ): SharedPreferences {
            val builder =
                aliasName?.let { MasterKey.Builder(context, it) } ?: MasterKey.Builder(context)
            builder.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            return  EncryptedSharedPreferences.create(
                context,
                fileName,
                builder.build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        /**
         * Recovers data from SharedPreferences encrypted with the default AndroidX Security master key.
         *
         * @param context The Android context used to access SharedPreferences and KeyStore.
         * @param fileName The name of the SharedPreferences file to recover data from.
         *                 Defaults to "secret_shared_prefs" + the application package name.
         * @return A map of key-value pairs recovered from the SharedPreferences, where keys
         *         are preference keys and values are string sets. Returns an empty map if
         *         recovery fails.
         */

        private fun recoverFromDefaultMasterKey(
            context: Context,
            fileName: String = "secret_shared_prefs" + context.packageName
        ): MutableMap<String, MutableSet<String>> {
            val cache = mutableMapOf<String, MutableSet<String>>()
            try {
                val encryptedSharedPreferences =
                    createPreferencesFile(context, fileName)
                encryptedSharedPreferences.all.entries.map { it.key }.forEach { key ->
                    encryptedSharedPreferences.getStringSet(key, emptySet())?.let {
                        val result = mutableSetOf<String>()
                        result.addAll(it)
                        cache[key] = result
                    }
                }
                deleteKeyEntry("_androidx_security_master_key_")
            } catch (e: Exception) {
                BindingMigration.logger.w("Failed to recover from default master key", e)
            }
            return cache
        }
    }
}