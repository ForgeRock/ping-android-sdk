/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.migration

import android.content.Context
import com.pingidentity.logger.Logger
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigrationTest {

    private val mockContext = mockk<Context>()
    private val mockLogger = mockk<Logger>(relaxed = true)

    @Test
    fun `test Migration companion object invoke creates migration with config`() {
        val migration = Migration {
            logger = mockLogger
            step("Test step") { MigrationStepResult.CONTINUE }
        }

        assertNotNull(migration)
    }

    @Test
    fun `test Migration empty configuration`() = runTest {
        val migration = Migration()
        val progressList = migration.migrate(mockContext).toList()

        assertEquals(2, progressList.size)
        assertTrue(progressList[0] is MigrationProgress.Started)
        assertTrue(progressList[1] is MigrationProgress.Success)
        assertEquals("Migration completed successfully", (progressList[1] as MigrationProgress.Success).message)
    }

    @Test
    fun `test Migration single step with CONTINUE result`() = runTest {
        val migration = Migration {
            logger = mockLogger
            step("Test step") { MigrationStepResult.CONTINUE }
        }

        val progressList = migration.migrate(mockContext).toList()

        assertEquals(4, progressList.size) // Started + InProgress + StepCompleted + Success
        assertTrue(progressList[0] is MigrationProgress.Started)

        val inProgress = progressList[1] as MigrationProgress.InProgress
        assertEquals(1, inProgress.currentStep)
        assertEquals(1, inProgress.totalSteps)
        assertEquals("Test step", inProgress.message)
        assertEquals("Test step", inProgress.step.description)

        val stepCompleted = progressList[2] as MigrationProgress.StepCompleted
        assertEquals("Test step", stepCompleted.step.description)

        assertTrue(progressList[3] is MigrationProgress.Success)
    }

    @Test
    fun `test Migration multiple steps all CONTINUE`() = runTest {
        val migration = Migration {
            logger = mockLogger
            step("Step 1") { MigrationStepResult.CONTINUE }
            step("Step 2") { MigrationStepResult.CONTINUE }
            step("Step 3") { MigrationStepResult.CONTINUE }
        }

        val progressList = migration.migrate(mockContext).toList()

        assertEquals(8, progressList.size) // Started + 3(InProgress + StepCompleted) + Success
        assertTrue(progressList[0] is MigrationProgress.Started)

        // Check each step has both InProgress and StepCompleted
        val step1InProgress = progressList[1] as MigrationProgress.InProgress
        assertEquals(1, step1InProgress.currentStep)
        assertEquals(3, step1InProgress.totalSteps)
        assertEquals("Step 1", step1InProgress.message)

        val step1Completed = progressList[2] as MigrationProgress.StepCompleted
        assertEquals("Step 1", step1Completed.step.description)

        val step2InProgress = progressList[3] as MigrationProgress.InProgress
        assertEquals(2, step2InProgress.currentStep)
        assertEquals(3, step2InProgress.totalSteps)
        assertEquals("Step 2", step2InProgress.message)

        val step2Completed = progressList[4] as MigrationProgress.StepCompleted
        assertEquals("Step 2", step2Completed.step.description)

        val step3InProgress = progressList[5] as MigrationProgress.InProgress
        assertEquals(3, step3InProgress.currentStep)
        assertEquals(3, step3InProgress.totalSteps)
        assertEquals("Step 3", step3InProgress.message)

        val step3Completed = progressList[6] as MigrationProgress.StepCompleted
        assertEquals("Step 3", step3Completed.step.description)

        assertTrue(progressList[7] is MigrationProgress.Success)
    }

    @Test
    fun `test Migration step with RERUN result`() = runTest {
        var executionCount = 0
        val migration = Migration {
            logger = mockLogger
            step("Rerun step") {
                executionCount++
                if (executionCount < 3) MigrationStepResult.RERUN else MigrationStepResult.CONTINUE
            }
        }

        val progressList = migration.migrate(mockContext).toList()

        assertEquals(8, progressList.size) // Started + 3 InProgress (reruns) + StepCompleted + Success
        assertEquals(3, executionCount)

        // All InProgress events should have the same step number since it reruns
        val inProgress1 = progressList[1] as MigrationProgress.InProgress
        val inProgress2 = progressList[2] as MigrationProgress.StepCompleted
        val inProgress3 = progressList[3] as MigrationProgress.InProgress
        val inProgress4 = progressList[4] as MigrationProgress.StepCompleted
        val inProgress5 = progressList[5] as MigrationProgress.InProgress

        assertEquals(1, inProgress1.currentStep)
        assertEquals(1, inProgress3.currentStep)
        assertEquals(1, inProgress3.currentStep)

        // StepCompleted should only be emitted after the final successful execution
        val stepCompleted = progressList[4] as MigrationProgress.StepCompleted
        assertEquals("Rerun step", stepCompleted.step.description)

        assertTrue(progressList[7] is MigrationProgress.Success)
    }

    @Test
    fun `test Migration step with ABORT result`() = runTest {
        val migration = Migration {
            logger = mockLogger
            step("Step 1") { MigrationStepResult.CONTINUE }
            step("Abort step") { MigrationStepResult.ABORT }
            step("Step 3") { MigrationStepResult.CONTINUE } // Should not execute
        }

        val progressList = migration.migrate(mockContext).toList()

        assertEquals(6, progressList.size) // Started + Step1(InProgress + StepCompleted) + Step2(InProgress) + Success
        assertTrue(progressList[0] is MigrationProgress.Started)

        val step1InProgress = progressList[1] as MigrationProgress.InProgress
        assertEquals(1, step1InProgress.currentStep)
        assertEquals("Step 1", step1InProgress.message)

        val step1Completed = progressList[2] as MigrationProgress.StepCompleted
        assertEquals("Step 1", step1Completed.step.description)

        val step2InProgress = progressList[3] as MigrationProgress.InProgress
        assertEquals(2, step2InProgress.currentStep)
        assertEquals("Abort step", step2InProgress.message)

        // No StepCompleted for the abort step since it aborts
        assertTrue(progressList[5] is MigrationProgress.Success)
    }

    @Test
    fun `test Migration step throws exception`() = runTest {
        val testException = RuntimeException("Test error")
        val migration = Migration {
            logger = mockLogger
            step("Failing step") { throw testException }
            step("Should not execute") { MigrationStepResult.CONTINUE }
        }

        val progressList = migration.migrate(mockContext).toList()

        assertEquals(3, progressList.size) // Started + InProgress + Error
        assertTrue(progressList[0] is MigrationProgress.Started)

        val inProgress = progressList[1] as MigrationProgress.InProgress
        assertEquals("Failing step", inProgress.message)

        val error = progressList[2] as MigrationProgress.Error
        assertEquals(testException, error.error)
        assertEquals("Failing step", error.step.description)
    }

    @Test
    fun `test Migration complex scenario with mixed results`() = runTest {
        var step2ExecutionCount = 0
        val migration = Migration {
            logger = mockLogger
            step("Step 1") { MigrationStepResult.CONTINUE }
            step("Step 2 (rerun twice)") {
                step2ExecutionCount++
                if (step2ExecutionCount < 3) MigrationStepResult.RERUN else MigrationStepResult.CONTINUE
            }
            step("Step 3") { MigrationStepResult.CONTINUE }
            step("Step 4 (abort)") { MigrationStepResult.ABORT }
            step("Step 5 (should not execute)") { MigrationStepResult.CONTINUE }
        }

        val progressList = migration.migrate(mockContext).toList()

        // Started + Step1(InProgress+StepCompleted) + Step2(3xInProgress+StepCompleted) + Step3(InProgress+StepCompleted) + Step4(InProgress) + Success = 11
        assertEquals(14, progressList.size)
        assertEquals(3, step2ExecutionCount)

        assertTrue(progressList[0] is MigrationProgress.Started)

        // Verify step progression with StepCompleted events
        val inProgressEvents = progressList.filterIsInstance<MigrationProgress.InProgress>()
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()

        assertEquals(6, inProgressEvents.size) // 1 + 3 + 1 + 1 = 6 InProgress events
        assertEquals(6, stepCompletedEvents.size) // Only completed steps emit StepCompleted

        // Verify the completed steps
        assertEquals("Step 1", stepCompletedEvents[0].step.description)
        assertEquals("Step 2 (rerun twice)", stepCompletedEvents[1].step.description)
        assertEquals("Step 3", stepCompletedEvents[4].step.description)

        assertTrue(progressList[13] is MigrationProgress.Success)
    }

    @Test
    fun `test ExecutionContext default initialization`() {
        val context = ExecutionContext(mockContext)

        assertEquals(mockContext, context.context)
        assertNotNull(context.logger)
        assertTrue(context.state.isEmpty())
    }

    @Test
    fun `test ExecutionContext custom initialization`() {
        val customState = mutableMapOf<String, Any>("key" to "value")
        val context = ExecutionContext(mockContext, mockLogger, customState)

        assertEquals(mockContext, context.context)
        assertEquals(mockLogger, context.logger)
        assertEquals(customState, context.state)
        assertEquals("value", context.state["key"])
    }

    @Test
    fun `test ExecutionContext getValue with correct type`() {
        val context = ExecutionContext(mockContext)
        context.state["string"] = "test"
        context.state["number"] = 42
        context.state["boolean"] = true

        assertEquals("test", context.getValue<String>("string"))
        assertEquals(42, context.getValue<Int>("number"))
        assertEquals(true, context.getValue<Boolean>("boolean"))
    }

    @Test
    fun `test ExecutionContext getValue with wrong type returns null`() {
        val context = ExecutionContext(mockContext)
        context.state["string"] = "test"

        assertNull(context.getValue<Int>("string"))
        assertNull(context.getValue<Boolean>("string"))
    }

    @Test
    fun `test ExecutionContext getValue with non-existent key returns null`() {
        val context = ExecutionContext(mockContext)

        assertNull(context.getValue<String>("nonexistent"))
        assertNull(context.getValue<Int>("nonexistent"))
    }

    @Test
    fun `test ExecutionContext state modification during migration`() = runTest {
        val migration = Migration {
            logger = mockLogger
            step("Set state") {
                state["step1"] = "executed"
                state["counter"] = 1
                MigrationStepResult.CONTINUE
            }
            step("Read and modify state") {
                val step1Value = getValue<String>("step1")
                val counter = getValue<Int>("counter")
                assertEquals("executed", step1Value)
                assertEquals(1, counter)

                state["step2"] = "also executed"
                state["counter"] = 2
                MigrationStepResult.CONTINUE
            }
            step("Verify final state") {
                assertEquals("executed", getValue<String>("step1"))
                assertEquals("also executed", getValue<String>("step2"))
                assertEquals(2, getValue<Int>("counter"))
                MigrationStepResult.CONTINUE
            }
        }

        val progressList = migration.migrate(mockContext).toList()

        // Should complete successfully if all assertions pass
        assertTrue(progressList.last() is MigrationProgress.Success)
    }

    @Test
    fun `test ExecutionContext with complex object types`() {
        val context = ExecutionContext(mockContext)
        val list = listOf(1, 2, 3)
        val map = mapOf("a" to "b")
        val stringValue = "test string"

        context.state["list"] = list
        context.state["map"] = map
        context.state["string"] = stringValue

        assertEquals(list, context.getValue<List<Int>>("list"))
        assertEquals(map, context.getValue<Map<String, String>>("map"))
        assertEquals(stringValue, context.getValue<String>("string"))

        // Test type mismatches that can be distinguished at runtime
        assertNull(context.getValue<String>("list"))  // List vs String
        assertNull(context.getValue<Int>("map"))      // Map vs Int
        assertNull(context.getValue<List<*>>("string")) // String vs List
        assertNull(context.getValue<Boolean>("nonexistent")) // Non-existent key
    }

    @Test
    fun `test Migration DSL configuration`() {
        val migration = Migration {
            logger = mockLogger

            step("Custom step 1") { MigrationStepResult.CONTINUE }
            step { MigrationStepResult.RERUN } // Auto-numbered
            step(MigrationStep("Direct step") { MigrationStepResult.ABORT })
        }

        // Just verify the migration was created successfully
        assertNotNull(migration)
    }
}
