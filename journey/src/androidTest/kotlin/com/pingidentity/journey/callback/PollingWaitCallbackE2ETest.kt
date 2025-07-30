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
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.SuccessNode
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class PollingWaitCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        tree = "PollingWaitCallbackTest"
    }

    @Test
    fun pollingWaitCallbackExitTest() = runTest {
        var node = defaultJourney.start(tree)  as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val pollingWaitCallback = node.callbacks.first() as PollingWaitCallback

        assertEquals(5000, pollingWaitCallback.waitTime)
        assertEquals("Please Wait", pollingWaitCallback.message)

        val confirmationCallback = node.callbacks.last() as ConfirmationCallback
        assertTrue(confirmationCallback.prompt.isEmpty())
        assertEquals(ConfirmationCallback.INFORMATION, confirmationCallback.messageType)
        assertTrue(confirmationCallback.options.contains("Exit"))
        assertEquals(ConfirmationCallback.UNSPECIFIED_OPTION, confirmationCallback.optionType)
        assertEquals(ConfirmationCallback.YES_NO_OPTION, confirmationCallback.defaultOption)
        // Set the selected index to YES to simulate user "exiting" the polling wait... (see SDKS-4277)
        confirmationCallback.selectedIndex = ConfirmationCallback.YES

        val result = node.next()
        assertTrue(result is SuccessNode)   // This should be the final node
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }

    @Test
    fun pollingWaitCallbackTimeoutTest() = runTest (timeout = 10.seconds) {
        var node = defaultJourney.start(tree)  as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val pollingWaitCallback = node.callbacks.first() as PollingWaitCallback

        assertEquals(5000, pollingWaitCallback.waitTime)
        assertEquals("Please Wait", pollingWaitCallback.message)

        val confirmationCallback = node.callbacks.last() as ConfirmationCallback
        assertTrue(confirmationCallback.options.contains("Exit"))

        // "next" will return the same node, until the polling wait times out...
        val result = node.next()
        assertTrue(result is ContinueNode)
        assertTrue(node.callbacks.first() is PollingWaitCallback)
        assertTrue(node.callbacks.last() is ConfirmationCallback)

        // Simulate the polling wait timeout
        Thread.sleep(6000) // Wait longer than the polling wait time
        val timeoutResult = node.next()
        assertTrue(timeoutResult is ErrorNode) // Should return an error node after timeout

        assertEquals("Login failure", (timeoutResult as ErrorNode).message)
        assertNull(defaultJourney.session())
    }
}