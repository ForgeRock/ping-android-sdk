/*
 * Copyright (c) 2023 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.device.binding.migration

import android.content.Context
import androidx.security.crypto.EncryptedFile
import java.io.File
import java.io.InputStream

/**
 * Default master key alias used by the Legacy SDK for encrypting file-based key stores.
 */
private const val ORG_FORGEROCK_V_1_KEY_STORE_MASTER_KEY_ALIAS = "org.forgerock.v1.KEY_STORE_MASTER_KEY_ALIAS"

/**
 * Provides access to encrypted file-based key stores created by the Legacy SDK.
 *
 * This class manages encrypted files that store cryptographic keys or sensitive data from the
 * Legacy SDK. It acts as a wrapper around [LegacyEncryptedFile] to provide convenient methods
 * for reading, checking existence, and deleting legacy encrypted key store files during migration.
 *
 * The Legacy SDK stored application pin authentication keys in encrypted files within the app's
 * internal storage directory. This class provides the necessary abstraction to:
 * - Read the encrypted key data for migration to the new storage format
 * - Check if a legacy key store exists before attempting migration
 * - Clean up legacy files after successful migration
 *
 * @property identifier The filename of the encrypted key store file in the app's files directory.
 *
 * @property aliasName The alias name for the master key in the AndroidKeyStore used to
 *                     encrypt/decrypt the file. Defaults to [ORG_FORGEROCK_V_1_KEY_STORE_MASTER_KEY_ALIAS].
 *
 * @see LegacyEncryptedFile
 * @see step3
 */
class LegacyEncryptedFileKeyStore(
    private val identifier: String,
    private val aliasName: String = ORG_FORGEROCK_V_1_KEY_STORE_MASTER_KEY_ALIAS
) {

    /**
     * Opens an input stream to read the decrypted contents of the legacy key store file.
     *
     * This method provides access to the decrypted data stored in the legacy encrypted file.
     * The returned [InputStream] should be properly closed after use, preferably using
     * Kotlin's `use` extension function.
     *
     * @param context The Android context used to access the file system and encryption keys.
     * @return An [InputStream] for reading the decrypted file contents.
     *
     * @throws java.io.FileNotFoundException if the encrypted file does not exist
     * @throws java.security.GeneralSecurityException if decryption fails
     *
     * ## Example
     *
     * ```kotlin
     * val keyStore = LegacyEncryptedFileKeyStore("ORG_FORGEROCK_V_1_BIOMETRIC")
     * keyStore.getInputStream(context).use { input ->
     *     val keyData = input.readBytes()
     *     // Process the decrypted key data
     * }
     * ```
     */
    fun getInputStream(context: Context): InputStream {
        return getEncryptedFile(context).openFileInput()
    }

    /**
     * Deletes the legacy key store file from the file system.
     *
     * This method should be called after successfully migrating the key data to the new
     * storage format. It removes the encrypted file from the app's internal storage directory,
     * helping to clean up legacy data and prevent confusion during subsequent migrations.
     *
     * Note: This method does not delete the master key from the AndroidKeyStore. The master
     * key cleanup is handled separately in the migration process.
     *
     * @param context The Android context used to access the file system.
     *
     */
    fun delete(context: Context) {
        val file = File(context.filesDir, identifier)
        file.delete()
    }

    /**
     * Checks if the legacy key store file exists in the file system.
     *
     * This method is useful for determining whether migration is necessary. If the file
     * doesn't exist, it indicates that either:
     * - The Legacy SDK was never used in this app installation
     * - The migration has already been completed
     * - The file was manually deleted
     *
     * @param context The Android context used to access the file system.
     * @return `true` if the encrypted key store file exists, `false` otherwise.
     *
     */
    fun exist(context: Context): Boolean {
        val file = File(context.filesDir, identifier)
        return file.exists()
    }

    /**
     * Creates an [EncryptedFile] instance for accessing the legacy key store file.
     *
     * This private helper method instantiates the appropriate [EncryptedFile] wrapper
     * using the configured identifier and alias name, delegating to [LegacyEncryptedFile]
     * to ensure compatibility with the Legacy SDK's encryption scheme.
     *
     * @param context The Android context used to access the file system and encryption keys.
     * @return An [EncryptedFile] instance configured for the legacy key store.
     */
    private fun getEncryptedFile(context: Context): EncryptedFile {
        val file = File(context.filesDir, identifier)
        return LegacyEncryptedFile.getInstance(context, file, aliasName)
    }

}