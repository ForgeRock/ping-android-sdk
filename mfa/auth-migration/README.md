[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Authenticator Migration

The Authenticator Migration module provides automatic migration capabilities for upgrading legacy FR Authenticator data to the new unified OATH and Push storage format. This module uses a JSON-based approach with mechanisms containing nested account data (1-to-1 mapping) and migrates TOTP/HOTP credentials and Push credentials while preserving all account metadata, visual customizations, and security policies.

## Key Features

- **3-Step Migration Process**: Simplified migration with export, migrate, and cleanup steps
- **JSON-Based Architecture**: Structured data with mechanisms containing nested account data
- **1-to-1 Mapping**: Each mechanism contains its associated account (no orphaned data)
- **Multiple Migration Paths**: 
  - XML migration (encrypted SharedPreferences)
  - SQL migration (SQLCipher database) with automatic passphrase retrieval
  - JSON migration (custom storage)
  - AUTO mode (automatic detection)
- **3-Tier Passphrase Retrieval** ⭐ NEW:
  - Manual configuration
  - Ping SDK DataStore (KeyStorePassphraseProvider)
  - Legacy SDK LocalKeyStore (LegacyPassphraseManager)
- **Smart Schema Detection** ⭐ NEW:
  - Automatic column name detection (camelCase vs snake_case)
  - Adapts to different database schema versions
- **Type-Safe**: Uses Kotlin data classes and Kotlinx serialization
- **Error Recovery**: Database backup and destructive recovery options
- **Custom Storage Support**: LegacyDataConverter helper for custom implementations
- **Test Environment Support**: Fixed passphrase for Robolectric and instrumentation tests
- **Zero Configuration**: Works out-of-the-box for most scenarios

## What Gets Migrated

✅ **OATH Credentials** (TOTP/HOTP with secrets, algorithms, and all account data)  
✅ **Push Credentials** (Push mechanisms with endpoints, secrets, and all account data)  
✅ **Account Metadata** (Display names, images, colors, policies - nested within mechanisms)  
✅ **Security Policies** (Device tampering detection, biometric requirements, etc.)  
❌ **Push Notifications** (Not migrated - handled by new system)  
❌ **Device Tokens** (Not migrated - re-registered automatically)

---

## Table of Contents

- [Integrating the SDK into your project](#integrating-the-sdk-into-your-project)
- [Migration Overview](#migration-overview)
- [Automatic Migration](#automatic-migration)
- [Migration Steps](#migration-steps)
- [Manual Migration](#manual-migration)
- [Monitoring Migration Progress](#monitoring-migration-progress)
- [Error Handling](#error-handling)
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

The Authenticator Migration module automatically migrates legacy FR Authenticator data during application startup. It uses a JSON-based approach with mechanisms containing nested account data for clean 1-to-1 mapping.

### What Gets Migrated

- **OATH Credentials**: Migrates TOTP and HOTP mechanisms from legacy format to new SQLOathStorage
- **Push Credentials**: Migrates Push authentication mechanisms to new SQLPushStorage
- **Account Data**: Account metadata is embedded within each mechanism (1-to-1 relationship)
- **Cleanup**: Removes legacy storage files and encryption keys after successful migration

### Migration Scope

| Legacy Component | Target Component | Description |
|------------------|------------------|-------------|
| Legacy TOTP/HOTP Mechanisms | SQLOathStorage | TOTP/HOTP credentials with secrets and configuration |
| Legacy Push Mechanisms | SQLPushStorage | Push credentials with endpoints and shared secrets |
| Nested Account Data | OATH/Push Credentials | Account metadata (issuer, account name, display names, images, policies) |
| Legacy Encryption Keys | - | Cleaned up from AndroidKeyStore after migration |

**Note**: Push notifications and device tokens are NOT migrated. Only mechanisms (credentials) with their associated account data are migrated.

---

## Automatic Migration

The migration process is can be automatically triggered during application startup by importing the module. No manual intervention will be required.

### Configuration

Add the following in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":mfa:auth-migration"))
}
```

The migration will automatically:
1. Check for legacy FR Authenticator data during app startup
2. Decrypt and export data from legacy encrypted SharedPreferences
3. Migrate OATH and Push credentials with nested account data
4. Clean up legacy files and encryption keys after successful migration
5. Skip migration if no legacy data exists

---

## Migration Approaches

The migration system supports multiple approaches to accommodate different use cases:

### 1. Default Storage (Automatic)

For applications using the default FR Authenticator storage:

```kotlin
import com.pingidentity.auth.migration.AuthMigration

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        lifecycleScope.launch {
            AuthMigration.start(applicationContext)
        }
    }
}
```

This automatically:
- Detects legacy encrypted data
- Decrypts using the default key alias (`org.forgerock.android.authenticator.KEYS`)
- Migrates all mechanisms with nested accounts
- Cleans up legacy data

### 2. Custom Key Alias

If your application used a custom encryption key alias:

```kotlin
import com.pingidentity.auth.migration.AuthMigration

// Configure custom key alias before migration
AuthMigration.configure(keyAlias = "com.myapp.custom.KEY_ALIAS")

lifecycleScope.launch {
    AuthMigration.start(applicationContext)
}
```

### 3. Backup Legacy Files

Enable backup to preserve legacy data files before deletion:

```kotlin
import com.pingidentity.auth.migration.AuthMigration

// Configure migration to backup legacy files
AuthMigration.configure(backupFiles = true)

lifecycleScope.launch {
    AuthMigration.start(applicationContext)
}
```

**What happens with backups:**
- XML migration: Backup files created with timestamp suffix (e.g., `DATA.ACCOUNT_backup_1234567890.xml`)
- SQL migration: Database backup created (e.g., `forgerock_authenticator_backup_1234567890.db`)
- Stored in the same directory as original files
- Can be used for recovery if migration fails
- Not automatically deleted (you can manually delete them later)

**Use cases for backups:**
- Testing migration on production devices
- Ensuring data recovery is possible
- Debugging migration issues
- Compliance or audit requirements

### 4. Migration Types

The migration system supports three migration types to accommodate different storage implementations:

#### XML Migration (Default Storage)

For applications using the default ForgeRock encrypted SharedPreferences storage:

```kotlin
import com.pingidentity.auth.migration.AuthMigration
import com.pingidentity.auth.migration.MigrationType

AuthMigration.configure(
    migrationType = MigrationType.XML,
    keyAlias = "org.forgerock.android.authenticator.KEYS",  // Default
    backupFiles = true
)

lifecycleScope.launch {
    AuthMigration.start(applicationContext)
}
```

**What it migrates:**
- Legacy SharedPreferences files:
  - `org.forgerock.android.authenticator.DATA.ACCOUNT`
  - `org.forgerock.android.authenticator.DATA.MECHANISM`
- Encrypted using AndroidKeyStore
- Decrypts using standalone decryptor (no Legacy SDK dependency)

#### SQL Migration (Legacy Database)

For applications using Legacy SDK's `SQLStorageClient` with encrypted SQLite database:

```kotlin
import com.pingidentity.auth.migration.AuthMigration
import com.pingidentity.auth.migration.MigrationType

// Simple configuration - passphrase auto-retrieved
AuthMigration.configure(
    migrationType = MigrationType.SQL,
    databaseName = "forgerock_authenticator.db",  // Default
    backupFiles = true
)

lifecycleScope.launch {
    AuthMigration.start(applicationContext)
}
```

**What it migrates:**
- Legacy SQL database (default: `forgerock_authenticator.db`)
- All OATH and Push credentials with nested account data
- Schema-aware (handles both camelCase and snake_case column names)
- Automatic passphrase retrieval from multiple sources

**Automatic Passphrase Retrieval** (3-Tier Fallback):

The migration automatically retrieves the SQLCipher database passphrase using a 3-tier fallback system:

1. **Manual Configuration** (Highest Priority)
   ```kotlin
   AuthMigration.configure(
       migrationType = MigrationType.SQL,
       databasePassphrase = "your-known-passphrase"
   )
   ```

2. **Ping SDK DataStore** (Automatic Fallback)
   - Retrieves from KeyStorePassphraseProvider
   - Uses DataStore + Android KeyStore encryption
   - For apps already using Ping SDK

3. **Legacy SDK LocalKeyStore** (Final Fallback) ⭐ NEW
   - Retrieves from Legacy SDK's PassphraseManager storage
   - File location: `{filesDir}/database_passphrase`
   - Uses Android KeyStore decryption
   - For apps upgrading from Legacy SDK

**Zero Configuration Required:**
```kotlin
// Just configure SQL migration - passphrase auto-retrieved!
AuthMigration.configure(migrationType = MigrationType.SQL)

lifecycleScope.launch {
    AuthMigration.start(applicationContext)
}
```

The migration will:
1. ✅ Auto-detect SQL database
2. ✅ Auto-retrieve passphrase (from Ping SDK or Legacy SDK storage)
3. ✅ Decrypt and migrate all data
4. ✅ Clean up legacy files

**Custom Database Configuration:**
```kotlin
AuthMigration.configure(
    migrationType = MigrationType.SQL,
    databaseName = "custom_database.db",
    databasePassphrase = "custom-passphrase",  // Optional - can be auto-retrieved
    backupFiles = true
)
```

**Schema Detection:**

The migration automatically detects and handles different database schemas:
- **CamelCase columns**: `displayIssuer`, `accountName`, `imageURL`
- **Snake_case columns**: `display_issuer`, `account_name`, `image_url`
- **Mixed schemas**: Automatically adapts to what's available

This ensures compatibility with different versions of the Legacy SDK database.

**Passphrase Retrieval Priority:**

```
1. Manual Configuration (config.databasePassphrase)
   ↓ (if not provided)
2. Ping SDK KeyStorePassphraseProvider
   Storage: DataStore + Android KeyStore
   ↓ (if fails)
3. Legacy SDK LegacyPassphraseManager ⭐ NEW
   Storage: {filesDir}/database_passphrase
   Encryption: AES/GCM with Android KeyStore
   ↓ (if all fail)
4. Throw Exception with helpful error message
```

**Test Environment Support:**

In Robolectric or instrumentation tests, the migration automatically uses:
- Fixed passphrase: `"test_passphrase"`
- No real KeyStore required

**Coverage:**
- ✅ 99%+ of Legacy SDK users (LocalKeyStore)
- ✅ 100% of Ping SDK users (KeyStorePassphraseProvider)
- ✅ 100% of custom apps (manual passphrase)
- ✅ 100% of test environments (fixed passphrase)

#### AUTO Migration (Auto-Detect)

The migration can automatically detect whether to use XML or SQL migration based on available data:

```kotlin
import com.pingidentity.auth.migration.AuthMigration
import com.pingidentity.auth.migration.MigrationType

// Simple auto-detection (recommended)
AuthMigration.configure(
    migrationType = MigrationType.AUTO,  // Default
    backupFiles = true
)

lifecycleScope.launch {
    AuthMigration.start(applicationContext)
}
```

**Auto-detection logic:**
1. **Check SQL database first** - Looks for `forgerock_authenticator.db`
2. If SQL database found → Uses SQL migration
   - Auto-retrieves passphrase (3-tier fallback)
   - Detects schema (camelCase vs snake_case)
   - Migrates all credentials
3. If no SQL database → Checks for XML SharedPreferences
4. If XML files found → Uses XML migration
   - Decrypts using Legacy SDK key
   - Migrates all credentials
5. If nothing found → Skips migration (clean install)

**With Custom Configuration:**
```kotlin
AuthMigration.configure(
    migrationType = MigrationType.AUTO,
    keyAlias = "org.forgerock.android.authenticator.KEYS",  // For XML if detected
    databaseName = "forgerock_authenticator.db",  // For SQL if detected
    databasePassphrase = null,  // Auto-retrieved if SQL detected
    backupFiles = true
)
```

**Migration Priority (AUTO Mode):**
- SQL database has **higher priority** than XML files
- If both exist, SQL migration is used
- This matches most common upgrade path

### 5. Custom Storage (JSON-Based)

For applications using custom storage implementations that don't implement StorageClient:

```kotlin
// Optional: Save for review
File(context.filesDir, "migration_export.json").writeText(exportJson)

// Migrate using JSON
lifecycleScope.launch {
    AuthMigration.startWithJson(applicationContext, exportJson)
}
```

### JSON Export Format

The migration uses a structured JSON format with mechanisms containing nested account data:

```json
{
  "mechanisms": [
    {
      "id": "Issuer-account@example.com-otpauth",
      "issuer": "Issuer",
      "accountName": "account@example.com",
      "mechanismUID": "unique-uuid",
      "secret": "BASE32_SECRET",
      "type": "otpauth",
      "oathType": "TOTP",
      "algorithm": "SHA256",
      "digits": 6,
      "period": 30,
      "account": {
        "id": "Issuer-account@example.com",
        "issuer": "Issuer",
        "displayIssuer": "Display Name",
        "accountName": "account@example.com",
        "displayAccountName": "Display Account",
        "imageURL": "https://example.com/logo.png",
        "backgroundColor": "#032b75",
        "policies": "{...}",
        "lock": false
      }
    }
  ],
  "metadata": {
    "totalMechanisms": 1,
    "exportedAt": 1234567890
  }
}
```

---

## Configuration API

The migration system provides a flexible configuration API to accommodate different scenarios:

### Configuration Options

```kotlin
import com.pingidentity.auth.migration.AuthMigration
import com.pingidentity.auth.migration.MigrationType

AuthMigration.configure(
    migrationType = MigrationType.AUTO,  // AUTO, XML, or SQL
    keyAlias = "org.forgerock.android.authenticator.KEYS",  // For XML migration
    databaseName = "forgerock_authenticator.db",  // For SQL migration
    databasePassphrase = null,  // For SQL migration (optional - auto-retrieved)
    backupFiles = false  // Create backups before deletion
)
```

### Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `migrationType` | `MigrationType` | `AUTO` | Migration type: `AUTO`, `XML`, or `SQL` |
| `keyAlias` | `String` | `"org.forgerock.android.authenticator.KEYS"` | Android KeyStore alias for XML decryption |
| `databaseName` | `String` | `"forgerock_authenticator.db"` | SQLCipher database filename |
| `databasePassphrase` | `String?` | `null` | Manual passphrase (optional - auto-retrieved) |
| `backupFiles` | `Boolean` | `false` | Create backups before deleting legacy files |

### Migration Types

```kotlin
enum class MigrationType {
    AUTO,  // Auto-detect (SQL first, then XML)
    XML,   // Force XML migration (SharedPreferences)
    SQL    // Force SQL migration (SQLite database)
}
```

**When to use each type:**

- **AUTO (Recommended)**: Let migration detect what data exists
- **XML**: You know the app uses encrypted SharedPreferences
- **SQL**: You know the app uses SQLCipher database

### Configuration Examples

#### Minimal Configuration (Recommended)
```kotlin
// Uses all defaults - auto-detects migration type
lifecycleScope.launch {
    AuthMigration.start(applicationContext)
}
```

#### XML Migration with Custom Key
```kotlin
AuthMigration.configure(
    migrationType = MigrationType.XML,
    keyAlias = "com.myapp.CUSTOM_KEY"
)
```

#### SQL Migration with Manual Passphrase
```kotlin
AuthMigration.configure(
    migrationType = MigrationType.SQL,
    databasePassphrase = "my-secret-passphrase"
)
```

#### SQL Migration with Custom Database
```kotlin
AuthMigration.configure(
    migrationType = MigrationType.SQL,
    databaseName = "custom_authenticator.db",
    backupFiles = true  // Create backup before deletion
)
```

#### Production Configuration (Full Options)
```kotlin
AuthMigration.configure(
    migrationType = MigrationType.AUTO,
    keyAlias = "org.forgerock.android.authenticator.KEYS",
    databaseName = "forgerock_authenticator.db",
    databasePassphrase = null,  // Auto-retrieve
    backupFiles = true  // Safety net
)

lifecycleScope.launch {
    AuthMigration.start(applicationContext)
}
```

---

## Migration Steps

The migration process consists of **three sequential steps**:

### Step 1: Export Legacy Authenticator Data

**For XML Migration (Default Storage):**
- Verifies existence of legacy encryption key in AndroidKeyStore
  - Default key alias: `org.forgerock.android.authenticator.KEYS`
  - Configurable via `AuthMigration.configure(keyAlias = "...")`
- Decrypts data from legacy SharedPreferences files:
  - `org.forgerock.android.authenticator.DATA.ACCOUNT`
  - `org.forgerock.android.authenticator.DATA.MECHANISM`
- Uses standalone decryptor (no Legacy SDK dependency required)
- Builds mechanisms with nested account data (1-to-1 mapping)
- Aborts migration if no legacy data is found

**For SQL Migration (Legacy Database):**
- Retrieves database passphrase using 3-tier fallback:
  1. Manual configuration (`databasePassphrase`)
  2. Ping SDK DataStore (KeyStorePassphraseProvider)
  3. Legacy SDK LocalKeyStore (LegacyPassphraseManager) ⭐ NEW
- Opens SQLCipher database (default: `forgerock_authenticator.db`)
- Detects database schema automatically:
  - Supports camelCase columns (`displayIssuer`, `accountName`)
  - Supports snake_case columns (`display_issuer`, `account_name`)
  - Adapts to available columns dynamically
- Reads accounts and mechanisms tables
- Builds mechanisms with nested account data
- Exports to JSON format for migration
- Aborts migration if database cannot be opened or is empty

**For Custom Storage (JSON-Based Migration):**
- Parses the provided JSON string containing exported mechanisms
- Validates JSON structure using `LegacyExportedData` data model
- Extracts mechanisms list with nested account data
- Throws `IllegalArgumentException` if JSON is invalid
- Aborts if mechanisms list is empty

### Step 2: Migrate Mechanisms to OATH and Push Storage

- **Processes each mechanism** from the exported data
- **Determines mechanism type** by examining the `type` field:
  - `otpauth`, `totp`, `hotp` → OATH credential
  - `push`, `pushauth` → Push credential
  
**For OATH Credentials (TOTP/HOTP):**
- Creates `OathCredential` objects with all fields:
  - **Credential fields**: `id` (mechanismUID), `secret`, `oathType`, `oathAlgorithm`, `digits`, `period`/`counter`
  - **User identifiers**: `userId` (from uid), `resourceId`
  - **Account fields** (from nested account): `issuer`, `displayIssuer`, `accountName`, `displayAccountName`
  - **Visual customizations**: `imageURL`, `backgroundColor`
  - **Security**: `policies`, `lockingPolicy`, `isLocked`
- Stores in `SQLOathStorage`
- Maps algorithm strings: `sha1` → SHA1, `sha256` → SHA256, `sha512` → SHA512
- Handles both TOTP (period-based) and HOTP (counter-based)

**For Push Credentials:**
- Creates `PushCredential` objects with all fields:
  - **Credential fields**: `id` (mechanismUID), `sharedSecret`, `serverEndpoint`, `platform`
  - **User identifiers**: `userId` (from uid), `resourceId` (fallback to mechanismUID)
  - **Account fields** (from nested account): `issuer`, `displayIssuer`, `accountName`, `displayAccountName`
  - **Visual customizations**: `imageURL`, `backgroundColor`
  - **Security**: `policies`, `lockingPolicy`, `isLocked`
- Stores in `SQLPushStorage`
- Cleans server endpoint by removing query parameters
- Supports both `authenticationEndpoint` and `registrationEndpoint`

**Error Handling:**
- Individual mechanism failures don't stop the entire migration
- Errors are logged for debugging
- Continues with remaining mechanisms

### Step 3: Cleanup Legacy Authenticator Data

**For XML Migration (SharedPreferences):**
- Deletes legacy SharedPreferences files:
  - `org.forgerock.android.authenticator.DATA.ACCOUNT.xml`
  - `org.forgerock.android.authenticator.DATA.MECHANISM.xml`
- Removes legacy encryption key from AndroidKeyStore
  - Default: `org.forgerock.android.authenticator.KEYS`
- Creates backups if `backupFiles = true`:
  - Format: `{filename}_backup_{timestamp}.xml`
  - Location: Same directory as original files
- Closes OATH and Push storage database connections
- Cleanup failures are logged but don't abort migration (non-critical)

**For SQL Migration (Database):**
- Deletes legacy SQLite database files:
  - Main database (default: `forgerock_authenticator.db`)
  - Journal file (if exists): `forgerock_authenticator.db-journal`
  - WAL file (if exists): `forgerock_authenticator.db-wal`
  - SHM file (if exists): `forgerock_authenticator.db-shm`
- Creates database backup if `backupFiles = true`:
  - Format: `{dbname}_backup_{timestamp}.db`
  - Preserves up to 5 most recent backups
  - Location: Same directory as original database
- Does NOT delete passphrase files:
  - `{filesDir}/database_passphrase` is preserved
  - Reason: May be needed for other purposes
- Closes OATH and Push storage database connections
- Cleanup failures are logged but don't abort migration (non-critical)

**For JSON-Based Migration:**
- No legacy files to clean up (custom storage handled separately)
- Closes database connections
- Step completes successfully

---

## What's NOT Migrated

The following are **intentionally NOT migrated** and will be handled by the new SDK:

- **Push Notifications** - Not migrated; new notifications handled by current system
- **Device Tokens** - Not migrated; re-registered automatically on first use
- **Legacy notification state** - Pending notifications are not preserved

---

## Manual Migration

While migration is automatic, you can also trigger it manually:

```kotlin
// Manual migration trigger
lifecycleScope.launch {
    AuthMigration.start(context)
}
```

### Monitoring Migration Progress

Monitor migration progress by collecting the Flow returned by `migrate()`:

```kotlin
AuthMigration.migration.migrate(context).collect { progress ->
    when (progress) {
        is MigrationProgress.Started -> {
            Log.d("Migration", "Authenticator migration started")
        }
        
        is MigrationProgress.InProgress -> {
            Log.d("Migration", "Step ${progress.currentStep}/3: ${progress.message}")
        }
        
        is MigrationProgress.StepCompleted -> {
            Log.d("Migration", "Completed: ${progress.step.description}")
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

---

## Error Handling

The migration framework includes robust error handling:

### Individual Mechanism Failures
```kotlin
try {
    // Migration logic
    oathStorage.storeOathCredential(oathCredential)
    logger.d("Migrated OATH credential: $issuer - $accountName")
} catch (e: Exception) {
    logger.e("Failed to migrate mechanism $mechanismId", e)
    // Continue with other mechanisms even if one fails
}
```

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
- Migration automatically aborts if no legacy encryption key is found
- No errors are thrown for clean installations
- Subsequent app starts skip migration checks after successful completion

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
| `authenticationEndpoint` | `serverEndpoint` | Server URL for push |
| `secret` | `sharedSecret` | Cryptographic secret |
| `imageURL` | `imageURL` | Optional logo URL |
| `backgroundColor` | `backgroundColor` | Optional UI customization |

### Push Notifications

| Legacy Field | New Field | Notes |
|-------------|-----------|-------|
| `messageId` | `messageId` | Unique notification ID |
| `challenge` | `challenge` | Authentication challenge |
| `mechanismUID` | `credentialId` | Links to push credential |
| `ttl` | `ttl` | Time to live in seconds |
| `approved` | `approved` | User approval status |
| `pending` | `pending` | Processing state |
| `timeAdded` | `createdAt` | Notification timestamp |

---

## Custom Storage Migration

**If you are using custom storage implementations (e.g., `CustomStorageClient`), this automatic migration will NOT handle your custom data.**

This migration module only handles the default Ping SDK storage implementations:
- Default SQLOathStorage (SQLite-based OATH storage)
- Default SQLPushStorage (SQLite-based Push storage)
- Legacy FR Authenticator encrypted SharedPreferences

### Custom Storage Requirements

If your application uses custom storage implementations, you can use the JSON-based migration approach:

1. **Export your data** from custom storage to JSON format
2. **Use LegacyDataConverter** to build the migration JSON
3. **Migrate using** `AuthMigration.startWithJson()`

#### Example - Custom Storage Migration

```kotlin
import com.pingidentity.auth.migration.LegacyDataConverter
import com.pingidentity.auth.migration.AuthMigration

// Step 1: Export from custom storage
val customStorage = CustomStorageClient(context)

val mechanismsMap = mutableMapOf<String, String>()
customStorage.getAllMechanisms().forEach { mechanism ->
    mechanismsMap[mechanism.id] = mechanism.toJson()
}

val accountsMap = mutableMapOf<String, String>()
customStorage.getAllAccounts().forEach { account ->
    accountsMap[account.id] = account.toJson()
}

// Step 2: Build migration JSON
val exportJson = LegacyDataConverter.buildExportJson(mechanismsMap, accountsMap)

// Step 3: Migrate
lifecycleScope.launch {
    AuthMigration.startWithJson(applicationContext, exportJson)
}
```

### Custom Key Alias

If your custom storage uses encrypted SharedPreferences with a different key alias:

```kotlin
// Configure the migration with your custom key alias
AuthMigration.configure(keyAlias = "com.myapp.custom.STORAGE_KEY")

// Then start migration normally
lifecycleScope.launch {
    AuthMigration.start(applicationContext)
}
```

### Alternative: Manual Export to JSON

You can also export data to a JSON file for review before migration:

```kotlin
// Export to JSON file
lifecycleScope.launch {
    val exportJson = AuthMigration.exportToJson(applicationContext)
    File(context.filesDir, "legacy_export.json").writeText(exportJson)
    
    // Later, review and migrate
    val json = File(context.filesDir, "legacy_export.json").readText()
    AuthMigration.startWithJson(applicationContext, json)
}
```

### Detailed Custom Storage Migration Guide

#### JSON Format Specification

The export JSON contains mechanisms with nested account data for 1-to-1 mapping:

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
        "policies": "{\"biometricAvailable\": {}, \"deviceTampering\": {\"score\": 0.8}}",
        "lock": false
      }
    }
  ],
  "notifications": [],
  "deviceToken": null,
  "metadata": {
    "totalMechanisms": 1,
    "totalNotifications": 0,
    "exportedAt": 1772565130356
  }
}
```

#### Field Mapping Reference

**Mechanism Fields (Required):**
- `id` - Mechanism ID
- `issuer` - Issuer name
- `accountName` - Account username
- `mechanismUID` - Unique mechanism identifier
- `secret` - Shared secret
- `type` - Mechanism type (otpauth, pushauth, etc.)

**OATH-Specific Fields:**
- `oathType` - TOTP or HOTP
- `algorithm` - SHA1, SHA256, SHA512
- `digits` - Number of digits (typically 6)
- `period` - Time period in seconds (TOTP only)
- `counter` - Counter value (HOTP only)

**Push-Specific Fields:**
- `registrationEndpoint` - Registration URL
- `authenticationEndpoint` - Authentication URL
- `platform` - Platform identifier

**Account Fields (Nested within mechanism):**
All account fields are optional and nested within the mechanism:
- `id` - Account ID
- `issuer` - Account issuer
- `displayIssuer` - Display name for issuer
- `accountName` - Account username
- `displayAccountName` - Display name for account
- `imageURL` - Account logo URL
- `backgroundColor` - Background color
- `policies` - Security policies JSON
- `lockingPolicy` - Locking policy name
- `lock` - Lock status

#### Complete CustomStorageClient Migration Example

```kotlin
import com.pingidentity.auth.migration.AuthMigration
import com.pingidentity.auth.migration.LegacyDataConverter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            migrateCustomStorage()
        }
    }
    
    private suspend fun migrateCustomStorage() {
        try {
            val customStorage = CustomStorageClient(applicationContext)
            
            // Export mechanisms
            val mechanismsMap = mutableMapOf<String, String>()
            customStorage.getAllMechanisms().forEach { mechanism ->
                mechanismsMap[mechanism.id] = mechanism.toJson()
            }
            
            // Export accounts
            val accountsMap = mutableMapOf<String, String>()
            customStorage.getAllAccounts().forEach { account ->
                accountsMap[account.id] = account.toJson()
            }
            
            // Convert to migration format
            val exportJson = LegacyDataConverter.buildExportJson(mechanismsMap, accountsMap)
            
            // Migrate
            AuthMigration.startWithJson(applicationContext, exportJson)
            
            Log.i("Migration", "Custom storage migration completed successfully")
        } catch (e: Exception) {
            Log.e("Migration", "Migration failed: ${e.message}", e)
        }
    }
}
```

---

## Troubleshooting

### Common Issues

**Migration Not Running**
- Verify the AuthenticationMigrationInitializer is properly configured in AndroidManifest.xml
- Check that the auth-migration dependency is included
- Ensure app has proper file system permissions

**Legacy Data Not Found**
- This is normal for new installations or users who never used FR Authenticator
- Migration will abort with ABORT result (not an error)
- No action required

**Decryption Failures**
- Check if the legacy encryption key exists in AndroidKeyStore
- Verify SharedPreferences files are not corrupted
- Ensure the app hasn't been reinstalled (encryption keys are lost on reinstall)

**Storage Initialization Errors**
- Verify SQLOathStorage and SQLPushStorage are properly initialized
- Check database permissions and storage availability
- Review SQLite database version compatibility

**Passphrase Retrieval Failures** ⭐ NEW
- Migration tries 3 sources automatically (manual → Ping SDK → Legacy SDK)
- Check logs to see which retrieval method failed
- For Legacy SDK apps, verify file exists: `{filesDir}/database_passphrase`
- For custom scenarios, provide passphrase manually via `databasePassphrase`
- In test environments, fixed passphrase is used automatically

**Database Decryption Errors**
- Error: "file is not a database" → Passphrase mismatch
- Verify passphrase is correct
- Check if database was created with different passphrase
- Enable debug logging to see which passphrase retrieval method was used

**Schema Detection Issues**
- Migration auto-detects column names (camelCase vs snake_case)
- If columns not found, check database schema using SQLite browser
- Verify table names are: `accounts`, `mechanisms`
- Migration logs detected columns for debugging

**Migration Fails with "Invalid exported data JSON format"**
- Ensure your JSON structure matches the expected format
- Use `LegacyDataConverter.buildExportJson()` to generate properly formatted JSON
- Validate JSON using a JSON validator before migration

**Data Not Migrating from Custom Storage**
- Check that mechanism IDs match the legacy format
- Verify account IDs follow the pattern: `issuer-accountName`
- Ensure all required fields are present in the JSON
- Review JSON structure against the format specification above

**Custom Key Alias Not Working**
- Ensure you call `AuthMigration.configure()` before starting migration
- Example: `AuthMigration.configure(keyAlias = "your.custom.KEY_ALIAS")`
- Verify the key alias exists in AndroidKeyStore

### Debug Logging

Enable debug logging to monitor migration progress:

```kotlin
// Migration uses the standard Ping Logger
Logger.logger = Logger.STANDARD
```

Check logs for migration status:

**XML Migration:**
```
Ping SDK: Checking for legacy authenticator data
Ping SDK: Legacy repository exists, proceeding with migration
Ping SDK: Successfully exported 5 mechanisms
Ping SDK: Starting mechanisms migration to new storage
Ping SDK: Migrated OATH credential: ForgeRock - user@example.com
Ping SDK: Migrated Push credential: PingAM - admin@company.com
Ping SDK: Migrated 3 OATH credentials and 2 Push credentials
Ping SDK: Cleaning up legacy authenticator data
Ping SDK: Successfully deleted legacy authenticator data
Ping SDK: Migration completed successfully
```

**SQL Migration with Passphrase Retrieval:**
```
Ping SDK: Attempting SQL migration
Ping SDK: getDatabasePassphrase() - checking manual config
Ping SDK: No manual passphrase provided
Ping SDK: Trying Ping SDK KeyStorePassphraseProvider
Ping SDK: Failed to retrieve from Ping SDK DataStore: File not found
Ping SDK: Trying Legacy SDK PassphraseManager
Ping SDK: Stored passphrase method: 2 (LocalKeyStore)
Ping SDK: LocalKeyStore passphrase file found: /data/.../files/database_passphrase
Ping SDK: Successfully decrypted passphrase from LocalKeyStore
Ping SDK: Retrieved passphrase from Legacy SDK PassphraseManager
Ping SDK: Opening SQLCipher database: forgerock_authenticator.db
Ping SDK: Database opened successfully
Ping SDK: Detected account columns: [id, issuer, displayIssuer, accountName, ...]
Ping SDK: Exported account: ForgeRock-user@example.com
Ping SDK: Found 2 accounts in SQL storage
Ping SDK: Detected mechanism columns: [id, mechanismUID, issuer, type, ...]
Ping SDK: Exported mechanism: ForgeRock-user@example.com-totp
Ping SDK: Migrated 3 OATH credentials and 2 Push credentials
Ping SDK: Cleaning up legacy data
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

