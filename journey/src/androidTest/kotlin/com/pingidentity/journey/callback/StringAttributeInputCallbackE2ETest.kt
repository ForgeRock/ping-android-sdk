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

class StringAttributeInputCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        tree = "StringAttributeInputCallbackTest"
    }

    @Test
    fun stringAttributeInputCallbackTest() = runTest {
        var node = defaultJourney.start(tree)  as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        assertEquals(3, node.callbacks.size)
        val mail = node.callbacks[0] as StringAttributeInputCallback
        val givenName = node.callbacks[1] as StringAttributeInputCallback
        val sn = node.callbacks[2] as StringAttributeInputCallback

        assertEquals("mail", mail.name)
        assertEquals("Email Address", mail.prompt)
        assertEquals(true, mail.required)
        assertEquals("{}", mail.policies.toString())
        assertTrue(mail.failedPolicies.isEmpty())
        assertEquals(false, mail.validateOnly)
        mail.value = "test@mail.com"

        assertEquals("givenName", givenName.name)
        assertEquals("First Name", givenName.prompt)
        assertEquals(true, givenName.required)
        assertEquals("{}", givenName.policies.toString())
        assertTrue(givenName.failedPolicies.isEmpty())
        assertEquals(false, givenName.validateOnly)
        givenName.value = "Given"

        assertEquals("sn", sn.name)
        assertEquals("Last Name", sn.prompt)
        assertEquals(true, sn.required)
        assertEquals("{}", sn.policies.toString())
        assertTrue(sn.failedPolicies.isEmpty())
        assertEquals(false, sn.validateOnly)
        sn.value = "Lastname"

        val result = node.next()
        assertTrue(result is SuccessNode)   // This should be the final node
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}