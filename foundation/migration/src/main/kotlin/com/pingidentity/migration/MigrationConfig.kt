/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.migration

import com.pingidentity.logger.Logger

/**
 * Represents the result of executing a migration step.
 *
 * This enum controls the flow of migration execution by indicating how the migration
 * framework should proceed after a step completes. Each result type affects the
 * migration differently:
 *
 * ## Usage Examples:
 *
 * **CONTINUE** - Normal progression:
 * ```kotlin
 * step("Initialize database") {
 *     database.initialize()
 *     MigrationStepResult.CONTINUE
 * }
 * ```
 *
 * **RERUN** - Retry logic:
 * ```kotlin
 * step("Download data") {
 *     val success = downloadData()
 *     if (success) {
 *         MigrationStepResult.CONTINUE
 *     } else {
 *         logger.w("Download failed, retrying...")
 *         MigrationStepResult.RERUN
 *     }
 * }
 * ```
 *
 * **ABORT** - Early termination:
 * ```kotlin
 * step("Check prerequisites") {
 *     if (prerequisitesMet()) {
 *         MigrationStepResult.CONTINUE
 *     } else {
 *         logger.i("Prerequisites not met, skipping migration")
 *         MigrationStepResult.ABORT
 *     }
 * }
 * ```
 */
enum class MigrationStepResult {
    /**
     * Continue to the next step in the migration sequence.
     * This is the normal result for successful step execution.
     */
    CONTINUE,

    /**
     * Rerun the current step without advancing to the next step.
     * Useful for implementing retry logic when a step may fail temporarily.
     * Be careful to avoid infinite loops by implementing proper retry limits.
     */
    RERUN,

    /**
     * Terminate the migration successfully without executing remaining steps.
     * The migration is considered completed successfully. Use this when
     * conditions indicate that further migration steps are not needed.
     */
    ABORT
}

/**
 * Represents a single migration step with its description and action.
 *
 * A MigrationStep encapsulates both the metadata (description) and the logic
 * (execute function) for a single migration operation. Steps are executed
 * sequentially by the Migration framework, and each step can control the
 * migration flow through its return value.
 *
 * ## Implementation Patterns:
 *
 * **Simple Step**:
 * ```kotlin
 * val step = MigrationStep("Clear cache") {
 *     clearApplicationCache()
 *     MigrationStepResult.CONTINUE
 * }
 * ```
 *
 * **Step with State Sharing**:
 * ```kotlin
 * val loadConfigStep = MigrationStep("Load configuration") {
 *     val config = loadConfiguration()
 *     state["config"] = config
 *     MigrationStepResult.CONTINUE
 * }
 *
 * val applyConfigStep = MigrationStep("Apply configuration") {
 *     val config = getValue<Configuration>("config")
 *     applyConfiguration(config)
 *     MigrationStepResult.CONTINUE
 * }
 * ```
 *
 * **Step with Error Handling**:
 * ```kotlin
 * val migrationStep = MigrationStep("Migrate user data") {
 *     try {
 *         migrateUserData()
 *         MigrationStepResult.CONTINUE
 *     } catch (e: Exception) {
 *         logger.e("Migration failed", e)
 *         throw MigrationException("Failed to migrate user data", e)
 *     }
 * }
 * ```
 *
 * @see ExecutionContext
 * @see MigrationStepResult
 */
interface MigrationStep {

    /**
     * The human-readable description of this migration step.
     *
     * This description is used in progress reporting and logging to help
     * identify which step is currently executing. It should be concise
     * but descriptive enough to understand the step's purpose.
     *
     * Examples:
     * - "Initialize database schema"
     * - "Migrate user preferences to new format"
     * - "Clean up temporary files"
     */
    var description: String

