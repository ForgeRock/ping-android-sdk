/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile

import com.pingidentity.device.id.DeviceIdentifier
import com.pingidentity.device.profile.collector.CameraCollector
import com.pingidentity.device.profile.collector.DeviceCollector
import com.pingidentity.device.profile.collector.PlatformCollector
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test

class DeviceProfileCallbackTest {

    @Test
    fun `collect returns config with custom collector`() = runTest {
        val callback = DeviceProfileCallback()
        callback.collect {
            deviceIdentifier = object : DeviceIdentifier {
                override val id = "test"
            }
            metadata {
                add(DummyDeviceCollector())
                add(DeviceCollector("test") {
                    Dummy2("testName2", "testValue2")
                })
                add(Dummy2DeviceCollector)
                add(DeviceCollector<Dummy2>("test3") {
                    null
                })
            }
            metadata {
                add(CameraCollector)
                add(PlatformCollector())
            }
        }
    }

    @Test
    fun `collect returns config with default`() = runTest {
        val callback = DeviceProfileCallback()
        callback.collect()
    }

}

class DummyDeviceCollector : DeviceCollector<Dummy> {
    override val key: String = "dummyCollector"
    override val serializer = Dummy.serializer()
    override suspend fun collect(): Dummy {
        return Dummy("testName", "testValue")
    }
}

val Dummy2DeviceCollector = DeviceCollector("dummy2") {
    Dummy2("testName2", "testValue2")
}

@Serializable
data class Dummy(var name: String, var value: String)

@Serializable
data class Dummy2(var name: String, var value: String)

