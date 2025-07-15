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
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ValidatePasswordCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        TREE = "ValidatedPasswordCallbackTest"
    }

    @Test
    fun validatedPasswordCallbackTest() = runTest {
        var node = defaultJourney.start(TREE) as ContinueNode

        assertEquals(1, node.callbacks.size)
        var validatedPasswordCallback = node.callbacks.first() as ValidatedPasswordCallback

        assertEquals("Password", validatedPasswordCallback.prompt)
        assertFalse(validatedPasswordCallback.validateOnly)
        assertTrue(validatedPasswordCallback.policies.isNotEmpty())
        assertTrue(validatedPasswordCallback.policies.keys.contains("policyRequirements"))
        assertTrue(validatedPasswordCallback.policies.keys.contains("fallbackPolicies"))
        assertTrue(validatedPasswordCallback.policies.keys.contains("name"))
        assertTrue(validatedPasswordCallback.policies.keys.contains("policies"))
        assertTrue(validatedPasswordCallback.policies.keys.contains("conditionalPolicies"))
        assertEquals(0,validatedPasswordCallback.failedPolicies.size)

        // Try to enter an empty password. This should cause the validation policy to fail...
        validatedPasswordCallback.password = ""

        node = node.next() as ContinueNode

        assertEquals(1, node.callbacks.size)
        validatedPasswordCallback = node.callbacks.first() as ValidatedPasswordCallback

        assertEquals(1, validatedPasswordCallback.failedPolicies.size)
        assertTrue(validatedPasswordCallback.failedPolicies.any { it.policyRequirement == "LENGTH_BASED" })

        // Now try with short password. This should also fail...
        validatedPasswordCallback.password = "123"
        node = node.next() as ContinueNode

        // We expect the same callback (ValidatedPasswordCallback) to be returned
        assertEquals(1, node.callbacks.size)
        validatedPasswordCallback = node.callbacks.first() as ValidatedPasswordCallback

        assertEquals(1, validatedPasswordCallback.failedPolicies.size)
        assertTrue(validatedPasswordCallback.failedPolicies.any { it.policyRequirement == "LENGTH_BASED" })

        // Finally, enter a valid password.
        validatedPasswordCallback.password = "ForgeR0cks1234!"
        node = node.next() as ContinueNode

        // The journey should now continue to the next node
        assertEquals(2, node.callbacks.size)
        node.handleLoginCallbacks()

        val result = node.next()
        assertTrue(result is SuccessNode)
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}