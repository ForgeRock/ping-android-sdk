[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Authenticator Migration

The Authenticator Migration module provides automatic migration capabilities for upgrading legacy FR Authenticator data to the new unified OATH and Push storage format. This module ensures seamless transitions between SDK versions while preserving user accounts, TOTP/HOTP credentials, and push credentials.

---

## Table of Contents

- [Integrating the SDK into your project](#integrating-the-sdk-into-your-project)
- [Migration Overview](#migration-overview)
- [Automatic Migration](#automatic-migration)
- [Migration Steps](#migration-steps)
- [Manual Migration](#manual-migration)
- [Monitoring Migration Progress](#monitoring-migration-progress)
- [Error Handling](#error-handling)
- [Custom Storage Migration](#custom-storage-migration)
- [Troubleshooting](#troubleshooting)

---

## Integrating the SDK into your project

To add the Authenticator Migration module as a dependency to your project, include the following in your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation(project(":mfa:auth-migration"))
}
```

---

## Migration Overview

The Authenticator Migration module automatically migrates legacy FR Authenticator data during application startup. It handles the following migration scenarios:

- **OATH Credentials**: Migrates TOTP and HOTP mechanisms from legacy format to new SQLOathStorage
- **Push Credentials**: Migrates Push authentication mechanisms to new SQLPushStorage
- **Cleanup**: Removes legacy storage files and encryption keys after successful migration

### What Gets Migrated

| Legacy Component | Target Component | Description |
|------------------|------------------|-------------|
| Legacy Accounts | OATH/Push Credentials | Account metadata (issuer, account name, images) |
| Legacy TOTP/HOTP Mechanisms | SQLOathStorage | TOTP/HOTP credentials with secrets and configuration |
| Legacy Push Mechanisms | SQLPushStorage | Push credentials with endpoints and shared secrets |
| Legacy Encryption Keys | - | Cleaned up from AndroidKeyStore after migration |

---

## Automatic Migration

The migration can be triggered automatically during application startup by calling `AuthMigration.start()`. No further manual intervention is required once configured.
To start automatic migration, please add the following code to your manifest file
```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false">
    <meta-data
        android:name="com.pingidentity.auth.migration.AuthenticationMigrationInitializer"
        android:value="androidx.startup" />
</provider>
```

### Configuration

Add the following in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":mfa:auth-migration"))
}
```

Create a `LegacyAuthenticationConfig` and start the migration, typically in your `Application.onCreate()` or an `AppInitializer`:

```kotlin
lifecycleScope.launch {
    AuthMigration.start(context)
}
```

The migration will automatically:
1. Check for legacy FR Authenticator data during app startup
2. Decrypt and export data from legacy encrypted SharedPreferences
3. Migrate OATH credentials and Push credentials
4. Clean up legacy files and encryption keys after successful migration
5. Skip migration if no legacy data exists

### LegacyAuthenticationConfig

`LegacyAuthenticationConfig` is configured via a DSL block passed to `AuthMigration.start()`. No arguments are required for standard FR Authenticator installations — the empty block (or no block at all) uses sensible defaults.

| Property | Default | Description |
|----------|---------|-------------|
| `keyAlias` | `"org.forgerock.android.authenticator.KEYS"` | AndroidKeyStore alias used to decrypt legacy SharedPreferences |
| `legacyStorageProvider` | `DefaultLegacyStorageProvider` | Override to supply data from a custom storage backend |
| `logger` | `Logger.STANDARD` | Logger instance used throughout the migration pipeline |

```kotlin
// Default — no configuration needed
lifecycleScope.launch {
    AuthMigration.start(applicationContext)
}

// Custom key alias
lifecycleScope.launch {
    AuthMigration.start(applicationContext) {
        keyAlias = "com.myapp.custom.STORAGE_KEY"
    }
}

// Custom storage provider
lifecycleScope.launch {
    AuthMigration.start(applicationContext) {
        legacyStorageProvider = MyCustomStorageProvider(applicationContext)
    }
}

// Custom logger
lifecycleScope.launch {
    AuthMigration.start(applicationContext) {
        logger = Logger.DEBUG
    }
}
```

---

## Migration Steps

The migration process consists of three sequential steps:

### Step 1: Import Legacy Data
- Calls `LegacyStorageProvider.isMigrationRequired()` — aborts early if no migration is needed
- Calls `LegacyStorageProvider.getMigrationData()` to load `LegacyExportedData`
- Default implementation (`DefaultLegacyStorageProvider`) decrypts two legacy SharedPreferences files:
  - `org.forgerock.android.authenticator.DATA.ACCOUNT`
  - `org.forgerock.android.authenticator.DATA.MECHANISM`
- Uses standalone decryptor (no Legacy SDK dependency required)
- Aborts migration if the mechanisms list is empty

### Step 2: Migrate OATH and Push Mechanisms
- Parses mechanism data to determine type (TOTP/HOTP/Push)
- Creates `OathCredential` objects for TOTP/HOTP mechanisms:
  - Preserves secret, algorithm (SHA1/SHA256/SHA512), digits, period
  - Maintains issuer, account name, and visual customizations
- Creates `PushCredential` objects for Push mechanisms:
  - Preserves server endpoint, shared secret
  - Maintains account information and visual customizations
- Stores credentials in `SQLOathStorage` and `SQLPushStorage` respectively

### Step 3: Cleanup Legacy Data
- Deletes all legacy SharedPreferences files
- Removes legacy encryption key from AndroidKeyStore
- Cleanup failures don't abort the migration (non-critical)

---

## Manual Migration

You can trigger the migration manually at any point:

```kotlin
// Default — uses the standard ForgeRock key alias
lifecycleScope.launch {
    AuthMigration.start(applicationContext)
}

// Custom key alias
lifecycleScope.launch {
    AuthMigration.start(applicationContext) {
        keyAlias = "com.myapp.custom.STORAGE_KEY" // optional, default is the ForgeRock alias
    }
}
```

---

## Monitoring Migration Progress

Pass a collector before calling `start()`, or observe through the migration's own logging. The `start()` function internally collects and logs every `MigrationProgress` event:

```kotlin
lifecycleScope.launch {
    AuthMigration.start(context)
    // Migration is complete when start() returns
}
```

`AuthMigration` logs the following events automatically via its `logger`:

```
Migration started
Migration in progress: Step 1 of 3 - Import legacy data
Step completed: Import legacy data
Migration in progress: Step 2 of 3 - Migrate mechanisms to OATH and Push storage
Step completed: Migrate mechanisms to OATH and Push storage
Migration in progress: Step 3 of 3 - Cleanup legacy authenticator data
Step completed: Cleanup legacy authenticator data
Migration completed successfully
```

To observe raw `MigrationProgress` events, use the `Migration` API directly with your own steps.

---

## Error Handling

The migration framework includes robust error handling:

### Individual Mechanism Failures
- Failures in individual mechanism migration don't stop the entire process
- Errors are logged for debugging purposes
- Partial migrations are supported

### Decryption Failures
The migration uses a standalone decryptor that:
- Handles both AES symmetric and RSA-wrapped keys
- Verifies MAC signatures for data integrity
- Gracefully handles corrupted or missing data
- Returns empty maps for non-existent files

### Migration Abortion
- Migration automatically aborts if no legacy data is found
- No errors are thrown for clean installations
- Subsequent app starts skip migration checks after successful completion

---

## Custom Storage Migration

If your application stores authenticator data in a custom storage backend (e.g., a different `StorageClient` implementation, an encrypted database, or a remote store), you can supply that data to the migration pipeline by implementing `LegacyStorageProvider`.

### LegacyStorageProvider Interface

```kotlin
interface LegacyStorageProvider {
    /** Return true if migration is needed (i.e., legacy data still exists). */
    suspend fun isMigrationRequired(context: Context): Boolean

    /**
     * Return the legacy authenticator data as [LegacyExportedData].
     * The returned object is fed directly into the migration pipeline.
     */
    suspend fun getMigrationData(context: Context): LegacyExportedData

    /** Remove legacy data after successful migration. */
    suspend fun cleanUp(context: Context)
}
```

### Option 1: Using a ForgeRock StorageClient

If your custom storage implements the ForgeRock `StorageClient` interface, use the built-in `LegacyDataConverter.convertToLegacyExportedData()` helper. It calls `account.toJson()` and `mechanism.toJson()` on each entry via the SDK's own serialisation — no manual field mapping required:

```kotlin
class MyStorageProvider(
    private val context: Context,
    private val storageClient: StorageClient
) : LegacyStorageProvider {

    override suspend fun isMigrationRequired(context: Context): Boolean =
        !storageClient.isEmpty

    override suspend fun getMigrationData(context: Context): LegacyExportedData =
        withContext(Dispatchers.IO) {
            LegacyDataConverter.convertToLegacyExportedData(storageClient)
        }

    override suspend fun cleanUp(context: Context) {
        // Remove data from your custom storage
    }
}
```

### Option 2: Building from raw JSON strings

If your storage exposes per-entry JSON strings (e.g., the same format ForgeRock uses internally), use `LegacyDataConverter.buildExportJson()`:

```kotlin
override suspend fun getMigrationData(context: Context): LegacyExportedData =
    withContext(Dispatchers.IO) {
        val mechanismsMap = mutableMapOf<String, String>()
        val accountsMap   = mutableMapOf<String, String>()

        myStorage.getAllMechanisms().forEach { mechanismsMap[it.id] = it.toJson() }
        myStorage.getAllAccounts().forEach   { accountsMap[it.id]   = it.toJson() }

        LegacyDataConverter.buildExportJson(mechanismsMap, accountsMap)
    }
```

### Option 3: Building LegacyExportedData manually

For fully custom storage backends, construct `LegacyExportedData` directly:

```kotlin
override suspend fun getMigrationData(context: Context): LegacyExportedData =
    withContext(Dispatchers.IO) {
        val mechanisms = myStorage.getAllMechanisms().map { m ->
            LegacyMechanism(
                id            = m.id,
                issuer        = m.issuer,
                accountName   = m.accountName,
                mechanismUID  = m.uid,
                secret        = m.secret,
                type          = m.type,         // "otpauth" or "pushauth"
                oathType      = m.oathType,     // "TOTP" or "HOTP" (OATH only)
                algorithm     = m.algorithm,    // "SHA1", "SHA256", "SHA512" (OATH only)
                digits        = m.digits,
                period        = m.period,
                counter       = m.counter,
                registrationEndpoint   = m.registrationEndpoint,   // Push only
                authenticationEndpoint = m.authenticationEndpoint, // Push only
                account = LegacyAccount(
                    id          = m.accountId,
                    issuer      = m.issuer,
                    accountName = m.accountName,
                    imageURL    = m.imageURL,
                    backgroundColor = m.backgroundColor
                )
            )
        }
        LegacyExportedData(
            mechanisms = mechanisms,
            metadata   = LegacyExportMetadata(totalMechanisms = mechanisms.size)
        )
    }
```

### Registering a Custom Storage Provider

Pass your implementation via the DSL block in `AuthMigration.start()`:

```kotlin
lifecycleScope.launch {
    AuthMigration.start(applicationContext) {
        legacyStorageProvider = MyStorageProvider(
            context = applicationContext,
            storageClient = MyStorageClient()
        )
    }
}
```

### Expected Data Format

The `LegacyExportedData` your implementation returns must conform to the structure in `sample_export.json`. Key points:

- Each mechanism contains its associated account nested directly (`account` field — 1-to-1 mapping)
- OATH mechanisms use `type: "otpauth"`, Push mechanisms use `type: "pushauth"`
- `metadata.exportedAt` is a UTC epoch millisecond timestamp

```json
{
  "mechanisms": [
    {
      "id": "Forgerock-totp@forgerock.com-otpauth",
      "issuer": "Forgerock",
      "accountName": "totp@forgerock.com",
      "mechanismUID": "e238a4ce-23f4-4f41-9ccd-2ef85fc641d9",
      "secret": "JBSWY3DPEHPK3PXP",
      "type": "otpauth",
      "oathType": "TOTP",
      "algorithm": "SHA256",
      "digits": 6,
      "period": 60,
      "timeAdded": 1772565130356,
      "account": {
        "id": "Forgerock-totp@forgerock.com",
        "issuer": "Forgerock",
        "displayIssuer": "Forgerock Display",
        "accountName": "totp@forgerock.com",
        "displayAccountName": "TOTP Display User",
        "imageURL": "http://forgerock.com/logo.jpg",
        "backgroundColor": "#032b75",
        "lock": false
      }
    },
    {
      "id": "PingIdentity-user@example.com-pushauth",
      "issuer": "PingIdentity",
      "accountName": "user@example.com",
      "mechanismUID": "22752db7-8d03-4181-9f06-f440e46527c1",
      "secret": "BASE64_ENCODED_SECRET",
      "type": "pushauth",
      "registrationEndpoint": "https://example.com/am/push/sns/message?_action=register",
      "authenticationEndpoint": "https://example.com/am/push/sns/message?_action=authenticate",
      "platform": "PING_AM",
      "uid": "user123",
      "resourceId": "resource-id-123",
      "timeAdded": 1772565130356,
      "account": {
        "id": "PingIdentity-user@example.com",
        "issuer": "PingIdentity",
        "displayIssuer": "Ping Identity",
        "accountName": "user@example.com",
        "displayAccountName": "Demo User",
        "imageURL": "https://pingidentity.com/logo.png",
        "backgroundColor": "#0066cc",
        "lock": false
      }
    }
  ],
  "metadata": {
    "totalMechanisms": 2,
    "exportedAt": 1772565130356
  }
}
```

See `sample_export.json` for a complete three-mechanism example.

---

## Legacy Encryption Format

The Legacy SDK's FR Authenticator used a custom encryption format:

1. **Data Wrapping**: Values stored as `{"type": X, "value": Y}` JSON
2. **Encryption**: AES/GCM with 12-byte IV and 128-bit authentication tag
3. **MAC**: HMAC-SHA256 for additional integrity verification
4. **Key Storage**: Master key in AndroidKeyStore with alias `org.forgerock.android.authenticator.KEYS`
5. **Encoding**: Base64 encoding for storage in SharedPreferences

The migration decryptor reverses this process without requiring the Legacy SDK.

---

## Data Mapping

### OATH Credentials

| Legacy Field | New Field | Notes |
|-------------|-----------|-------|
| `issuer` | `issuer` | Preserved exactly |
| `accountName` | `accountName` | Preserved exactly |
| `secret` | `secret` | Base32-encoded shared secret |
| `algorithm` | `oathAlgorithm` | Maps SHA1/SHA256/SHA512 |
| `digits` | `digits` | Typically 6 or 8 |
| `period` | `period` | For TOTP (default 30s) |
| `counter` | `counter` | For HOTP |
| `oathType` | `oathType` | Maps to TOTP or HOTP enum |
| `imageURL` | `imageURL` | Optional logo URL |
| `backgroundColor` | `backgroundColor` | Optional UI customization |

### Push Credentials

| Legacy Field | New Field | Notes |
|-------------|-----------|-------|
| `issuer` | `issuer` | Preserved exactly |
| `accountName` | `accountName` | Preserved exactly |
| `authenticationEndpoint` | `serverEndpoint` | Server URL for push (query params stripped) |
| `secret` | `sharedSecret` | Cryptographic secret |
| `imageURL` | `imageURL` | Optional logo URL |
| `backgroundColor` | `backgroundColor` | Optional UI customization |

---

## Troubleshooting

### Common Issues

**Migration Not Running**
- Ensure `AuthMigration.start(context)` is called on app startup
- Check that the `auth-migration` dependency is included in your build

**Legacy Data Not Found**
- This is normal for new installations or users who never used FR Authenticator
- Migration will abort with `ABORT` result (not an error)
- No action required

**Decryption Failures**
- Check if the legacy encryption key exists in AndroidKeyStore
- Verify SharedPreferences files are not corrupted
- Ensure the app hasn't been reinstalled (encryption keys are lost on reinstall)

**Storage Initialization Errors**
- Verify `SQLOathStorage` and `SQLPushStorage` are properly initialized
- Check database permissions and storage availability
- Review SQLite database version compatibility

### Debug Logging

Enable debug logging to monitor migration progress:

```kotlin
// Migration uses the standard Ping Logger
Logger.logger = Logger.STANDARD
```

Check logs for migration status:
```
Ping SDK: Checking for legacy authenticator data
Ping SDK: Legacy repository exists, proceeding with migration
Ping SDK: Successfully exported 3 mechanisms
Ping SDK: Starting mechanisms migration to new storage
Ping SDK: Migrated OATH credential: ForgeRock - user@example.com
Ping SDK: Migrated Push credential: PingAM - admin@company.com
Ping SDK: Migrated 3 OATH credentials and 2 Push credentials
Ping SDK: Cleaning up legacy authenticator data
Ping SDK: Successfully deleted legacy authenticator data
Ping SDK: Migration completed successfully
```

### Migration State

The migration maintains state between steps:
- Exported data is stored in migration context
- Storage instances are initialized once and reused
- Failed migrations can be retried on next app start
- Successful migrations are marked complete to prevent re-running

---

## Migration Safety

The migration process is designed to be safe and non-destructive:

- **Backup Approach**: `DefaultLegacyStorageProvider` always backs up legacy SharedPreferences files before deleting them. Custom implementations of `LegacyStorageProvider.cleanUp()` can perform their own backup logic.
- **Rollback Safety**: Original data remains until migration succeeds
- **Idempotent**: Can be run multiple times safely (only runs once per install)
- **Partial Recovery**: Individual mechanism failures don't affect others
- **Cleanup Safety**: Legacy data is only deleted after successful migration

### Data Integrity

- All cryptographic secrets maintain their security properties
- User authentication credentials are preserved exactly
- Account associations remain intact
- No data is lost during migration process
- Encryption keys are properly managed throughout

### Performance Considerations

- Migration runs on a background thread (`Dispatchers.IO`)
- Doesn't block app startup
- Typically completes in 1-5 seconds for normal datasets
- Scales linearly with number of accounts/mechanisms

---

## Example Migration Scenario

### Before Migration (Legacy SDK)
```
Legacy SharedPreferences:
├── org.forgerock.android.authenticator.DATA.ACCOUNT
│   ├── "ForgeRock-user@example.com"
│   └── "PingAM-admin@company.com"
└── org.forgerock.android.authenticator.DATA.MECHANISM
    ├── "ForgeRock-user@example.com-totp"
    ├── "ForgeRock-user@example.com-push"
    └── "PingAM-admin@company.com-totp"

AndroidKeyStore:
└── "org.forgerock.android.authenticator.KEYS"
```

### After Migration (New SDK)
```
SQLOathStorage (SQLite):
├── OathCredential(issuer="ForgeRock", account="user@example.com", type=TOTP)
└── OathCredential(issuer="PingAM", account="admin@company.com", type=TOTP)

SQLPushStorage (SQLite):
└── PushCredential(issuer="ForgeRock", account="user@example.com")

Legacy Files: [Deleted]
Legacy KeyStore Entry: [Deleted]
```

---

## Requirements

- **Dependencies**:
  - `mfa:oath` - OATH credential storage
  - `mfa:push` - Push credential and notification storage
  - `foundation:migration` - Migration framework
  - `foundation:legacy-migration` - Legacy decryption utilities

---

## Additional Resources

- [Migration Framework Documentation](../../foundation/migration/README.md)
- [OATH Storage Documentation](../oath/README.md)
- [Push Storage Documentation](../push/README.md)

---

## License

This module is part of the Ping Identity SDK for Android and is subject to the MIT License. See the LICENSE file for details.

---
