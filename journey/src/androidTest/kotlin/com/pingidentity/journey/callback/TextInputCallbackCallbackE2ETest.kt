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

class TextInputCallbackCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        TREE = "TextInputCallbackTest"
    }

    @Test
    fun textInputCallbackTest() = runTest {
        var node = defaultJourney.start(TREE)  as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val callback = node.callbacks.first() as TextInputCallback

        assertEquals("What is your username?", callback.prompt)
        assertEquals("ForgerRocker", callback.defaultText)

        // Set text value and continue
        callback.text = USERNAME

        // This step here is to ensure that the SDK correctly sets the value in the TextInputCallback...
        // The values entered in the NameCallback and  TextInputCallback above should match for "success"
        node = node.next() as ContinueNode
        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        kotlin.test.assertEquals("Success", textOutputCallback.message)

        val result = node.next()

        assertTrue(result is SuccessNode)
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}