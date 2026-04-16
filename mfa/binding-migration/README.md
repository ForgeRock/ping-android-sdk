[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Device Binding Migration

The Device Binding Migration module provides automatic migration capabilities for upgrading legacy device binding data to the new unified storage format. This module ensures seamless transitions between SDK versions while preserving user authentication keys and configurations.

## Getting Started

### Prerequisites

- Ping Identity Platform
    - Ping Advanced Identity Cloud
    - PingAM 6.5.2 or higher
- Android API level 29 or higher
- The `binding` module must be integrated in your project

### Installation

To integrate this module into your Android project, include the following dependency in
your `build.gradle.kts` (or `build.gradle`) file:

To add the Device Binding Migration module as a dependency to your project, include the following in your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation(project(":mfa:binding-migration"))
}
```

Replace `<version>` with the latest available version of the SDK from the Maven repository. Ensure your
project's `repositories` block includes Maven Central or the Ping Identity Maven repository.

## Migration Overview

The Device Binding Migration module automatically migrates legacy device binding data during application startup. It handles the following migration scenarios:

- **Legacy User Keys**: Migrates user key metadata from legacy encrypted file storage to the new UserKeysStorage system
- **App PIN Keys**: Migrates App PIN cryptographic keys from legacy encrypted file keystore to new encrypted data store
- **Cleanup**: Removes legacy storage files after successful migration

### What Gets Migrated

| Legacy Component | Target Component | Description |
|------------------|------------------|-------------|
| Legacy Device Repository | UserKeysStorage | User key metadata and authentication type mappings |
| Legacy Encrypted File Keystore | AppPinConfig Storage | App PIN cryptographic key material |
| Legacy Storage Files | - | Cleaned up after successful migration |


## Automatic Migration

The migration process is automatically triggered during application startup using AndroidX Startup library. No manual intervention is required.

### Configuration

The following initializer will be added to your `AndroidManifest.xml`:

```xml

<application>
    <provider android:authorities="${applicationId}.androidx-startup"
        android:exported="false" android:name="androidx.startup.InitializationProvider"
        tools:node="merge">
        <meta-data
            android:name="com.pingidentity.device.binding.migration.BindingMigrationInitializer"
            android:value="androidx.startup" />
    </provider>
</application>
```

The migration will automatically:
1. Check for legacy data during app startup
2. Migrate data if found
3. Clean up legacy files after successful migration
4. Skip migration if no legacy data exists

## Migration Steps

The migration process consists of three main steps:

### Step 1: Check for Legacy Data
- Verifies existence of legacy device binding repository
- Retrieves all stored user keys
- Aborts migration if no legacy data is found

### Step 2: Migrate User Keys
- Transfers user key metadata to new UserKeysStorage system
- Preserves user IDs, authentication types, and key identifiers
- Deletes legacy repository after successful migration

### Step 3: Migrate App PIN Keys
- Migrates App PIN cryptographic keys to new encrypted storage
- Maintains key security and encryption
- Removes legacy encrypted files after migration

## Manual Migration

While migration is automatic, you can also trigger it manually:

```kotlin
import com.pingidentity.device.binding.migration.BindingMigration

// Manual migration trigger
BindingMigration.start(context)
```

### Monitoring Migration Progress

```kotlin
BindingMigration.migration.migrate(context).collect { progress ->
    when (progress) {
        is MigrationProgress.Started -> {
            Log.d("Migration", "Device binding migration started")
        }
        
        is MigrationProgress.InProgress -> {
            Log.d("Migration", "Step ${progress.currentStep}/${progress.totalSteps}: ${progress.message}")
        }
        
        is MigrationProgress.Success -> {
            Log.d("Migration", "Migration completed successfully")
        }
        
        is MigrationProgress.Error -> {
            Log.e("Migration", "Migration failed", progress.error)
        }
    }
}
```

## Error Handling

The migration framework includes robust error handling:

### Individual Step Failures
```kotlin
try {
    // Migration logic
    appPinConfig.storage().save(byteArray)
} catch (e: Exception) {
    logger.e("Failed to migrate APP_PIN key for user ${key.userId}: ${e.message}", e)
    // Continue with other keys even if one fails
}
```

- Failures in individual key migration don't stop the entire process
- Errors are logged for debugging purposes
- Partial migrations are supported

### Migration Abortion
- Migration automatically aborts if no legacy data is found
- No errors are thrown for clean installations
- Subsequent app starts skip migration checks after successful completion

## ⚠️ Important Warning: Custom Storage Migration

**If you are using custom storage implementations for device binding, this automatic migration will NOT handle your custom data.**

This migration module only handles the default Ping SDK storage implementations:
- Default UserKeysStorage (encrypted DataStore)
- Default AppPinConfig storage (encrypted DataStore)
- Legacy encrypted file keystore

### Custom Storage Requirements

If your application uses custom storage implementations, you must:

1. **Write your own migration logic** to handle your custom data format
2. **Implement migration before** the automatic migration runs
3. **Test your custom migration thoroughly** with real user data

## Troubleshooting

### Common Issues

**Migration Not Running**
- Verify the BindingMigrationInitializer is properly configured in AndroidManifest.xml
- Check that the binding-migration dependency is included
- Ensure app has proper file system permissions

**Legacy Data Not Found**
- This is normal for new installations
- Migration will abort with ABORT result (not an error)
- No action required

### Debug Logging

Enable debug logging to monitor migration progress:

```kotlin
// Migration uses the standard Ping Logger
Logger.logger = Logger.STANDARD
```

Check logs for migration status:
```
Ping SDK: Migration started
Ping SDK: Step 1/3: Check for legacy encrypted file keystore
Ping SDK: Found 2 keys to migrate: [user123(APPLICATION_PIN), user456(BIOMETRIC_ONLY)]
Ping SDK: Step 2/3: Migrate user keys to new user keys storage
Ping SDK: Successfully migrated 2 user keys to new storage
Ping SDK: Step 3/3: Migrate App PIN keystore file to new encrypted keystore file
Ping SDK: Migration completed successfully
```

### Migration State

The migration maintains state between steps:
- User keys are stored in migration context
- Legacy repository reference is preserved for cleanup
- Failed migrations can be retried on next app start

## Migration Safety

The migration process is designed to be safe and non-destructive:

- **Backup Approach**: Legacy data is copied before deletion
- **Rollback Safety**: Original data remains until migration succeeds
- **Idempotent**: Can be run multiple times safely
- **Partial Recovery**: Individual key failures don't affect others

### Data Integrity

- All cryptographic keys maintain their security properties
- User authentication types are preserved
- Key-user associations remain intact
- No data is lost during migration process

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.
