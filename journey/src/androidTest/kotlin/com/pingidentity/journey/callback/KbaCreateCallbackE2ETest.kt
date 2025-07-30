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

class KbaCreateCallbackE2ETest : BaseJourneyTest() {

    @Before
    fun setupTree() = runTest {
        tree = "KbaCreateCallbackTest"
    }

    @Test
    fun kbaCreateCallbackTest() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode

        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        assertEquals(2, node.callbacks.size)
        val firstQuestion = node.callbacks[0] as KbaCreateCallback
        assertEquals(2, firstQuestion.predefinedQuestions.size)
        firstQuestion.selectedQuestion = firstQuestion.predefinedQuestions[0]
        firstQuestion.selectedAnswer = "Yellow"
        assertTrue(firstQuestion.allowUserDefinedQuestions)
        assertEquals("Security questions", firstQuestion.prompt);

        val secondQuestion = node.callbacks[1] as KbaCreateCallback
        assertEquals(2, secondQuestion.predefinedQuestions.size)
        secondQuestion.selectedQuestion = "What city were you born in?"
        secondQuestion.selectedAnswer = "Plovdiv"
        assertTrue(secondQuestion.allowUserDefinedQuestions)
        assertEquals("Security questions", secondQuestion.prompt);

        val result = node.next()
        assertTrue(result is SuccessNode)
        result as SuccessNode
        logger.i("Session: ${result.session.value}")

        assertNotNull(result.session)
        assertNotNull(defaultJourney.session())
    }
}