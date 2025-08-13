/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.id

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.forgerock.android.auth.DeviceIdentifier
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class LegacyDeviceIdentifierTest {

    private val applicationContext: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }

    @AfterTest
    fun tearDown() = runTest {
        KeyManager.removeKey(DefaultDeviceIdentifier.KEY_ALIAS)
        KeyManager.removeKey(AndroidIDDeviceIdentifier.id())
    }

    @Test
    fun testIdNotNull() = runTest {
        val id = LegacyDeviceIdentifier.id()
        assertNotNull(id, "Legacy Device ID should not be null")
    }

    @Test
    fun testIdConsistency() = runTest {

        val firstCall = LegacyDeviceIdentifier.id()
        val secondCall = LegacyDeviceIdentifier.id()

        assertEquals(firstCall, secondCall, "Legacy Device ID should be consistent between calls")
    }

    @Test
    fun testLegacyId() = runTest {
        val legacyId = DeviceIdentifier.builder().context(applicationContext).build().identifier
        val legacyDeviceId = LegacyDeviceIdentifier.id()

        assertEquals(
            legacyId,
            legacyDeviceId,
            "Legacy Device ID should match the DeviceIdentifier implementation"
        )
    }
}

