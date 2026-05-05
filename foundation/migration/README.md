[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Migration Module

The Migration module provides a flexible framework for managing data migration and transformation tasks in Android applications. It enables applications to perform step-by-step migrations with progress tracking, error handling, and state management.

## Getting Started

### Prerequisites

- Android API level 29 or higher

### Installation

To integrate this module into your Android project, include the following dependency in
your `build.gradle.kts` (or `build.gradle`) file:

```kotlin
dependencies {
    implementation("com.pingidentity.sdks:migration:<version>")
}
```

Replace `<version>` with the latest available version of the SDK from the Maven repository. Ensure your
project's `repositories` block includes Maven Central or the Ping Identity Maven repository.

## Migration Framework Overview

The Migration SDK consists of several key components:

- **Migration**: The main class that orchestrates the migration process
- **MigrationStep**: Interface representing individual migration steps
- **MigrationConfig**: Configuration class for setting up migration steps and options
- **ExecutionContext**: Context object that provides shared state and resources across migration steps
- **MigrationProgress**: Sealed class representing different states of migration progress

### Migration Flow

```
Started → InProgress (Step 1) → InProgress (Step 2) → ... → Success/Error
```

Each step can return one of three results:
- **CONTINUE**: Proceed to the next step
- **RERUN**: Execute the current step again
- **ABORT**: Terminate migration successfully

## How to Use the SDK

### Creating a Simple Migration

```kotlin
import com.pingidentity.migration.Migration
import com.pingidentity.migration.MigrationStepResult

val migration = Migration {
    logger = Logger.STANDARD
    
    step("Initialize database") {
        // Perform database initialization
        MigrationStepResult.CONTINUE
    }
    
    step("Migrate user data") {
        // Move user data to new format
        MigrationStepResult.CONTINUE
    }
    
    step("Clean up old data") {
        // Remove obsolete data
        MigrationStepResult.CONTINUE
    }
}

// Execute the migration
migration.migrate(context).collect { progress ->
    when (progress) {
        is MigrationProgress.Started -> {
            println("Migration started")
        }
        is MigrationProgress.InProgress -> {
            println("Step ${progress.currentStep}/${progress.totalSteps}: ${progress.message}")
        }
        is MigrationProgress.Success -> {
            println("Migration completed: ${progress.message}")
        }
        is MigrationProgress.Error -> {
            println("Migration failed: ${progress.error.message}")
        }
    }
}
```

### Migration Progress Monitoring

The migration framework emits progress events as a Kotlin Flow:

```kotlin
migration.migrate(context).collect { progress ->
    when (progress) {
        is MigrationProgress.Started -> {
            // Migration has begun
            showProgressDialog()
        }
        
        is MigrationProgress.InProgress -> {
            // Update progress UI
            updateProgress(progress.currentStep, progress.totalSteps, progress.message)
        }
        
        is MigrationProgress.Success -> {
            // Migration completed successfully
            hideProgressDialog()
            showSuccessMessage(progress.message)
        }
        
        is MigrationProgress.Error -> {
            // Handle migration failure
            hideProgressDialog()
            showErrorMessage(progress.error)
        }
    }
}
```

### Step Configuration

You can configure migration steps in several ways:

```kotlin
val migration = Migration {
    // Step with custom description
    step("Update app configuration") {
        updateAppConfig()
        MigrationStepResult.CONTINUE
    }
    
    // Step with auto-generated description
    step {
        performCleanup()
        MigrationStepResult.CONTINUE
    }
    
    // Step with conditional logic
    step("Migrate preferences") {
        if (needsPreferenceMigration()) {
            migratePreferences()
            MigrationStepResult.CONTINUE
        } else {
            MigrationStepResult.CONTINUE
        }
    }
}
```

### State Management

The migration framework provides a shared state mechanism through the ExecutionContext:

```kotlin
val migration = Migration {
    step("Load configuration") {
        val config = loadConfiguration()
        state["config"] = config
        state["migrationStartTime"] = System.currentTimeMillis()
        MigrationStepResult.CONTINUE
    }
    
    step("Process data") {
        val config = getValue<Configuration>("config")
        val startTime = getValue<Long>("migrationStartTime")
        
        processDataWithConfig(config)
        state["processedCount"] = getProcessedCount()
        MigrationStepResult.CONTINUE
    }
    
    step("Generate report") {
        val processedCount = getValue<Int>("processedCount")
        val startTime = getValue<Long>("migrationStartTime")
        val duration = System.currentTimeMillis() - startTime
        
        generateMigrationReport(processedCount, duration)
        MigrationStepResult.CONTINUE
    }
}
```

### Error Handling

The migration framework automatically catches exceptions and emits error progress events:

```kotlin
val migration = Migration {
    step("Risky operation") {
        try {
            performRiskyOperation()
            MigrationStepResult.CONTINUE
        } catch (e: SpecificException) {
            // Handle specific error and continue
            handleSpecificError(e)
            MigrationStepResult.CONTINUE
        }
        // Other exceptions will be caught by the framework
    }
}
```

## Migration Step Results

| Result | Description | Behavior |
|--------|-------------|----------|
| `CONTINUE` | Step completed successfully | Proceeds to the next migration step |
| `RERUN` | Step needs to be executed again | Repeats the current step (useful for retry logic) |
| `ABORT` | Migration should terminate successfully | Stops migration and emits Success progress |

### Example with Retry Logic

```kotlin
step("Download required data") {
    val maxRetries = getValue<Int>("retries") ?: 0
    
    if (maxRetries >= 3) {
        logger.w("Max retries reached, skipping download")
        MigrationStepResult.CONTINUE
    } else {
        try {
            downloadData()
            MigrationStepResult.CONTINUE
        } catch (e: NetworkException) {
            state["retries"] = maxRetries + 1
            logger.i("Download failed, retry ${maxRetries + 1}/3")
            MigrationStepResult.RERUN
        }
    }
}
```

---

## Advanced Usage

### Custom Migration Steps

You can create reusable migration steps by implementing the MigrationStep interface:

```kotlin
class DatabaseMigrationStep(private val fromVersion: Int, private val toVersion: Int) : MigrationStep {
    override var description = "Migrate database from v$fromVersion to v$toVersion"
    
    override suspend fun execute(context: ExecutionContext): MigrationStepResult {
        context.logger.i("Starting database migration: $fromVersion -> $toVersion")
        
        // Perform database migration logic
        migrateDatabaseSchema(fromVersion, toVersion)
        
        context.state["databaseVersion"] = toVersion
        return MigrationStepResult.CONTINUE
    }
}

// Usage
val migration = Migration {
    step(DatabaseMigrationStep(1, 2))
    step(DatabaseMigrationStep(2, 3))
}
```

### Conditional Migration Logic

```kotlin
val migration = Migration {
    step("Check migration requirements") {
        val currentVersion = getCurrentAppVersion()
        val lastMigrationVersion = getLastMigrationVersion()
        
        state["currentVersion"] = currentVersion
        state["needsMigration"] = currentVersion > lastMigrationVersion
        
        MigrationStepResult.CONTINUE
    }
    
    step("Perform migration") {
        val needsMigration = getValue<Boolean>("needsMigration") ?: false
        
        if (needsMigration) {
            performActualMigration()
            MigrationStepResult.CONTINUE
        } else {
            logger.i("No migration needed, skipping")
            MigrationStepResult.ABORT // Skip remaining steps
        }
    }
}
```

### Migration Context

The ExecutionContext provides access to:

- **context**: Android Context for system resources
- **logger**: Logger instance for debugging and monitoring
- **state**: Mutable map for sharing data between migration steps

```kotlin
step("Access system resources") {
    // Access Android Context
    val packageName = context.packageName
    val sharedPrefs = context.getSharedPreferences("migration", Context.MODE_PRIVATE)
    
    // Use logger
    logger.d("Processing migration for package: $packageName")
    
    // Store data in shared state
    state["packageName"] = packageName
    state["preferences"] = sharedPrefs
    
    MigrationStepResult.CONTINUE
}
```

## Best Practices

1. **Keep steps focused**: Each migration step should have a single responsibility
2. **Use descriptive names**: Provide clear descriptions for migration steps
3. **Handle errors gracefully**: Use try-catch blocks for operations that might fail
4. **Leverage state**: Share data between steps using the ExecutionContext state
5. **Monitor progress**: Implement proper progress tracking for long-running migrations
6. **Test thoroughly**: Ensure migration steps work correctly in isolation and as part of the full migration

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.
