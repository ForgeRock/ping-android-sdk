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

class ConfirmationCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        TREE = "ConfirmationCallbackTest"
    }

    @Test
    fun confirmationCallbackTest() = runTest {
        var node = defaultJourney.start(TREE)  as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        // Handle TextOutputCallback callback
        val textInputCallback = node.callbacks.first() as TextOutputCallback
        assertEquals("Test", textInputCallback.message)
        assertEquals(0, textInputCallback.messageType)

        // Handle ConfirmationCallback callback
        val confirmationCallback = node.callbacks.last() as ConfirmationCallback
        assertEquals("", confirmationCallback.prompt)
        assertEquals(1, confirmationCallback.defaultOption)
        assertEquals(0, confirmationCallback.messageType)
        assertEquals(-1, confirmationCallback.optionType)
        assertEquals(2, confirmationCallback.options.size)
        assertTrue(confirmationCallback.options.contains("Yes"))
        assertTrue(confirmationCallback.options.contains("No"))

        confirmationCallback.selectedIndex = 0 // Select "Yes"

        val result = node.next()
        assertTrue(result is SuccessNode)
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}