<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Ping Identity Logo" width="200">
  </a>
  <hr/>
</p>

# Ping SDK - OATH MFA Module

The OATH MFA module provides functionality for implementing Time-based One-Time Password (TOTP) and HMAC-based One-Time Password (HOTP) multi-factor authentication in Android applications. This module enables applications to manage OATH credentials and generate one-time passwords for authentication.

## Features

- OATH credential management (add, retrieve, delete)
- TOTP and HOTP support
- Multiple hashing algorithms (SHA1, SHA256, SHA512)
- Customizable digit lengths (6-8 digits)
- Customizable periods for TOTP
- URI parsing and formatting

## Getting Started

### Prerequisites

- Android API level 24 or higher

### Installation

Add the following dependency to your app module's `build.gradle` file:

```gradle
dependencies {
    implementation 'com.pingidentity.sdk:mfa-oath:1.0.0'
}
```

## Usage

### Initialize the OATH Client

Before using the OATH MFA functionality, you need to initialize the `OathClient`. There are several ways to create and initialize an OathClient:

#### Basic Initialization
```kotlin
// Create a client with default configuration (not initialized)
val oathClient = OathClient.create()

// Initialize the client
oathClient.initialize()
```

#### Initialize with Custom Configuration
```kotlin
// Create with custom configuration using DSL-style builder
val oathClient = OathClient {
    enableCredentialCache = true
    encryptionEnabled = false
    // Any other configuration options
}
```

The DSL-style initialization automatically calls `initialize()` for you.

### Add a Credential from URI

Add a new OATH credential from a URI:

```kotlin
val uri = "otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&algorithm=SHA1&digits=6&period=30"
val credential = oathClient.addCredentialFromUri(uri)
```

### Generate OTP Code

Generate a one-time password for an OATH credential:

```kotlin
// Generate code for a credential by its ID
val code = oathClient.generateCode(credentialId)
```

### Retrieve Credentials

```kotlin
// Get a specific credential by ID
val credential = oathClient.getCredential(credentialId)

// Get all stored credentials
val allCredentials = oathClient.getAllCredentials()
```

### Update a Credential

```kotlin
// Update a credential's properties
credential.displayAccountName = "John Doe" // Change the display account name
val updatedCredential = oathClient.saveCredential(credential)
```

### Delete a Credential

```kotlin
// Remove a credential by ID
val isDeleted = oathClient.deleteCredential(credentialId)
```

### Extended Implementation Example

```kotlin
class OathAuthActivity : AppCompatActivity() {
    private lateinit var oathClient: OathClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oath_auth)

        // Initialize OATH client
        oathClient = OathClient {
            encryptionEnabled = false
            enableCredentialCache = true
        }

        // Load all credentials
        loadCredentials()

        // Set up button to add new credential
        btnAddCredential.setOnClickListener {
            val uri = editTextUri.text.toString()
            lifecycleScope.launch {
                try {
                    val credential = oathClient.addCredentialFromUri(uri)
                    showMessage("Credential added: ${credential.issuer}")
                    loadCredentials() // Refresh list
                } catch (e: Exception) {
                    showError("Failed to add credential: ${e.message}")
                }
            }
        }
    }

    private fun loadCredentials() {
        lifecycleScope.launch {
            try {
                val credentials = oathClient.getAllCredentials()
                // Update UI with credentials list
                credentialsAdapter.submitList(credentials)
            } catch (e: Exception) {
                showError("Failed to load credentials: ${e.message}")
            }
        }
    }

    private fun generateCodeForCredential(credentialId: String) {
        lifecycleScope.launch {
            try {
                val code = oathClient.generateCode(credentialId)
                // Display code to user
                textViewCode.text = code
            } catch (e: Exception) {
                showError("Failed to generate code: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            oathClient.close()
        }
    }
}
```

## Internal Storage

The OATH module uses the MFA common storage infrastructure to securely store credentials. By default:

- Credentials are encrypted using the Android KeyStore system
- Credentials are persisted in a SQLite database
- Sensitive data is never stored in plain text

### Customizing Storage with SQLOathStorage

You can customize the storage behavior by creating a custom instance of SQLOathStorage and passing it to the OathClient:

```kotlin
// Create a custom storage instance with specific parameters
val customStorage = SQLOathStorage {
    context = applicationContext
    databaseName = "my_custom_oath_db.db"
    passphraseProvider = NonePassphraseProvider()
}

// Create the client with the custom storage
val oathClient = OathClient {
    storage = customStorage
    enableCredentialCache = true
}

```

The custom storage options include:

- **context**: Android application context (required)
- **encryptionEnabled**: Whether to encrypt the database (default: true)
- **databaseName**: Custom database name for the SQLite database (optional)
- **secretKey**: Custom encryption key for the database (optional)
- **blockStorePreferred**: Whether to prefer Google BlockStore over local KeyStore for key storage (default: false)

If `secretKey` is not provided, the system will:
1. Try to retrieve an existing key from Google BlockStore (if `blockStorePreferred` is true)
2. Fall back to retrieving from local KeyStore if BlockStore retrieval fails
3. Generate a new secure random key if no existing key is found

## Error Handling

The OATH module provides specific exceptions for different error cases:

```kotlin
try {
    oathClient.addCredentialFromUri(uri)
} catch (e: OathUriParseException) {
    // Handle invalid URI format
} catch (e: OathCredentialException) {
    // Handle credential validation errors
} catch (e: MfaStorageException) {
    // Handle storage-related errors
} catch (e: MfaException) {
    // Handle general MFA errors
}
```

## Advanced Usage

### Custom Storage Implementation

You can implement a custom storage solution as alternative to the default `SQLOathStorage` by implementing the `OathStorage` interface:

```kotlin
class MyCustomStorage : OathStorage {
    override fun initialize() {
        // Initialize your custom storage
    }
    
    override fun close() {
        // Close storage
    }
    
    override fun clear() {
        // Remove all data
    }
    
    override fun storeOathCredential(credential: OathCredential) {
        // Store credential data
    }
    
    override fun retrieveOathCredential(credentialId: String): OathCredential? {
        // Retrieve credential data
        return null
    }
    
    override fun getAllOathCredentials(): List<OathCredential> {
        // Retrieve all credentials of a type
        return emptyList()
    }
    
    override fun removeOathCredential(credentialId: String): Boolean {
        // Delete credential data
        return true
    }
    
    override fun clearOathCredentials() {
        // Clear all credentials of a type
    }
}
```

The [`SharedPrefsOathStorage`](/mfa/oath/src/androidTest/kotlin/com/pingidentity/mfa/oath/storage/SharedPrefsOathStorage.kt) is a simple reference implementation that uses Android's SharedPreferences for storage, but it is not recommended for sensitive data like OATH credentials.

## License

Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.