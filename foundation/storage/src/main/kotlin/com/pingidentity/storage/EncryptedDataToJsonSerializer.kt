/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.pingidentity.storage.encrypt.Encryptor
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * Creates an encrypted serializer for the given type.
 *
 * @param T The type of the object to serialize.
 * @param encryptor The encryptor to use for encryption and decryption.
 * @param serializer The serializer to use for serialization and deserialization.
 *
 * @return A Serializer that encrypts the serialized data.
 */
inline fun <reified T : Any> EncryptedDataToJsonSerializer(
    encryptor: Encryptor,
    serializer: KSerializer<T> = Json.serializersModule.serializer()
): Serializer<T?> {
    return object : Serializer<T?> {
        override val defaultValue: T? = null

        /**
         * Reads the object from the given InputStream.
         *
         * @param input The InputStream to read from.
         *
         * @return The object if it exists, null otherwise.
         */
        override suspend fun readFrom(input: InputStream): T? {
            try {
                return if (input.isNotEmpty()) {
                    val result = encryptor.decrypt(input.readBytes())
                    if(T::class == ByteArray::class) {
                        return if (result.isEmpty()) null else result as T
                    }
                    return if (result.isEmpty()) null else json.decodeFromString(String(result))
                } else {
                    null
                }
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                throw CorruptionException("Failed to decrypt data", e)
            }
        }

        /**
         * Writes the object to the given OutputStream.
         *
         * @param t The object to write.
         * @param output The OutputStream to write to.
         */
        override suspend fun writeTo(
            t: T?,
            output: OutputStream,
        ) {
            t?.let {
                if (t is ByteArray) {
                    output.write(encryptor.encrypt(t))
                    return
                } else {
                    output.write(
                        encryptor.encrypt(
                            json.encodeToString(serializer, it).toByteArray()
                        )
                    )
                }
            }
        }

        /**
         * Checks if the InputStream is not empty.
         *
         * @return True if the InputStream is not empty, false otherwise.
         */
        private fun InputStream.isNotEmpty() = available() > 0
    }
}