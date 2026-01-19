/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.BaseJourneyTest
import com.pingidentity.journey.module.session
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.protect.journey.PingOneProtectInitializeCallback
import com.pingidentity.testrail.TestRailCase
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class PingOneProtectInitializeCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        tree = "TEST_PING_ONE_PROTECT_INITIALIZE"
    }

    @Test
    @TestRailCase(32272)
    fun testProtectInitializeDefaults() = runTest {
        var node = defaultJourney.start(tree)  as ContinueNode

        // Select test from the choice callback
        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("init-default")
        node = node.next() as ContinueNode

        // Handle the PingOneProtectInitializeCallback
        val pingOneProtectInitializeCallback = node.callbacks.first() as PingOneProtectInitializeCallback

        assertTrue(pingOneProtectInitializeCallback.envId != "")
        assertFalse(pingOneProtectInitializeCallback.agentIdentification)
        assertEquals(0, pingOneProtectInitializeCallback.agentTimeout)
        assertEquals(0, pingOneProtectInitializeCallback.agentPort)
        assertTrue(pingOneProtectInitializeCallback.customHost == "")
        assertTrue(pingOneProtectInitializeCallback.behavioralDataCollection)

        val initResult = pingOneProtectInitializeCallback.start()
        assertTrue(initResult.isSuccess)

        node = node.next() as ContinueNode
        // Handle the NameCallback
        node.handleLoginCallbacks()

        val result = node.next()
        assertTrue(result is SuccessNode)   // This should be the final node
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }

    @Test
    fun testProtectInitializeCustom() = runTest {
        var node = defaultJourney.start(tree)  as ContinueNode

        // Select test from the choice callback
        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("init-custom")
        node = node.next() as ContinueNode

        // Handle the PingOneProtectInitializeCallback
        val pingOneProtectInitializeCallback = node.callbacks.first() as PingOneProtectInitializeCallback

        assertTrue(pingOneProtectInitializeCallback.envId != "")
        assertTrue(pingOneProtectInitializeCallback.customHost.isEmpty())
        assertTrue(pingOneProtectInitializeCallback.agentIdentification)
        assertEquals(200, pingOneProtectInitializeCallback.agentTimeout)
        assertEquals(8089, pingOneProtectInitializeCallback.agentPort)
        assertFalse(pingOneProtectInitializeCallback.behavioralDataCollection)

        val initResult = pingOneProtectInitializeCallback.start()
        assertTrue(initResult.isSuccess)

        node = node.next() as ContinueNode
        // Handle the NameCallback
        node.handleLoginCallbacks()

        val result = node.next()
        assertTrue(result is SuccessNode)   // This should be the final node
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}