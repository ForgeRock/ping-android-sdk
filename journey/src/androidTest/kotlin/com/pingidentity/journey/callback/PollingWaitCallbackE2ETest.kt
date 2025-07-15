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
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class PollingWaitCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        TREE = "PollingWaitCallbackTest"
    }

    @Test
    fun pollingWaitCallbackTest() = runTest {
        var node = defaultJourney.start(TREE)  as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val pollingWaitCallback = node.callbacks.first() as PollingWaitCallback

        assertEquals(10000, pollingWaitCallback.waitTime)
        assertEquals("Please Wait", pollingWaitCallback.message)

        val confirmationCallback = node.callbacks.last() as ConfirmationCallback
        assertTrue(confirmationCallback.prompt.isEmpty())
        assertEquals(ConfirmationCallback.INFORMATION, confirmationCallback.messageType)
        assertTrue(confirmationCallback.options.contains("Exit"))
        assertEquals(ConfirmationCallback.UNSPECIFIED_OPTION, confirmationCallback.optionType)
        assertEquals(ConfirmationCallback.YES_NO_OPTION, confirmationCallback.defaultOption)
        confirmationCallback.selectedIndex = ConfirmationCallback.YES

        val result = node.next()
        assertTrue(result is SuccessNode)   // This should be the final node
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}