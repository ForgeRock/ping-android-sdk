/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class EncryptedDataStoreStorageTest {

    @Test
    fun createsStorageWithValidConfig() {
        val storage = EncryptedDataStoreStorage<String> {
            fileName = "secure_prefs"
            cache = true
            keyAlias = EncryptedDataStoreStorageTest::class.java.simpleName
        }
        assertNotNull(storage)
    }

    @Test
    fun throwsExceptionWhenFileNameNotSet() {
        assertFailsWith<UninitializedPropertyAccessException> {
            EncryptedDataStoreStorage<String> {
                // fileName not set
                keyAlias = DataStoreStorageWithEncryptorTest::class.java.simpleName
            }
        }
    }
}