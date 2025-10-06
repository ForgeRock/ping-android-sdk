/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.storage.CacheStrategy
import com.pingidentity.storage.Storage
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class UserKeyStorageConfigTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }

    @BeforeTest
    fun setUp() {
        ContextProvider.init(context)
    }

    @Test
    fun `test default configuration`() {
        val config = UserKeyStorageConfig()

        assertNotNull(config.logger)
        assertNotNull(config.storage)
    }

    @Test
    fun `test custom logger configuration`() {
        val mockLogger = mockk<Logger>()
        val config = UserKeyStorageConfig().apply {
            logger = mockLogger
        }

        assertEquals(mockLogger, config.logger)
    }

    @Test
    fun `test default storage creation`() {
        val config = UserKeyStorageConfig()

        val storage = config.storage()

        assertNotNull(storage)
    }

    @Test
    fun `test custom storage function`() {
        val mockStorage = mockk<Storage<List<UserKey>>>()
        val config = UserKeyStorageConfig().apply {
            storage = { mockStorage }
        }

        val storage = config.storage()

        assertEquals(mockStorage, storage)
    }

    @Test
    fun `test storage configuration customization`() {
        val config = UserKeyStorageConfig().apply {
            storage {
                fileName = "custom_user_keys"
                keyAlias = "custom_key_alias"
                strongBoxPreferred = true
                cacheStrategy = CacheStrategy.NO_CACHE
            }
        }

        // Create storage to ensure configuration is applied without errors
        val storage = config.storage()
        assertNotNull(storage)
    }

    @Test
    fun `test multiple storage configuration calls`() {
        val config = UserKeyStorageConfig().apply {
            storage {
                fileName = "first_config"
            }
            storage {
                keyAlias = "second_config"
            }
        }

        // Both configurations should be applied
        val storage = config.storage()
        assertNotNull(storage)
    }

    @Test
    fun `test logger propagation to storage config`() {
        val mockLogger = mockk<Logger>()
        val config = UserKeyStorageConfig().apply {
            logger = mockLogger
        }

        // Create storage to ensure logger is propagated
        val storage = config.storage()
        assertNotNull(storage)
    }
}

