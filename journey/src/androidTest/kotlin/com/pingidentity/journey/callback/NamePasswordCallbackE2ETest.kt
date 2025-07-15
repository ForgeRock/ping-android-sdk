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

class NamePasswordCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        TREE = "NamePasswordCallbackTest"
    }

    @Test
    fun namePasswordCallbackTest() = runTest {
        var node = defaultJourney.start(TREE) as ContinueNode

        assertEquals(1, node.callbacks.size)
        val nameCallback = node.callbacks[0] as NameCallback
        assertEquals("User Name", nameCallback.prompt)
        nameCallback.name = USERNAME
        node = node.next() as ContinueNode

        assertEquals(1, node.callbacks.size)
        val passwordCallback = node.callbacks[0] as PasswordCallback
        assertEquals("Password", passwordCallback.prompt)
        passwordCallback.password  = PASSWORD

        val result = node.next()
        assertTrue(result is SuccessNode)
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}