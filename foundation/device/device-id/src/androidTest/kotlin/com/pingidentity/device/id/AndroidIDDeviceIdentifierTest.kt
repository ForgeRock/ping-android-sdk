/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.id

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pingidentity.android.ContextProvider
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AndroidIDDeviceIdentifierTest {

    private val applicationContext: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }

    @BeforeTest
    fun setUp() {
        ContextProvider.init(applicationContext)
    }

    @Test
    fun returnsAndroidIdAsDeviceId() = runTest {
        val originalAndroidId =  Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID);
        // Simulate the Android ID value
        assertEquals(originalAndroidId, AndroidIDDeviceIdentifier.id())
    }
}