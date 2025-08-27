[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Ping SDK - OATH MFA Module

The OATH MFA module provides functionality for implementing Time-based One-Time Password (TOTP) and HMAC-based One-Time Password (HOTP) multi-factor authentication in Android applications. This module enables applications to manage OATH credentials and generate one-time passwords for authentication.

## Features

- OATH credential management (add, retrieve, delete)
- TOTP and HOTP support
- Multiple hashing algorithms (SHA1, SHA256, SHA512)
- Customizable digit lengths (6-8 digits)
- Customizable periods for TOTP
- URI parsing and formatting
- Kotlin Result-based API for improved error handling

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
val oathClient = OathClient()

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

// Using onSuccess/onFailure callbacks
oathClient.addCredentialFromUri(uri).onSuccess { credential ->
    // Handle the successfully created credential
    println("Created credential: ${credential.issuer}")
}.onFailure { exception ->
    // Handle error
    println("Failed to add credential: ${exception.message}")
}

// Alternative: Using getOrThrow()
try {
    val credential = oathClient.addCredentialFromUri(uri).getOrThrow()
    // Use credential
} catch (e: Exception) {
    // Handle exception
}

// Alternative: Using getOrNull() for a nullable result
val credential = oathClient.addCredentialFromUri(uri).getOrNull()
if (credential != null) {
    // Use credential
} else {
    // Handle null case
}
```

### Generate OTP Code

Generate a one-time password for an OATH credential:

```kotlin
// Generate code for a credential by its ID
oathClient.generateCode(credentialId).onSuccess { code ->
    // Use the generated code
    displayCode(code)
}.onFailure { exception ->
    // Handle error
    showError("Failed to generate code: ${exception.message}")
}

// With timing information
oathClient.generateCodeWithValidity(credentialId).onSuccess { codeInfo ->
    displayCode(codeInfo.code)
    updateProgressBar(codeInfo.progress)
    startCountdown(codeInfo.timeRemaining)
}
```

### Retrieve Credentials

```kotlin
// Get a specific credential by ID
oathClient.getCredential(credentialId).onSuccess { credential ->
    if (credential != null) {
        // Credential found, use it
        displayCredential(credential)
    } else {
        // Credential not found
        showMessage("Credential not found")
    }
}

// Get all stored credentials
oathClient.getCredentials().onSuccess { credentials ->
    if (credentials.isEmpty()) {
        showMessage("No credentials found")
    } else {
        displayCredentials(credentials)
    }
}
```

### Update a Credential

```kotlin
// Update a credential's properties
credential.displayAccountName = "John Doe" // Change the display account name

oathClient.saveCredential(credential).onSuccess { updatedCredential ->
    // Handle successful update
    showMessage("Credential updated")
}.onFailure { exception ->
    // Handle failure
    showError("Failed to update credential: ${exception.message}")
}
```

### Delete a Credential

```kotlin
// Remove a credential by ID
oathClient.deleteCredential(credentialId).onSuccess { isDeleted ->
    if (isDeleted) {
        showMessage("Credential deleted")
    } else {
        showMessage("Credential not found")
    }
}.onFailure { exception ->
    showError("Failed to delete credential: ${exception.message}")
}
```

### Extended Implementation Example

```kotlin
class OathAuthActivity : AppCompatActivity() {
    private lateinit var oathClient: OathMfaClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oath_auth)

        // Initialize OATH client
        lifecycleScope.launch {
            try {
                oathClient = OathClient {
                    encryptionEnabled = false
                    enableCredentialCache = true
                }
                
                // Load all credentials
                loadCredentials()
            } catch (e: Exception) {
                showError("Failed to initialize: ${e.message}")
            }
        }

        // Set up button to add new credential
        btnAddCredential.setOnClickListener {
            val uri = editTextUri.text.toString()
            lifecycleScope.launch {
                oathClient.addCredentialFromUri(uri).onSuccess { credential ->
                    showMessage("Credential added: ${credential.issuer}")
                    loadCredentials() // Refresh list
                }.onFailure { e ->
                    showError("Failed to add credential: ${e.message}")
                }
            }
        }
    }

    private fun loadCredentials() {
        lifecycleScope.launch {
            oathClient.getCredentials().onSuccess { credentials ->
                // Update UI with credentials list
                credentialsAdapter.submitList(credentials)
            }.onFailure { e ->
                showError("Failed to load credentials: ${e.message}")
            }
        }
    }

    private fun generateCodeForCredential(credentialId: String) {
        lifecycleScope.launch {
            oathClient.generateCode(credentialId).onSuccess { code ->
                // Display code to user
                textViewCode.text = code
            }.onFailure { e ->
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
- **databaseVersion**: Custom database version (default: 1)
- **passphraseProvider**: Custom passphrase provider for database encryption (default: uses Android KeyStore)

## Error Handling

The OATH module uses Kotlin's Result API for error handling, providing a more functional approach:

```kotlin
// Using onSuccess/onFailure
oathClient.addCredentialFromUri(uri)
    .onSuccess { credential ->
        // Success path
    }
    .onFailure { exception ->
        when (exception) {
            is IllegalArgumentException -> // Handle invalid URI format
            is MfaException -> // Handle general MFA errors
            else -> // Handle other exceptions
        }
    }

// Using fold for combined handling
oathClient.addCredentialFromUri(uri).fold(
    onSuccess = { credential -> 
        // Handle success
    },
    onFailure = { exception ->
        // Handle failure
    }
)

// Using runCatching for additional operations
runCatching { 
    oathClient.addCredentialFromUri(uri).getOrThrow()
}.onSuccess { credential ->
    // Do something with credential
}.onFailure { exception ->
    // Handle error
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