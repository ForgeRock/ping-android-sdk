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

class ChoiceCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        tree = "ChoiceCallbackTest"
    }

    @Test
    fun choiceCallbackTest() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        // Handle choice callback
        val choiceCallback = node.callbacks.first() as ChoiceCallback

        // Assert that the choice callback properties are set correctly
        assertEquals("Choice", choiceCallback.prompt)
        assertEquals(0, choiceCallback.defaultChoice)
        assertEquals(2, choiceCallback.choices.size)
        assertTrue(choiceCallback.choices.contains("Yes"))
        assertTrue(choiceCallback.choices.contains("No"))

        choiceCallback.selectedIndex = 0 // Select "Yes"

        val result = node.next()
        assertTrue(result is SuccessNode)
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}