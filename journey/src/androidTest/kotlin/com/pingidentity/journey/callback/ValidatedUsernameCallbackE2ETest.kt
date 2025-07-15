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

class ValidatedUsernameCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        TREE = "ValidatedUsernameCallbackTest"
    }

    @Test
    fun validatedUsernameCallbackTest() = runTest {
        var node = defaultJourney.start(TREE) as ContinueNode

        assertEquals(1, node.callbacks.size)
        var validatedUsernameCallback = node.callbacks.first() as ValidatedUsernameCallback

        assertEquals("Username", validatedUsernameCallback.prompt)
        assertTrue(validatedUsernameCallback.policies.isNotEmpty())
        assertTrue(validatedUsernameCallback.policies.keys.contains("policyRequirements"))
        assertTrue(validatedUsernameCallback.policies.keys.contains("fallbackPolicies"))
        assertTrue(validatedUsernameCallback.policies.keys.contains("name"))
        assertTrue(validatedUsernameCallback.policies.keys.contains("policies"))
        assertTrue(validatedUsernameCallback.policies.keys.contains("conditionalPolicies"))
        assertEquals(0,validatedUsernameCallback.failedPolicies.size)

        // Try to enter username of already existing user.
        // This should cause the validation policy to fail...
        validatedUsernameCallback.username = USERNAME
        node = node.next() as ContinueNode

        // We expect the same callback (ValidatedUsernameCallback) to be returned
        assertEquals(1, node.callbacks.size)
        validatedUsernameCallback= node.callbacks.first() as ValidatedUsernameCallback

        assertEquals(1, validatedUsernameCallback.failedPolicies.size)
        assertTrue(validatedUsernameCallback.failedPolicies.any { it.policyRequirement == "VALID_USERNAME" })
        assertFalse(validatedUsernameCallback.failedPolicies.any { it.policyRequirement == "CANNOT_CONTAIN_CHARACTERS" })

        // Now try to enter a username with invalid character "/". This should also fail...
        validatedUsernameCallback.username = "invalid/characters"
        node = node.next() as ContinueNode

        // We expect the same callback (ValidatedUsernameCallback) to be returned
        assertEquals(1, node.callbacks.size)
        validatedUsernameCallback= node.callbacks.first() as ValidatedUsernameCallback

        assertEquals(1, validatedUsernameCallback.failedPolicies.size)
        assertFalse(validatedUsernameCallback.failedPolicies.any { it.policyRequirement == "VALID_USERNAME" })
        assertTrue(validatedUsernameCallback.failedPolicies.any { it.policyRequirement == "CANNOT_CONTAIN_CHARACTERS" })

        // Finally, enter a valid username
        validatedUsernameCallback.username = "username" + System.currentTimeMillis() // Ensure uniqueness
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