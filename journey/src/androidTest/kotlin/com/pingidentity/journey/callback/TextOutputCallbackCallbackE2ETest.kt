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

class TextOutputCallbackCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        TREE = "TextOutputCallbackTest"
    }

    @Test
    fun textOutputCallbackTest() = runTest {
        var node = defaultJourney.start(TREE)  as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        assertEquals(4, node.callbacks.size)

        var callback = node.callbacks[0] as TextOutputCallback
        assertEquals("TextOutput Type 0 (INFO)", callback.message)
        assertEquals(TextOutputCallback.INFORMATION, callback.messageType)

        callback = node.callbacks[1] as TextOutputCallback
        assertEquals("TextOutput Type 1 (WARNING)", callback.message)
        assertEquals(TextOutputCallback.WARNING, callback.messageType)

        callback = node.callbacks[2] as TextOutputCallback
        assertEquals("TextOutput Type 2 (ERROR)", callback.message)
        assertEquals(TextOutputCallback.ERROR, callback.messageType)

        // ToDo: Align the "Type 4" value later...
        callback = node.callbacks[3] as TextOutputCallback
        assertEquals("TextOutput Type 4 (SCRIPT)", callback.message)
        assertEquals(4, callback.messageType)

        val result = node.next()

        assertTrue(result is SuccessNode)
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}