/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.device.id.DeviceIdentifier
import com.pingidentity.device.profile.DeviceProfileCallback
import com.pingidentity.device.profile.collector.DefaultDeviceCollector
import com.pingidentity.device.profile.collector.DeviceCollector
import com.pingidentity.device.profile.collector.HardwareCollector
import com.pingidentity.device.profile.collector.PlatformCollector
import com.pingidentity.journey.BaseJourneyTest
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.SuccessNode
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class DeviceProfileCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        tree = "DeviceProfileCallbackTest"
    }

    @Test
    // @BSCase TC-10641
    fun testDeviceProfileCallbackWithDefaultCollectors() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        // The first callback in the journey is a ChoiceCallback (choose to collect location or not...)
        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = 0 // Select "Yes" (collect location)
        node = node.next() as ContinueNode

        val deviceProfileCallback = node.callbacks.first() as DeviceProfileCallback

        val collectors = mutableListOf<DeviceCollector<*>>().apply(DefaultDeviceCollector())
        assertEquals("Collecting profile ...", deviceProfileCallback.message)
        assertTrue(deviceProfileCallback.location)
        assertTrue(deviceProfileCallback.metadata)

        val result = deviceProfileCallback.collect {
            collectors.apply(DefaultDeviceCollector())
        }
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertTrue(profile.keys.isNotEmpty())

        // Assertions based on the example profile
        assertTrue(profile.containsKey("identifier"))
        assertTrue(profile.containsKey("metadata"))
        assertTrue(profile.containsKey("version"))

        val metadata = profile["metadata"]!!.jsonObject
        assertTrue(metadata.containsKey("platform"))
        assertTrue(metadata.containsKey("hardware"))
        assertTrue(metadata.containsKey("network"))
        assertTrue(metadata.containsKey("telephony"))
        assertTrue(metadata.containsKey("bluetooth"))
        assertTrue(metadata.containsKey("browser"))

        // Verify that version is set to "1.0"
        assertEquals("1.0", profile["version"]!!.jsonPrimitive.content)

        // Verify some platform fields
        val platform = metadata["platform"]!!.jsonObject
        assertEquals("android", platform["platform"]!!.jsonPrimitive.content)
        assertTrue(platform["version"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(platform["device"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(platform["deviceName"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(platform["model"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(platform["brand"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(platform["locale"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(platform["timeZone"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(platform["jailBreakScore"]!!.jsonPrimitive.content.isNotEmpty())

        // Verify some hardware fields
        val hardware = metadata["hardware"]!!.jsonObject
        assertTrue(hardware["hardware"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(hardware["manufacturer"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(hardware["storage"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(hardware["memory"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(hardware["cpu"]!!.jsonPrimitive.content.isNotEmpty())
        assertTrue(hardware.containsKey("display"))
        assertTrue(hardware.containsKey("camera"))

        // Verify some network fields
        val network = metadata["network"]!!.jsonObject
        assertTrue(network["connected"]!!.jsonPrimitive.boolean)

        val finalNode = node.next()
        assertTrue(finalNode is SuccessNode)
    }

    @Test
    // @BSCase TC-10642
    fun testDeviceProfileCallbackWithCustomCollectors() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        // The first callback in the journey is a ChoiceCallback (choose to collect location or not...)
        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = 1 // Select "No" (do NOT collect location)
        node = node.next() as ContinueNode

        val deviceProfileCallback = node.callbacks.first() as DeviceProfileCallback

        val result = deviceProfileCallback.collect {
            collectors {
                clear()
                add(PlatformCollector())
                add(HardwareCollector())
            }
        }
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertTrue(profile.keys.isNotEmpty())

        // Assertions
        assertTrue(profile.containsKey("identifier"))
        assertTrue(profile.containsKey("metadata"))
        assertTrue(profile.containsKey("version"))

        val metadata = profile["metadata"]!!.jsonObject
        assertTrue(metadata.containsKey("platform"))
        assertTrue(metadata.containsKey("hardware"))
        // Ensure other collectors are not present
        assertTrue(!metadata.containsKey("network"))
        assertTrue(!metadata.containsKey("telephony"))
        assertTrue(!metadata.containsKey("bluetooth"))
        assertTrue(!metadata.containsKey("browser"))

        val platform = metadata["platform"]!!.jsonObject
        assertTrue(platform["brand"]!!.jsonPrimitive.content.isNotEmpty())

        val hardware = metadata["hardware"]!!.jsonObject
        assertTrue(hardware["manufacturer"]!!.jsonPrimitive.content.isNotEmpty())

        val finalNode = node.next()
        assertTrue(finalNode is SuccessNode)
    }

    @Test
    // @BSCase TC-10647
    fun testDeviceProfileCallbackWithCustomDeviceIdentifier() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        // The first callback in the journey is a ChoiceCallback (choose to collect location or not...)
        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = 1 // Select "No" (do NOT collect location)
        node = node.next() as ContinueNode

        val deviceProfileCallback = node.callbacks.first() as DeviceProfileCallback

        val collectors = mutableListOf<DeviceCollector<*>>().apply(DefaultDeviceCollector())

        val customId = "custom-device-id-67890"
        val result = deviceProfileCallback.collect {
            deviceIdentifier = object : DeviceIdentifier {
                override val id:suspend () -> String = { customId }
            }

            collectors {
                collectors.apply(DefaultDeviceCollector())
            }
        }
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertTrue(profile.keys.isNotEmpty())

        // Assertions
        assertTrue(profile.containsKey("identifier"))
        assertEquals(customId, profile["identifier"]!!.jsonPrimitive.content)

        val finalNode = node.next()
        assertTrue(finalNode is SuccessNode)
    }

    @Test
    // @BSCase TC-10648
    fun testDeviceProfileCallbackWithSimpleCustomCollector() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        // The first callback in the journey is a ChoiceCallback (choose to collect location or not...)
        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = 1 // Select "No" (do NOT collect location)
        node = node.next() as ContinueNode

        val deviceProfileCallback = node.callbacks.first() as DeviceProfileCallback

        @Serializable // Ensure the data class is serializable
        data class BatteryData(val level: Int, val isCharging: Boolean, val capacity: Int)

        val BatteryCollector = DeviceCollector<BatteryData>("battery") {
            BatteryData(
                level = 95,
                isCharging = true,
                capacity = 4000,
            )
        }

        val result = deviceProfileCallback.collect {
            collectors {
                clear()
                add(BatteryCollector)
            }
        }
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertTrue(profile.keys.isNotEmpty())

        // Assertions
        assertTrue(profile.containsKey("metadata"))
        val metadata = profile["metadata"]!!.jsonObject
        assertTrue(metadata.containsKey("battery"))
        val battery = metadata["battery"]!!.jsonObject
        assertEquals(95, battery["level"]!!.jsonPrimitive.int)
        assertEquals(true, battery["isCharging"]!!.jsonPrimitive.boolean)
        assertEquals(4000, battery["capacity"]!!.jsonPrimitive.int)

        val finalNode = node.next()
        assertTrue(finalNode is SuccessNode)
    }

    @Test
    // @BSCase TC-10649
    fun testDeviceProfileCallbackWithComplexCustomCollector() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        // The first callback in the journey is a ChoiceCallback (choose to collect location or not...)
        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = 1 // Select "No" (do NOT collect location)
        node = node.next() as ContinueNode

        val deviceProfileCallback = node.callbacks.first() as DeviceProfileCallback

        val result = deviceProfileCallback.collect {
            collectors {
                clear()
                add(SecurityCollector())
            }
        }
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertTrue(profile.keys.isNotEmpty())

        // Assertions
        assertTrue(profile.containsKey("metadata"))
        val metadata = profile["metadata"]!!.jsonObject
        assertTrue(metadata.containsKey("security"))
        val security = metadata["security"]!!.jsonObject
        assertEquals(false, security["isDeviceSecure"]!!.jsonPrimitive.boolean)
        assertEquals(true, security["hasScreenLock"]!!.jsonPrimitive.boolean)
        assertEquals(false, security["biometricsAvailable"]!!.jsonPrimitive.boolean)

        val finalNode = node.next()
        assertTrue(finalNode is SuccessNode)
    }

    @Test
    // @BSCase TC-10653
    fun testDeviceProfileCallbackWithFaultyCollector() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        // The first callback in the journey is a ChoiceCallback (choose to collect location or not...)
        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = 1 // Select "No" (do NOT collect location)
        node = node.next() as ContinueNode

        val deviceProfileCallback = node.callbacks.first() as DeviceProfileCallback

        val FaultyCollector = DeviceCollector<JsonObject>("faulty") {
            throw RuntimeException("FaultyCollector failed")
        }

        val result = deviceProfileCallback.collect {
            collectors {
                clear()
                add(PlatformCollector())
                add(FaultyCollector)
            }
        }

        assertTrue(result.isFailure)

        val thrown = assertThrows(RuntimeException::class.java) {
            result.getOrThrow()
        }
        assertEquals("FaultyCollector failed", thrown.message)
    }
}

class SecurityCollector : DeviceCollector<SecurityInfo> {
    override val key = "security"
    override val serializer = SecurityInfo.serializer()

    override suspend fun collect(): SecurityInfo {
        return SecurityInfo(
            isDeviceSecure = false,
            hasScreenLock = true,
            biometricsAvailable = false
        )
    }
}

@Serializable
data class SecurityInfo(
    val isDeviceSecure: Boolean,
    val hasScreenLock: Boolean,
    val biometricsAvailable: Boolean
)