    /**
     * Executes the migration step with the provided execution context.
     *
     * This method contains the actual migration logic for the step. It has
     * access to the ExecutionContext which provides Android context, logging,
     * and shared state management capabilities.
     *
     * The method should return a MigrationStepResult to indicate how the
     * migration should proceed:
     * - CONTINUE: Move to the next step
     * - RERUN: Execute this step again
     * - ABORT: Terminate migration successfully
     *
     * If the step encounters an unrecoverable error, it should throw an
     * exception which will be caught by the Migration framework and result
     * in a MigrationProgress.Error being emitted.
     *
     * @param context The execution context providing Android context, logging, and state
     * @return MigrationStepResult indicating how the migration should proceed
     * @throws Exception if the step encounters an unrecoverable error
     */
    suspend fun execute(context: ExecutionContext): MigrationStepResult
}

/**
 * Factory function to create a MigrationStep instance with a description and action.
 *
 * This is a convenience function that creates an anonymous implementation of the
 * MigrationStep interface. It's typically used when defining migration steps
 * inline within a Migration configuration block.
 *
 * ## Usage Examples:
 *
 * **Direct Step Creation**:
 * ```kotlin
 * val step = MigrationStep("Initialize components") {
 *     initializeComponents()
 *     MigrationStepResult.CONTINUE
 * }
 * ```
 *
 * **Within Migration Configuration**:
 * ```kotlin
 * val migration = Migration {
 *     step("Clear old data") {
 *         clearOldData()
 *         MigrationStepResult.CONTINUE
 *     }
 *
 *     // You can also create steps separately and add them
 *     step(MigrationStep("Custom step") {
 *         performCustomMigration()
 *         MigrationStepResult.CONTINUE
 *     })
 * }
 * ```
 *
 * @param description A human-readable description of what this step does
 * @param collect The suspend function that implements the migration logic
 * @return A MigrationStep instance that can be executed by the Migration framework
 *
 * @see MigrationStep
 * @see ExecutionContext
 * @see MigrationStepResult
 */
fun MigrationStep(
    description: String,
    collect: suspend ExecutionContext.() -> MigrationStepResult
): MigrationStep {
    return object : MigrationStep {
        override var description = description
        override suspend fun execute(context: ExecutionContext) = collect(context)
    }
}

/**
 * Configuration class for setting up migration steps and options.
 *
 * MigrationConfig provides a DSL (Domain Specific Language) for configuring
 * migration operations. It allows you to define migration steps, configure
 * logging, and set up the execution sequence for data migration operations.
 *
 * The configuration is used by the Migration class to create and execute
 * migration workflows with proper progress tracking and error handling.
 *
 * ## Basic Usage:
 *
 * ```kotlin
 * val migration = Migration {
 *     // Configure logging (optional)
 *     logger = MyCustomLogger()
 *
 *     // Define migration steps
 *     step("Initialize database") {
 *         database.createTables()
 *         MigrationStepResult.CONTINUE
 *     }
 *
 *     step("Migrate user data") {
 *         migrateUsers()
 *         MigrationStepResult.CONTINUE
 *     }
 *
 *     step("Clean up old data") {
 *         cleanupOldFiles()
 *         MigrationStepResult.CONTINUE
 *     }
 * }
 * ```
 *
 * ## Advanced Usage with State Management:
 *
 * ```kotlin
 * val migration = Migration {
 *     step("Load configuration") {
 *         val config = loadMigrationConfig()
 *         state["migrationConfig"] = config
 *         logger.i("Configuration loaded: ${config.version}")
 *         MigrationStepResult.CONTINUE
 *     }
 *
 *     step("Validate prerequisites") {
 *         val config = getValue<MigrationConfig>("migrationConfig")
 *         if (config?.isValid() == true) {
 *             MigrationStepResult.CONTINUE
 *         } else {
 *             logger.w("Prerequisites not met, aborting migration")
 *             MigrationStepResult.ABORT
 *         }
 *     }
 * }
 * ```
 *
 * ## Retry Logic Example:
 *
 * ```kotlin
 * val migration = Migration {
 *     step("Download migration data") {
 *         val retryCount = getValue<Int>("retryCount") ?: 0
 *
 *         if (retryCount >= 3) {
 *             logger.e("Max retries exceeded")
 *             throw RuntimeException("Failed to download after 3 attempts")
 *         }
 *
 *         val success = downloadData()
 *         if (success) {
 *             MigrationStepResult.CONTINUE
 *         } else {
 *             state["retryCount"] = retryCount + 1
 *             logger.w("Download failed, retry attempt ${retryCount + 1}")
 *             MigrationStepResult.RERUN
 *         }
 *     }
 * }
 * ```
 *
 * @see Migration
 * @see MigrationStep
 * @see ExecutionContext
 */
