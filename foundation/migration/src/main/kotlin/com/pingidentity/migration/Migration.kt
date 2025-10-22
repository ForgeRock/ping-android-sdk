/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.migration

import android.content.Context
import com.pingidentity.logger.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A migration framework that executes a series of migration steps in sequence.
 *
 * The Migration class provides a structured way to perform data migration operations
 * with progress tracking, error handling, and step control flow. Each migration step
 * can decide whether to continue to the next step, rerun the current step, or abort
 * the entire migration process.
 *
 * ## Key Features:
 * - **Sequential Execution**: Steps are executed in the order they are defined
 * - **Progress Tracking**: Emits progress events throughout the migration process
 * - **Error Handling**: Captures and reports errors with step context
 * - **Step Control Flow**: Steps can control execution flow (continue, rerun, abort)
 * - **Execution Context**: Provides shared state and utilities across steps
 *
 * ## Usage:
 * ```kotlin
 * val migration = Migration {
 *     logger = MyLogger()
 *
 *     step("Initialize database") {
 *         // Initialize database schema
 *         MigrationStepResult.CONTINUE
 *     }
 *
 *     step("Migrate user data") {
 *         // Migrate user data with retry logic
 *         if (migrationSuccessful) {
 *             MigrationStepResult.CONTINUE
 *         } else {
 *             MigrationStepResult.RERUN
 *         }
 *     }
 *
 *     step("Clean up old data") {
 *         // Clean up old data
 *         MigrationStepResult.CONTINUE
 *     }
 * }
 *
 * migration.migrate(context).collect { progress ->
 *     when (progress) {
 *         is MigrationProgress.Started -> println("Migration started")
 *         is MigrationProgress.InProgress -> println("Step ${progress.currentStep}/${progress.totalSteps}: ${progress.message}")
 *         is MigrationProgress.StepCompleted -> println("Completed: ${progress.step.description}")
 *         is MigrationProgress.Success -> println("Migration completed: ${progress.message}")
 *         is MigrationProgress.Error -> println("Migration failed: ${progress.error.message}")
 *     }
 * }
 * ```
 *
 * @param config The migration configuration containing steps and settings
 * @see MigrationConfig
 * @see MigrationProgress
 * @see MigrationStep
 */
class Migration(private val config: MigrationConfig) {

    companion object {
        /**
         * Creates a new Migration instance using a configuration DSL.
         *
         * This operator function allows for convenient migration creation using
         * a builder pattern with Kotlin DSL syntax.
         *
         * @param block Configuration block for setting up migration steps and options
         * @return A configured Migration instance
         *
         * Example:
         * ```kotlin
         * val migration = Migration {
         *     step("First step") { MigrationStepResult.CONTINUE }
         *     step("Second step") { MigrationStepResult.CONTINUE }
         * }
         * ```
         */
        operator fun invoke(block: MigrationConfig.() -> Unit = {}) =
            Migration(MigrationConfig().apply(block))
    }

    /**
     * Executes the migration process and emits progress events.
     *
     * This method runs all configured migration steps sequentially, emitting
     * progress events to track the migration status. Each step's result determines
     * the next action:
     *
     * - **CONTINUE**: Proceed to the next step
     * - **RERUN**: Execute the current step again
     * - **ABORT**: Terminate migration successfully (no error)
     *
     * The migration process follows this sequence:
     * 1. Emit [MigrationProgress.Started]
     * 2. For each step:
     *    - Emit [MigrationProgress.InProgress] before execution
     *    - Execute the step
     *    - Emit [MigrationProgress.StepCompleted] after successful execution
     *    - Handle the step result (continue, rerun, or abort)
     * 3. Emit [MigrationProgress.Success] when all steps complete
     * 4. Emit [MigrationProgress.Error] if any step throws an exception
     *
     * @param context Android context for migration operations
     * @return Flow of [MigrationProgress] events tracking migration status
     *
     * @see MigrationProgress
     * @see MigrationStepResult
     * @see ExecutionContext
     */
    fun migrate(context: Context): Flow<MigrationProgress> = flow {
        val executionContext = ExecutionContext(context,  logger = config.logger)
        emit(MigrationProgress.Started)

        val totalSteps = config.steps.size
        var currentStepIndex = 0

        while (currentStepIndex < totalSteps) {
            val migrationStep = config.steps[currentStepIndex]
            val currentStep = currentStepIndex + 1

            emit(
                MigrationProgress.InProgress(
                    currentStep = currentStep,
                    step = migrationStep,
                    totalSteps = totalSteps,
                    message = migrationStep.description
                )
            )

            try {
                val result = migrationStep.execute(executionContext)
                emit(MigrationProgress.StepCompleted(migrationStep))

                when (result) {
                    MigrationStepResult.CONTINUE -> {
                        currentStepIndex++
                    }

                    MigrationStepResult.RERUN -> {
                        // Stay on the same step, will rerun in next iteration
                    }

                    MigrationStepResult.ABORT -> {
                        // Terminate migration successfully
                        break
                    }
                }
            } catch (error: Throwable) {
                emit(MigrationProgress.Error(error, migrationStep))
                return@flow
            }
        }

        emit(MigrationProgress.Success("Migration completed successfully"))
    }
}

/**
 * Execution context for migration steps providing access to Android context,
 * logging, and shared state across steps.
 *
 * The ExecutionContext serves as a container for utilities and state that
 * migration steps may need during execution. It provides:
 *
 * - **Android Context**: For accessing system services and resources
 * - **Logger**: For consistent logging across migration steps
 * - **Shared State**: For passing data between migration steps
 *
 * ## State Management:
 * The state map allows steps to store and retrieve data that may be needed
 * by subsequent steps:
 *
 * ```kotlin
 * step("Load configuration") {
 *     val config = loadConfiguration()
 *     state["config"] = config
 *     MigrationStepResult.CONTINUE
 * }
 *
 * step("Apply configuration") {
 *     val config = getValue<Configuration>("config")
 *     if (config != null) {
 *         applyConfiguration(config)
 *         MigrationStepResult.CONTINUE
 *     } else {
 *         logger.e("Configuration not found in state")
 *         throw IllegalStateException("Missing configuration")
 *     }
 * }
 * ```
 *
 * @param context Android context for system access
 * @param logger Logger instance for consistent logging
 * @param state Mutable map for sharing data between migration steps
 */
class ExecutionContext(
    val context: Context,
    val logger: Logger = Logger.logger,
    val state: MutableMap<String, Any> = mutableMapOf()
) {
    /**
     * Retrieves a typed value from the execution state.
     *
     * This method provides type-safe access to values stored in the execution
     * state map. It uses reified generics to ensure type safety at runtime.
     *
     * @param T The expected type of the value
     * @param key The state key to retrieve
     * @return The value cast to type T, or null if the key doesn't exist or type doesn't match
     *
     * Example:
     * ```kotlin
     * // Store a value
     * state["userCount"] = 42
     *
     * // Retrieve with type safety
     * val count: Int? = getValue<Int>("userCount") // Returns 42
     * val invalidType: String? = getValue<String>("userCount") // Returns null
     * ```
     */
    inline fun <reified T> getValue(key: String): T? {
        val value = state[key]
        return if (value is T) value else null
    }
}
