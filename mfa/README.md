<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Ping Identity Logo" width="200">
  </a>
  <hr/>
</p>

# Multi-Factor Authentication (MFA) Module

The Multi-Factor Authentication (MFA) module provides a comprehensive framework for implementing various authentication methods in Android applications using the Ping Identity SDK. It offers a unified API for multiple authentication factors including OATH (TOTP/HOTP), Push notifications, and FIDO2 (WebAuthn).

## Features

- **Unified API**: Consistent API patterns across different MFA methods
- **Modular Architecture**: Include only the MFA methods your application needs
- **Secure Storage**: Built-in encrypted storage for authentication credentials
- **Standards Compliance**: Implementation of industry standards (OATH, FIDO2)
- **Push Notifications**: Integration with Firebase Cloud Messaging (FCM)
- **Easy Configuration**: Flexible configuration options with builder and DSL patterns

## Table of Contents

- [Installation](#installation)
- [Architecture Overview](#architecture-overview)
- [Getting Started](#getting-started)
  - [Configuration](#configuration)
  - [Initialization](#initialization)
- [Module Usage](#module-usage)
  - [OATH (TOTP/HOTP)](#oath-totphotp)
  - [Push Authentication](#push-authentication)
  - [FIDO2 (WebAuthn)](#fido2-webauthn)
- [Advanced Usage](#advanced-usage)
  - [Custom Storage Implementation](#custom-storage-implementation)
  - [Security Recommendations](#security-recommendations)
- [Sample Applications](#sample-applications)
- [API Reference](#api-reference)
- [License](#license)

## Installation

Add the dependencies for the MFA modules you need in your app's `build.gradle` or `build.gradle.kts` file:

```kotlin
// For OATH (TOTP/HOTP) support
implementation("com.pingidentity.android:mfa-oath:1.0.0")

// For Push notification support
implementation("com.pingidentity.android:mfa-push:1.0.0")

// For FIDO2 (WebAuthn) support
implementation("com.pingidentity.android:mfa-fido2:1.0.0")
```

## Architecture Overview

The MFA module follows a modular architecture with a common foundation layer and specialized submodules for each authentication method:

```
mfa/
├── commons/           # Common foundation for all MFA methods
├── oath/              # OATH (TOTP/HOTP) implementation
├── push/              # Push notification authentication
├── fido2/             # FIDO2 (WebAuthn) implementation
└── ...
```

Each submodule can be used independently or in combination with others, allowing for a flexible implementation that meets your application's specific needs.

## Getting Started

### Configuration

Each MFA client can be configured using a DSL-style approach:

```kotlin
// Using the DSL-style pattern
val config = MfaConfiguration {
    encryptionEnabled = true
    timeoutMs = 30000L
    enableCredentialCache = false
    logger = customLogger
}

// Using the DSL-style pattern with client directly
val pushClient = PushClient {
    encryptionEnabled = true
    timeoutMs = 30000L
    enableCredentialCache = false
    logger = customLogger
}
```

### Initialization

Each MFA client must be initialized before use:

```kotlin
// Create and initialize a client (Java style)
val oathClient = OathClient.create(configuration)
if (!oathClient.initialize()) {
    // Handle initialization failure
}

// Create and initialize a client (Kotlin style with auto-initialization)
val pushClient = PushClient {
    // Configuration options here
}
```

## Module Usage

### OATH (TOTP/HOTP)

The OATH module supports both Time-based One-Time Passwords (TOTP) and HMAC-based One-Time Passwords (HOTP):

```kotlin
// Create an OATH client
val oathClient = OathClient {
    // Configuration options here
    enableCredentialCache = true
}

// Add a TOTP credential
val uri = "otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&algorithm=SHA1&digits=6&period=30"
val credential = oathClient.addCredentialFromUri(uri)

// Generate a TOTP code
val code = oathClient.generateCode(totpCredential.id)
```

### Push Authentication

The Push module supports Push notification-based authentication:

```kotlin
// Create a Push client
val pushClient = PushClient {
    // Configuration options here
    timeoutMs = 30000L
}

// Add a credential from a URI
val credential = pushClient.addCredentialFromUri("pushauth://push/Ping:john.doe@example.com?r=https://api.example.com/register&a=https://api.example.com/auth&s=your-shared-secret&d=user_id")

// Update the FCM device token
pushClient.updateDeviceToken("fcm-token")

// Process an incoming push message
val notification = pushClient.processPushMessage(messageData)

// Approve a push notification
val approved = pushClient.approveNotification(notification)

// Deny a push notification
val denied = pushClient.denyNotification(notification)
```

### FIDO2 (WebAuthn)

The FIDO2 module supports WebAuthn standard for passwordless authentication:

```kotlin
// Create a FIDO2 client
val fido2Client = Fido2Client {
    // Configuration options here
}

// Register a new credential
val options = Fido2RegistrationOptions.Builder()
    .setRp("example.com")
    .setUser(userId, userName, userDisplayName)
    .build()
    
val credential = fido2Client.registerCredential(options)

// Authenticate with a credential
val assertion = fido2Client.authenticate(authenticationOptions)
```

### Security Recommendations

1. **Enable Encryption**: Always enable encryption for sensitive data storage (default)
   ```kotlin
   .encryptionEnabled(true)
   ```

2. **Disable Credential Caching**: Unless absolutely necessary, keep credential caching disabled (default)
   ```kotlin
   .enableCredentialCache(false)
   ```

3. **Implement Proper Lifecycle Management**: Always close the client when it's no longer needed
   ```kotlin
   override fun onDestroy() {
       super.onDestroy()
       mfaClient.close()
   }
   ```

## Sample Applications

Check out our sample application demonstrating the use of the MFA module:

- [Authenticator Sample](https://github.com/ForgeRock/ping-android-sdk/tree/main/samples/authenticatorapp)

## API Reference

For detailed documentation, visit the [Ping SDKs](https://docs.pingidentity.com/sdks/latest/index.html).

## License

Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
This software may be modified and distributed under the terms of the MIT license. See the [LICENSE](../LICENSE) file for details.
