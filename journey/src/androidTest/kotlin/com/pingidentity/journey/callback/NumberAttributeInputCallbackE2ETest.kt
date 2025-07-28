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

class NumberAttributeInputCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        tree = "NumberAttributeInputCallbackTest"
    }

    @Test
    fun numberAttributeInputCallbackTest() = runTest {
        var node = defaultJourney.start(tree)  as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val callback = node.callbacks.first() as NumberAttributeInputCallback
        assertTrue(callback.name.contains("age"))
        assertEquals("How old are you?", callback.prompt)
        assertEquals(true, callback.required)
        assertEquals("{}", callback.policies.toString())
        assertTrue(callback.failedPolicies.isEmpty())

        // Set the value to 30.0 and continue
        callback.value = 30.0

        val result = node.next()
        assertTrue(result is SuccessNode)
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}
