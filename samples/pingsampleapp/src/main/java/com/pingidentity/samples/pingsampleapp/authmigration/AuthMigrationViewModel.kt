/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.authmigration

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.auth.migration.AuthMigration
import com.pingidentity.auth.migration.DefaultStorageClientProvider
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.migration.MigrationProgress
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Represents the current state of the migration process.
 */
enum class MigrationStatus {
    IDLE,
    CHECKING,
    RUNNING,
    COMPLETED,
    FAILED
}

/**
 * Represents the status of an individual migration step.
 */
enum class StepStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * Represents a single migration step's result for UI display.
 */
data class StepResult(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val status: StepStatus
)

/**
 * State for the Auth Migration screen.
 */
data class AuthMigrationState(
    val migrationStatus: MigrationStatus = MigrationStatus.IDLE,
    val isMigrationNeeded: Boolean? = null,
    val stepResults: List<StepResult> = emptyList(),
    val summaryMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel that manages the auth migration process.
 *
 * Provides functionality to check if legacy FR Authenticator data exists
 * and to run the full migration pipeline with progress tracking.
 */
class AuthMigrationViewModel : ViewModel() {

    var state = MutableStateFlow(AuthMigrationState())
        private set

    /**
     * Checks whether legacy FR Authenticator data exists in SharedPreferences.
     */
    fun checkMigrationNeeded(context: Context) {
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
...

        viewModelScope.launch {
            state.update { it.copy(migrationStatus = MigrationStatus.CHECKING) }
            try {
                val needed = withContext(Dispatchers.IO) {
                    val provider = DefaultStorageClientProvider(context.applicationContext)
                    provider.isMigrationRequired(context.applicationContext)
                }
                state.update {
                    it.copy(
                        migrationStatus = MigrationStatus.IDLE,
                        isMigrationNeeded = needed
                    )
                }
            } catch (_: Exception) {
                state.update {
                    it.copy(
                        migrationStatus = MigrationStatus.IDLE,
                        isMigrationNeeded = false
                    )
                }
            }
        }
    }

    /**
     * Starts the migration pipeline with progress tracking.
     */
    fun startMigration(context: Context) {
        viewModelScope.launch {
            // Reset state
            state.update {
                it.copy(
                    migrationStatus = MigrationStatus.RUNNING,
                    stepResults = emptyList(),
                    summaryMessage = null,
                    errorMessage = null
                )
            }

            try {
                AuthMigration.start(context.applicationContext) {
                    logger = Logger.STANDARD
                    progress = FlowCollector { progress ->
                        when (progress) {
                            is MigrationProgress.Started -> {
                                // Migration beginning - no UI update needed
                            }

                            is MigrationProgress.InProgress -> {
                                state.update { currentState ->
                                    val updatedSteps = currentState.stepResults + StepResult(
                                        description = progress.step.description,
                                        status = StepStatus.IN_PROGRESS
                                    )
                                    currentState.copy(stepResults = updatedSteps)
                                }
                            }

                            is MigrationProgress.StepCompleted -> {
                                state.update { currentState ->
                                    val updatedSteps = currentState.stepResults.map { step ->
                                        if (step.description == progress.step.description) {
                                            step.copy(status = StepStatus.COMPLETED)
                                        } else {
                                            step
                                        }
                                    }
                                    currentState.copy(stepResults = updatedSteps)
                                }
                            }

                            is MigrationProgress.Success -> {
                                state.update {
                                    it.copy(
                                        migrationStatus = MigrationStatus.COMPLETED,
                                        summaryMessage = progress.message
                                            ?: "Migration completed successfully",
                                        isMigrationNeeded = false
                                    )
                                }
                            }

                            is MigrationProgress.Error -> {
                                state.update { currentState ->
                                    val updatedSteps = currentState.stepResults.map { step ->
                                        if (step.description == progress.step.description) {
                                            step.copy(status = StepStatus.FAILED)
                                        } else {
                                            step
                                        }
                                    }
                                    currentState.copy(
                                        migrationStatus = MigrationStatus.FAILED,
                                        stepResults = updatedSteps,
                                        errorMessage = "Failed at \"${progress.step.description}\": ${progress.error.message}"
                                    )
                                }
                            }
                        }
                    }
                }

                // If migration completed without explicit success/error
                if (state.value.migrationStatus == MigrationStatus.RUNNING) {
                    state.update {
                        it.copy(
                            migrationStatus = MigrationStatus.COMPLETED,
                            summaryMessage = it.summaryMessage ?: "No legacy data to migrate."
                        )
                    }
                }
            } catch (e: Exception) {
                state.update {
                    it.copy(
                        migrationStatus = MigrationStatus.FAILED,
                        errorMessage = e.message ?: "Migration failed with an unknown error."
                    )
                }
            }
        }
    }
}
