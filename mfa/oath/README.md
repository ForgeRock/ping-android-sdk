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
    timeoutMs = 60000
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

### Get Credentials

Retrieve all stored credentials:

```kotlin
val credentials = oathClient.getCredentials()
```

### Remove a Credential

Remove an OATH credential:

```kotlin
val removed = oathClient.removeCredential(credentialId)
if (removed) {
    // Update UI or notify user
}
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
            encryptionEnabled(true)
            enableCredentialCache(true)
        }
        
        // Load all credentials
        loadCredentials()
        
        // Set up button to add new credential
        btnAddCredential.setOnClickListener {
            val uri = editTextUri.text.toString()
            try {
                val credential = oathClient.addCredentialFromUri(uri)
                showMessage("Credential added: ${credential.issuer}")
                loadCredentials() // Refresh list
            } catch (e: Exception) {
                showError("Failed to add credential: ${e.message}")
            }
        }
    }
    
    private fun loadCredentials() {
        try {
            val credentials = oathClient.getCredentials()
            // Update UI with credentials list
        } catch (e: Exception) {
            showError("Failed to load credentials: ${e.message}")
        }
    }
    
    private fun generateCodeForCredential(credentialId: String) {
        try {
            val code = oathClient.generateCode(credentialId)
            // Display code to user
            textViewCode.text = code
        } catch (e: Exception) {
            showError("Failed to generate code: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        oathClient.close()
    }
}
```

## License

Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.
