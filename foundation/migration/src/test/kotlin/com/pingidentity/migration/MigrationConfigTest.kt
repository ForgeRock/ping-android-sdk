/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.migration

import com.pingidentity.logger.Logger
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MigrationConfigTest {

    @Test
    fun `test MigrationStepResult enum values`() {
        assertEquals(3, MigrationStepResult.entries.size)
        assertEquals(MigrationStepResult.CONTINUE, MigrationStepResult.valueOf("CONTINUE"))
        assertEquals(MigrationStepResult.RERUN, MigrationStepResult.valueOf("RERUN"))
        assertEquals(MigrationStepResult.ABORT, MigrationStepResult.valueOf("ABORT"))
    }

    @Test
    fun `test MigrationStep factory function creates step with description`() = runTest {
        val description = "Test migration step"
        val mockContext = mockk<ExecutionContext>()

        val step = MigrationStep(description) { MigrationStepResult.CONTINUE }

        assertEquals(description, step.description)
        assertEquals(MigrationStepResult.CONTINUE, step.execute(mockContext))
    }

    @Test
    fun `test MigrationStep factory function allows description modification`() {
        val originalDescription = "Original description"
        val newDescription = "Updated description"

        val step = MigrationStep(originalDescription) { MigrationStepResult.CONTINUE }
        step.description = newDescription

        assertEquals(newDescription, step.description)
    }

    @Test
    fun `test MigrationStep factory function executes with different results`() = runTest {
        val mockContext = mockk<ExecutionContext>()

        val continueStep = MigrationStep("Continue step") { MigrationStepResult.CONTINUE }
        val rerunStep = MigrationStep("Rerun step") { MigrationStepResult.RERUN }
        val abortStep = MigrationStep("Abort step") { MigrationStepResult.ABORT }

        assertEquals(MigrationStepResult.CONTINUE, continueStep.execute(mockContext))
        assertEquals(MigrationStepResult.RERUN, rerunStep.execute(mockContext))
        assertEquals(MigrationStepResult.ABORT, abortStep.execute(mockContext))
    }

    @Test
    fun `test MigrationConfig default configuration`() {
        val config = MigrationConfig()

        assertNotNull(config.logger)
        assertTrue(config.steps.isEmpty())
    }

    @Test
    fun `test MigrationConfig custom logger`() {
        val mockLogger = mockk<Logger>()
        val config = MigrationConfig()
        config.logger = mockLogger

        assertEquals(mockLogger, config.logger)
    }

    @Test
    fun `test MigrationConfig step with description and action`() = runTest {
        val config = MigrationConfig()
        val description = "Test step description"
        val mockContext = mockk<ExecutionContext>()

        config.step(description) { MigrationStepResult.CONTINUE }

        assertEquals(1, config.steps.size)
        assertEquals(description, config.steps[0].description)
        assertEquals(MigrationStepResult.CONTINUE, config.steps[0].execute(mockContext))
    }

    @Test
    fun `test MigrationConfig step without description uses default`() = runTest {
        val config = MigrationConfig()
        val mockContext = mockk<ExecutionContext>()

        config.step { MigrationStepResult.RERUN }

        assertEquals(1, config.steps.size)
        assertEquals("Migration step 1", config.steps[0].description)
        assertEquals(MigrationStepResult.RERUN, config.steps[0].execute(mockContext))
    }

    @Test
    fun `test MigrationConfig step with MigrationStep object`() = runTest {
        val config = MigrationConfig()
        val mockContext = mockk<ExecutionContext>()
        val customStep = MigrationStep("Custom step") { MigrationStepResult.ABORT }

        config.step(customStep)

        assertEquals(1, config.steps.size)
        assertEquals("Custom step", config.steps[0].description)
        assertEquals(MigrationStepResult.ABORT, config.steps[0].execute(mockContext))
    }

    @Test
    fun `test MigrationConfig multiple steps with correct numbering`() = runTest {
        val config = MigrationConfig()
        val mockContext = mockk<ExecutionContext>()

        config.step { MigrationStepResult.CONTINUE } // Step 1
        config.step { MigrationStepResult.RERUN }    // Step 2
        config.step { MigrationStepResult.ABORT }    // Step 3

        assertEquals(3, config.steps.size)
        assertEquals("Migration step 1", config.steps[0].description)
        assertEquals("Migration step 2", config.steps[1].description)
        assertEquals("Migration step 3", config.steps[2].description)

        assertEquals(MigrationStepResult.CONTINUE, config.steps[0].execute(mockContext))
        assertEquals(MigrationStepResult.RERUN, config.steps[1].execute(mockContext))
        assertEquals(MigrationStepResult.ABORT, config.steps[2].execute(mockContext))
    }

    @Test
    fun `test MigrationConfig mixed step types`() = runTest {
        val config = MigrationConfig()
        val mockContext = mockk<ExecutionContext>()
        val customStep = MigrationStep("Custom step") { MigrationStepResult.ABORT }

        config.step("Named step") { MigrationStepResult.CONTINUE }
        config.step { MigrationStepResult.RERUN }
        config.step(customStep)

        assertEquals(3, config.steps.size)
        assertEquals("Named step", config.steps[0].description)
        assertEquals("Migration step 2", config.steps[1].description)
        assertEquals("Custom step", config.steps[2].description)

        assertEquals(MigrationStepResult.CONTINUE, config.steps[0].execute(mockContext))
        assertEquals(MigrationStepResult.RERUN, config.steps[1].execute(mockContext))
        assertEquals(MigrationStepResult.ABORT, config.steps[2].execute(mockContext))
    }

    @Test
    fun `test MigrationStep interface implementation`() = runTest {
        val mockContext = mockk<ExecutionContext>()

        val customStep = object : MigrationStep {
            override var description = "Custom implementation"
            override suspend fun execute(context: ExecutionContext): MigrationStepResult {
                return MigrationStepResult.CONTINUE
            }
        }

        assertEquals("Custom implementation", customStep.description)
        assertEquals(MigrationStepResult.CONTINUE, customStep.execute(mockContext))

        // Test description modification
        customStep.description = "Modified description"
        assertEquals("Modified description", customStep.description)
    }

    @Test
    fun `test ExecutionContext is passed correctly to step actions`() = runTest {
        val config = MigrationConfig()
        val mockContext = mockk<ExecutionContext>()
        var capturedContext: ExecutionContext? = null

        config.step("Context test") {
            capturedContext = this
            MigrationStepResult.CONTINUE
        }

        config.steps[0].execute(mockContext)
        assertEquals(mockContext, capturedContext)
    }

    @Test
    fun `test step numbering continues correctly with mixed additions`() {
        val config = MigrationConfig()

        // Add steps in different ways
        config.step { MigrationStepResult.CONTINUE }  // Should be "Migration step 1"
        config.step("Custom name") { MigrationStepResult.RERUN }
        config.step { MigrationStepResult.ABORT }     // Should be "Migration step 3"

        assertEquals("Migration step 1", config.steps[0].description)
        assertEquals("Custom name", config.steps[1].description)
        assertEquals("Migration step 3", config.steps[2].description)
    }
}
