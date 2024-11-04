/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

import com.pingidentity.testrail.TestRailCase
import com.pingidentity.davinci.CollectorRegistry
import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.davinci.collector.PasswordCollector
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.davinci.plugin.CollectorFactory
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectorRegistryTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private lateinit var collectorRegistry: CollectorRegistry

    @BeforeTest
    fun setup() {
        collectorRegistry = CollectorRegistry()
    }

    @AfterTest
    fun reset() {
        CollectorFactory.reset()
    }

    @TestRailCase(21279)
    @Test
    fun `should register Collector`() {
        collectorRegistry.initialize()

        val jsonArray = buildJsonArray {
            add(buildJsonObject { put("type", "TEXT") })
            add(buildJsonObject { put("type", "PASSWORD") })
            add(buildJsonObject { put("type", "SUBMIT_BUTTON") })
            add(buildJsonObject { put("type", "FLOW_BUTTON") })
        }

        val collectors = CollectorFactory.collector(jsonArray)
        assertEquals(TextCollector::class.java, collectors[0]::class.java)
        assertEquals(PasswordCollector::class.java, collectors[1]::class.java)
        assertEquals(SubmitCollector::class.java, collectors[2]::class.java)
        assertEquals(FlowCollector::class.java, collectors[3]::class.java)
    }

    @TestRailCase(21280)
    @Test
    fun `should ignore unknown Collector`() {
        collectorRegistry.initialize()

        val jsonArray = buildJsonArray {
            add(buildJsonObject { put("type", "TEXT") })
            add(buildJsonObject { put("type", "PASSWORD") })
            add(buildJsonObject { put("type", "SUBMIT_BUTTON") })
            add(buildJsonObject { put("type", "FLOW_BUTTON") })
            add(buildJsonObject { put("type", "UNKNOWN") })
        }

        val collectors = CollectorFactory.collector(jsonArray)
        assertEquals(4, collectors.size)
    }


}