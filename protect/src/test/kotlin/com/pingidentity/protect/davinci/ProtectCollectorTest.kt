/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

import com.pingidentity.protect.Protect
import com.pingidentity.protect.davinci.ProtectCollector
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtectCollectorTest {

    @BeforeTest
    fun setup() {
        mockkObject(Protect)
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(Protect)
    }

    @Test
    fun initSetsAllPropertiesFromJson() {
        val collector = ProtectCollector()
        val jsonObject = Json.parseToJsonElement(
            """
            {
                "key": "riskKey",
                "behavioralDataCollection": false,
                "universalDeviceIdentification": true
            }
            """.trimIndent()
        ) as JsonObject
        collector.init(jsonObject)
        assertEquals("riskKey", collector.key)
        assertFalse(collector.behavioralDataCollection)
        assertTrue(collector.universalDeviceIdentification)
    }

    @Test
    fun initUsesDefaultsWhenFieldsMissing() {
        val collector = ProtectCollector()
        val json = buildJsonObject { }
        collector.init(json)
        assertEquals("", collector.key)
        assertTrue(collector.behavioralDataCollection)
        assertFalse(collector.universalDeviceIdentification)
    }

    @Test
    fun idReturnsKey() {
        val collector = ProtectCollector()
        val json = buildJsonObject { put("key", "abc") }
        collector.init(json)
        assertEquals("abc", collector.id())
    }

    @Test
    fun payloadReturnsNullWhenValueIsEmpty() {
        val collector = ProtectCollector()
        assertNull(collector.payload())
    }

    @Test
    fun collectReturnsSuccessWithData() = runTest {
        coEvery { Protect.init() } returns Unit
        coEvery { Protect.data() } returns "deviceSignals"
        val collector = ProtectCollector()
        val result = collector.collect()
        assertTrue(result.isSuccess)
        assertEquals("deviceSignals", result.getOrNull())
        assertEquals("deviceSignals", collector.payload())
    }

    @Test
    fun collectReturnsFailureOnException() = runBlocking {
        every { Protect.config(any()) } returns Unit
        coEvery { Protect.init() } returns Unit
        coEvery { Protect.data() } throws RuntimeException("fail")
        val collector = ProtectCollector()
        val result = collector.collect()
        assertTrue(result.isFailure)
        assertEquals("fail", result.exceptionOrNull()?.message)
    }
}