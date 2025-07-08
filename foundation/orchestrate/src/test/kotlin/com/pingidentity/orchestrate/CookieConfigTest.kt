/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

import com.pingidentity.orchestrate.module.CookieConfig
import com.pingidentity.storage.EncryptedDataStoreStorageConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CookieConfigTest {

    private lateinit var cookieConfig: CookieConfig

    @Before
    fun setUp() {
        cookieConfig = CookieConfig()
    }

    @Test
    fun `storageOption should be customizable`() {
        cookieConfig.storage {
            fileName = "custom_file"
        }

        val config = EncryptedDataStoreStorageConfig().apply(cookieConfig.storageOption)

        //Override the default file name
        assertEquals("custom_file", config.fileName)
        //Keep the default keyAlias
        assertEquals("com.pingidentity.sdk.v1.cookies", config.keyAlias)
        assertTrue(config.strongBoxPreferred)
    }

    @Test
    fun `storageOption should be accumulate customization`() {
        cookieConfig.storage {
            fileName = "custom_file"
        }

        cookieConfig.storage {
            strongBoxPreferred = false
        }

        cookieConfig.storage {
            symmetricKeySize = 128
        }

        cookieConfig.storage {
            keyAlias = "custom_key_alias"
        }

        val config = EncryptedDataStoreStorageConfig().apply(cookieConfig.storageOption)

        //Override the default file name
        assertEquals("custom_file", config.fileName)
        //Keep the default keyAlias
        assertEquals("custom_key_alias", config.keyAlias)
        assertFalse(config.strongBoxPreferred)
        assertEquals(128, config.symmetricKeySize)
    }
}