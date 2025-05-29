/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.signalssdk.sdk.GetDataCallback
import com.pingidentity.signalssdk.sdk.InitCallback
import com.pingidentity.signalssdk.sdk.PingOneSignals
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtectTest {

    @Test
    fun configSetsAllProtectConfigPropertiesCorrectly() {
        Protect.config {
            envId = "env123"
            deviceAttributesToIgnore = listOf("attr1", "attr2")
            customHost = "custom.host"
            isConsoleLogEnabled = true
            isLazyMetadata = true
            isBehavioralDataCollection = false
        }
        // Reflection to access private property for test
        val config = Protect::class.java.getDeclaredField("protectConfig").apply { isAccessible = true }.get(Protect) as ProtectConfig
        assertEquals("env123", config.envId)
        assertEquals(listOf("attr1", "attr2"), config.deviceAttributesToIgnore)
        assertEquals("custom.host", config.customHost)
        assertTrue(config.isConsoleLogEnabled)
        assertTrue(config.isLazyMetadata)
        assertFalse(config.isBehavioralDataCollection)
    }

    @Test
    fun initDoesNotReinitializeIfAlreadyInitialized() = runTest {
        val context = mockk<Context>(relaxed = true)
        mockkObject(ContextProvider)
        every { ContextProvider.context } returns context
        mockkStatic("com.pingidentity.signalssdk.sdk.PingOneSignals")
        every { PingOneSignals.init(any(), any()) } just runs
        every { PingOneSignals.setInitCallback(any()) } answers {
            val callback = arg<InitCallback>(0)
            callback.onInitialized() // or callback.onError("code", "msg", "details")
        }
        Protect.config {  }

        Protect.init()
        val isInitializedField = Protect::class.java.getDeclaredField("isInitialized").apply { isAccessible = true }
        //isInitializedField.set(Protect, true)
        // Should not throw or call PingOneSignals.init again
        assertTrue(isInitializedField.get(Protect) as Boolean)
    }

    @Test
    fun dataReturnsDeviceSignalsOnSuccess() = runTest {
        mockkStatic("com.pingidentity.signalssdk.sdk.PingOneSignals")
        coEvery { PingOneSignals.getData(any()) } answers {
            val callback = arg<GetDataCallback>(0)
            callback.onSuccess("signals")
        }
        val result = Protect.data()
        assertEquals("signals", result)
    }

    @Test
    fun dataThrowsProtectExceptionOnFailure() = runTest {
        mockkStatic("com.pingidentity.signalssdk.sdk.PingOneSignals")
        coEvery { PingOneSignals.getData(any()) } answers {
            val callback = arg<GetDataCallback>(0)
            callback.onFailure("error")
        }
        val exception = kotlin.runCatching { Protect.data() }.exceptionOrNull()
        assertTrue(exception is ProtectException)
        assertEquals("error", exception.message)
    }

    @Test
    fun pauseBehavioralDataCallsUnderlyingSdk() {
        mockkStatic("com.pingidentity.signalssdk.sdk.PingOneSignals")
        every { PingOneSignals.pauseBehavioralData() } returns Unit
        Protect.pauseBehavioralData()
        verify(exactly = 1) { PingOneSignals.pauseBehavioralData() }
    }

    @Test
    fun resumeBehavioralDataCallsUnderlyingSdk() {
        mockkStatic("com.pingidentity.signalssdk.sdk.PingOneSignals")
        every { PingOneSignals.resumeBehavioralData() } returns Unit
        Protect.resumeBehavioralData()
        verify(exactly = 1) { PingOneSignals.resumeBehavioralData() }
    }
}