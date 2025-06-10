/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import com.pingidentity.storage.EncryptedDataStoreStorageConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionConfigTest {

    private lateinit var sessionConfig: SessionConfig

    @Before
    fun setUp() {
        sessionConfig = SessionConfig()
    }

    @Test
    fun `storageOption should be customizable`() {
        sessionConfig.storage {
            fileName = "custom_file"
        }

        val config = EncryptedDataStoreStorageConfig().apply(sessionConfig.storageOption)

        //Override the default file name
        assertEquals("custom_file", config.fileName)
        //Keep the default keyAlias
        assertEquals("com.pingidentity.sdk.v1.session", config.keyAlias)
        assertTrue(config.strongBoxPreferred)
    }

    @Test
    fun `storageOption should be accumulate customization`() {
        sessionConfig.storage {
            fileName = "custom_file"
        }

        sessionConfig.storage {
            strongBoxPreferred = false
        }

        sessionConfig.storage {
            symmetricKeySize = 128
        }

        sessionConfig.storage {
            keyAlias = "custom_key_alias"
        }

        val config = EncryptedDataStoreStorageConfig().apply(sessionConfig.storageOption)

        //Override the default file name
        assertEquals("custom_file", config.fileName)
        //Keep the default keyAlias
        assertEquals("custom_key_alias", config.keyAlias)
        assertFalse(config.strongBoxPreferred)
        assertEquals(128, config.symmetricKeySize)
    }
}