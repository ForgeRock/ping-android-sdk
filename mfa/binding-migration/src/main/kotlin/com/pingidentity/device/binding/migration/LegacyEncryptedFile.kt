/*
 * Copyright (c) 2023 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.device.binding.migration

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Utility class for accessing encrypted files created by the Legacy SDK.
 *
 * This class provides a bridge to read encrypted files that were created using the Legacy SDK's
 * It's used during the migration process to decrypt and read legacy biometric authentication data.
 *
 * The Legacy SDK stored keys in encrypted files using the AndroidX Security library's
 * [EncryptedFile] with specific encryption parameters. This class recreates the same encryption
 * configuration to enable reading those files during migration.
 *
 * @see step1
 * @see androidx.security.crypto.EncryptedFile
 */
class LegacyEncryptedFile {
    companion object {
        /**
         * Creates or retrieves an [EncryptedFile] instance configured to access Legacy SDK encrypted files.
         *
         * This method recreates the exact encryption configuration used by the Legacy SDK,
         * allowing the migration process to decrypt and read legacy data.
         */
        fun getInstance(context: Context, file: File, aliasName: String): EncryptedFile {
            // Creates or gets the key to encrypt and decrypt.
            val masterKeyAlias = MasterKey.Builder(context, aliasName)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedFile.Builder(context, file,
                masterKeyAlias,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
        }

    }
}

