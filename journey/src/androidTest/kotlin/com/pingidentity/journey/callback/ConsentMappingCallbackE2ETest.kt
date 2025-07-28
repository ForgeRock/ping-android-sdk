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

class ConsentMappingCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        tree = "ConsentMappingCallbackTest"
    }

    @Test
    fun consentMappingCallbackTest() = runTest {
        var node = defaultJourney.start(tree)  as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val consentMappingCallback = node.callbacks.first() as ConsentMappingCallback

        assertEquals("Actual Profile", consentMappingCallback.accessLevel)
        assertEquals("Identity Mapping", consentMappingCallback.displayName)
        assertNotNull(consentMappingCallback.icon)
        assertTrue(consentMappingCallback.isRequired)
        assertEquals("Test", consentMappingCallback.message)
        assertEquals("managedUser_managedUser", consentMappingCallback.name)

        consentMappingCallback.accept = true
        val result = node.next()
        assertTrue(result is SuccessNode)
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}