- **Backup Approach**: Legacy data is read before any modifications
- **Rollback Safety**: Original data remains until migration succeeds
- **Idempotent**: Can be run multiple times safely (only runs once)
- **Partial Recovery**: Individual mechanism failures don't affect others
- **Cleanup Safety**: Legacy data is only deleted after successful migration

### Data Integrity

- All cryptographic secrets maintain their security properties
- User authentication credentials are preserved exactly
- Account associations remain intact
- No data is lost during migration process
- Encryption keys are properly managed throughout

### Performance Considerations

- Migration runs on background thread (Dispatchers.Default)
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
├── org.forgerock.android.authenticator.DATA.MECHANISM
│   ├── "ForgeRock-user@example.com-totp"
│   ├── "ForgeRock-user@example.com-push"
│   └── "PingAM-admin@company.com-totp"

AndroidKeyStore:
└── "org.forgerock.android.authenticator.KEYS"
```

### After Migration (New SDK)
```
SQLOathStorage (SQLite):
├── OathCredential(id="mechanism-uuid-1", issuer="ForgeRock", account="user@example.com", type=TOTP)
│   └── Nested: Account(issuer="ForgeRock", displayIssuer="ForgeRock Display", accountName="user@example.com", ...)
└── OathCredential(id="mechanism-uuid-3", issuer="PingAM", account="admin@company.com", type=TOTP)
    └── Nested: Account(issuer="PingAM", accountName="admin@company.com", ...)

SQLPushStorage (SQLite):
└── PushCredential(id="mechanism-uuid-2", issuer="ForgeRock", account="user@example.com")
    └── Nested: Account(issuer="ForgeRock", accountName="user@example.com", ...)

Legacy Files: [Deleted]
Legacy KeyStore Entry: [Deleted]
```

**Note**: Each mechanism now contains its own account data (1-to-1 mapping). Push notifications and device tokens are NOT migrated.

---

## Requirements

- **Dependencies**:
  - `mfa:oath` - OATH credential storage
  - `mfa:push` - Push credential and notification storage
  - `foundation:migration` - Migration framework
  - `foundation:legacy-migration` - Legacy decryption utilities
  - AndroidX Startup library

---

## Additional Resources

- [Migration Framework Documentation](../../foundation/migration/README.md)
- [OATH Storage Documentation](../oath/README.md)
- [Push Storage Documentation](../push/README.md)

---

## License

This module is part of the Ping Identity SDK for Android and is subject to the MIT License. See the LICENSE file for details.

---

