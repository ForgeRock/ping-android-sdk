/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.migration

/**
 * Represents the progress state of a migration operation.
 *
 * MigrationProgress is a sealed class hierarchy that tracks the various states
 * of a migration process. It's emitted as a Flow from the Migration.migrate()
 * method, allowing consumers to monitor migration progress, handle errors,
 * and provide user feedback.
 *
 * ## Progress Flow Sequence:
 *
 * A typical migration follows this progress sequence:
 * 1. **Started** - Migration begins
 * 2. **InProgress** - For each step being executed
 * 3. **StepCompleted** - After each step completes successfully
 * 4. **Success** - When all steps complete, OR
 * 5. **Error** - If any step fails with an exception
 *
 * ## Usage Example:
 *
 * ```kotlin
 * migration.migrate(context).collect { progress ->
 *     when (progress) {
 *         is MigrationProgress.Started -> {
 *             showProgressDialog()
 *             updateStatus("Migration starting...")
 *         }
 *
 *         is MigrationProgress.InProgress -> {
 *             updateProgressBar(progress.currentStep, progress.totalSteps)
 *             updateStatus("Step ${progress.currentStep}/${progress.totalSteps}: ${progress.message}")
 *         }
 *
 *         is MigrationProgress.StepCompleted -> {
 *             logStepCompletion(progress.step.description)
 *         }
 *
 *         is MigrationProgress.Success -> {
 *             hideProgressDialog()
 *             showSuccessMessage(progress.message ?: "Migration completed")
 *         }
 *
 *         is MigrationProgress.Error -> {
 *             hideProgressDialog()
 *             showErrorDialog("Migration failed at step: ${progress.step.description}", progress.error)
 *         }
 *     }
 * }
 * ```
 *
 * ## Error Handling:
 *
 * When a migration step throws an exception, the migration process stops
 * and emits an Error progress state. The error contains both the exception
 * and the step where the failure occurred:
 *
 * ```kotlin
 * is MigrationProgress.Error -> {
 *     logger.e("Migration failed at step: ${progress.step.description}", progress.error)
 *
 *     // Handle specific error types
 *     when (progress.error) {
 *         is NetworkException -> retryMigration()
 *         is DataCorruptionException -> resetAndRestart()
 *         else -> showGenericError()
 *     }
 * }
 * ```
 *
 * @see Migration.migrate
 * @see MigrationStep
 * @see ExecutionContext
 */
sealed class MigrationProgress {
    /**
     * Migration has started.
     *
     * This is the first progress event emitted when a migration begins execution.
     * It indicates that the Migration framework has initialized and is about to
     * start executing the configured steps.
     *
     * ## Usage:
     * Use this event to initialize progress tracking UI, show loading indicators,
     * or prepare logging systems for the migration process.
     *
     * ```kotlin
     * is MigrationProgress.Started -> {
     *     progressBar.visibility = View.VISIBLE
     *     statusText.text = "Initializing migration..."
     *     startTime = System.currentTimeMillis()
     * }
     * ```
     */
    data object Started : MigrationProgress()

    /**
     * Migration is in progress with current step information.
     *
     * This progress event is emitted when a migration step is about to be executed.
     * It provides detailed information about the current step, including its position
     * in the sequence and descriptive message.
     *
     * This event is emitted BEFORE the step's execute() method is called, allowing
     * UI updates to show what operation is about to begin.
     *
     * ## Usage:
     * Use this event to update progress bars, status messages, and provide
     * real-time feedback about which operation is currently executing.
     *
     * ```kotlin
     * is MigrationProgress.InProgress -> {
     *     val progressPercent = (progress.currentStep * 100) / progress.totalSteps
     *     progressBar.progress = progressPercent
     *     statusText.text = "Step ${progress.currentStep}/${progress.totalSteps}: ${progress.message}"
     *     logger.i("Executing: ${progress.step.description}")
     * }
     * ```
     *
     * @param currentStep The 1-based index of the step currently being executed (1, 2, 3, ...)
     * @param step The MigrationStep instance being executed, containing description and logic
     * @param totalSteps The total number of steps in this migration sequence
     * @param message Optional descriptive message about the current step (usually step.description)
     */
    data class InProgress(
        val currentStep: Int,
        val step: MigrationStep,
        val totalSteps: Int,
        val message: String? = null
    ) : MigrationProgress()

    /**
     * A migration step has completed successfully.
     *
     * This progress event is emitted AFTER a migration step's execute() method
     * returns successfully with a result of CONTINUE or ABORT. It indicates that
     * the step has finished its work without throwing an exception.
     *
     * Note: Steps that return RERUN do not emit StepCompleted until they
     * eventually return CONTINUE or ABORT on a subsequent execution.
     *
     * ## Usage:
     * Use this event for logging step completions, updating detailed progress
     * tracking, or performing cleanup actions after each step.
     *
     * ```kotlin
     * is MigrationProgress.StepCompleted -> {
     *     logger.i("Completed step: ${progress.step.description}")
     *     completedSteps.add(progress.step.description)
     *     updateDetailedProgressLog("✓ ${progress.step.description}")
     * }
     * ```
     *
     * ## Flow Behavior:
     * - Emitted after successful step execution
     * - NOT emitted for steps that return RERUN (until final success)
     * - NOT emitted for steps that throw exceptions
     * - Emitted before proceeding to the next step or completing migration
     *
     * @param step The MigrationStep that has completed successfully
     */
    data class StepCompleted(val step: MigrationStep) : MigrationProgress()

    /**
     * Migration completed successfully.
     *
     * This is the final progress event emitted when a migration completes without
     * errors. It indicates that all migration steps have been executed successfully,
     * or the migration was terminated early by a step returning ABORT.
     *
     * ## Completion Scenarios:
     *
     * **All Steps Completed**:
     * All configured steps executed and returned CONTINUE.
     *
     * **Early Termination**:
     * A step returned ABORT, terminating the migration successfully.
     *
     * **Empty Migration**:
     * No steps were configured, migration completes immediately.
     *
     * ## Usage:
     * Use this event to hide progress UI, show success messages, perform
     * post-migration cleanup, or trigger dependent operations.
     *
     * ```kotlin
     * is MigrationProgress.Success -> {
     *     hideProgressDialog()
     *     val message = progress.message ?: "Migration completed successfully"
     *     showSuccessNotification(message)
     *
     *     // Perform post-migration actions
     *     cleanupTemporaryFiles()
     *     notifyMigrationComplete()
     *
     *     // Log completion time
     *     val duration = System.currentTimeMillis() - startTime
     *     logger.i("Migration completed in ${duration}ms")
     * }
     * ```
     *
     * @param message Optional success message providing additional context about the completion
     */
    data class Success(val message: String? = null) : MigrationProgress()

    /**
     * Migration failed with an error.
     *
     * This progress event is emitted when a migration step throws an exception
     * during execution. The migration process stops immediately when an error
     * occurs, and no further steps are executed.
     *
     * ## Error Context:
     * The Error state provides both the exception that caused the failure and
     * the specific step where the error occurred, enabling precise error handling
     * and debugging.
     *
     * ```
     *
     * @param error The exception that caused the migration to fail
     * @param step The MigrationStep where the error occurred, providing context for debugging
     */
    data class Error(
        val error: Throwable,
        val step: MigrationStep
    ) : MigrationProgress()
}