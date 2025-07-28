/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.BaseJourneyTest
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.orchestrate.ContinueNode
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SuspendedTextCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        tree = "SuspendedTextCallbackTest"
    }

    @Test
    fun suspendedTextCallbackTest() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        // Handle the SuspendedTextCallback
        assertEquals(1, node.callbacks.size)
        val suspendedTextOutputCallback = node.callbacks.first() as SuspendedTextOutputCallback

        assertTrue(suspendedTextOutputCallback.message.contains("An email has been sent to the address you entered"))
        assertEquals(TextOutputCallback.INFORMATION, suspendedTextOutputCallback.messageType)

        /// And that's it...
    }
}