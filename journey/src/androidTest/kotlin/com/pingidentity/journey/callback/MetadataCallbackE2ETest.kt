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

class MetadataCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        tree = "MetadataCallbackTest"
    }

    @Test
    fun namePasswordCallbackTest() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        // Handle the callbacks. We should have a MetadataCallback and the ChoiceCallback at this point.
        assertEquals(2, node.callbacks.size)
        val metaCallback = node.callbacks.first() as MetadataCallback
        val choiceCallback = node.callbacks.last() as ChoiceCallback

        // Assert that the metadata callback contains the expected keys and values
        assertEquals(2, metaCallback.value.size)
        assertTrue(metaCallback.value.containsKey("username"))
        assertTrue(metaCallback.value.containsKey("custom"))

        assertEquals(USERNAME, metaCallback.value["username"].toString().replace("\"", ""))
        assertEquals("dummy value", metaCallback.value["custom"].toString().replace("\"", ""))

        // Select "Yes" in the ChoiceCallback and finish the journey
        assertTrue(choiceCallback.choices.contains("Yes"))
        assertTrue(choiceCallback.choices.contains("No"))
        choiceCallback.selectedIndex = 0

        val result = node.next()
        assertTrue(result is SuccessNode)
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}