class MigrationConfig {

    /**
     * Logger instance used for migration progress and error reporting.
     *
     * Defaults to the standard Logger.logger. You can override this with
     * a custom logger implementation to control how migration events are logged.
     *
     * Example:
     * ```kotlin
     * Migration {
     *     logger = MyCustomLogger() // Custom logger implementation
     *     // ... migration steps
     * }
     * ```
     */
    var logger: Logger = Logger.logger

    /**
     * Internal list of migration steps to be executed in sequence.
     *
     * This list is populated by calls to the step() methods and is used
     * by the Migration class to execute the migration workflow.
     */
    internal val steps = mutableListOf<MigrationStep>()

    /**
     * Adds a migration step with a description and action.
     *
     * This is the primary method for defining migration steps within the
     * configuration DSL. Each step will be executed in the order it's added
     * to the configuration.
     *
     * ## Examples:
     *
     * **Simple Step**:
     * ```kotlin
     * step("Clear application cache") {
     *     clearCache()
     *     MigrationStepResult.CONTINUE
     * }
     * ```
     *
     * **Step with Conditional Logic**:
     * ```kotlin
     * step("Migrate user preferences") {
     *     val userCount = getUserCount()
     *     if (userCount > 0) {
     *         migrateUserPreferences()
     *         MigrationStepResult.CONTINUE
     *     } else {
     *         logger.i("No users to migrate, skipping step")
     *         MigrationStepResult.CONTINUE
     *     }
     * }
     * ```
     *
     * **Step with State Management**:
     * ```kotlin
     * step("Process migration batch") {
     *     val batchSize = getValue<Int>("batchSize") ?: 100
     *     val processed = processBatch(batchSize)
     *     state["processedCount"] = processed
     *     MigrationStepResult.CONTINUE
     * }
     * ```
     *
     * @param description A descriptive message explaining what this step does
     * @param action The suspend function containing the migration logic
     */
    fun step(description: String, action: suspend ExecutionContext.() -> MigrationStepResult) {
        steps.add(MigrationStep(description, action))
    }

    /**
     * Adds a migration step with an auto-generated description.
     *
     * This method is provided for backward compatibility and convenience.
     * It creates a migration step with a generic description based on the
     * step's position in the sequence.
     *
     * **Note**: It's recommended to use the version with an explicit description
     * for better progress tracking and debugging.
     *
     * Example:
     * ```kotlin
     * step {
     *     performMigrationTask()
     *     MigrationStepResult.CONTINUE
     * }
     * // Creates a step with description "Migration step 1", "Migration step 2", etc.
     * ```
     *
     * @param step The suspend function containing the migration logic
     */
    fun step(step: suspend ExecutionContext.() -> MigrationStepResult) {
        steps.add(MigrationStep("Migration step ${steps.size + 1}", step))
    }

    /**
     * Adds a pre-created MigrationStep instance to the configuration.
     *
     * This method allows you to add MigrationStep instances that were created
     * outside of the configuration DSL. This is useful for reusing common
     * migration steps or when you need more complex step creation logic.
     *
     * ## Examples:
     *
     * **Reusable Step**:
     * ```kotlin
     * val databaseInitStep = MigrationStep("Initialize database") {
     *     initializeDatabase()
     *     MigrationStepResult.CONTINUE
     * }
     *
     * Migration {
     *     step(databaseInitStep) // Reuse the step
     *     step("Additional migration") { /* ... */ }
     * }
     * ```
     *
     * **Conditional Step Addition**:
     * ```kotlin
     * Migration {
     *     if (needsSpecialMigration) {
     *         step(createSpecialMigrationStep())
     *     }
     *     step("Standard migration") { /* ... */ }
     * }
     * ```
     *
     * @param step A pre-created MigrationStep instance
     */
    fun step(step: MigrationStep) {
        steps.add(step)
    }

}