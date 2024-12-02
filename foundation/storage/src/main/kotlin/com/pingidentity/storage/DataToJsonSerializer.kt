/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.storage

import androidx.datastore.core.Serializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Creates a new Serializer instance for a given type T.
 * The object will be serialized using kotlinx.serialization to/from JSON.
 *
 * @param T The type of the object to be serialized/deserialized. The object must be serializable.
 *
 * @return A new Serializer instance.
 */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T : @Serializable Any> DataToJsonSerializer(): Serializer<T?> {
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
            return if (input.isNotEmpty()) {
                json.decodeFromStream(input)
            } else {
                null
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
            t?.let { json.encodeToStream(it, output) }
        }

        /**
         * Checks if the InputStream is not empty.
         *
         * @return True if the InputStream is not empty, false otherwise.
         */
        private fun InputStream.isNotEmpty() = available() > 0
    }
}
