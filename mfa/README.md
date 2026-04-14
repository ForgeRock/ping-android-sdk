[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

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
implementation("com.pingidentity.android:mfa:oath:<version>")

// For Push notification support
implementation("com.pingidentity.android:mfa:push:<version>")

// For FIDO2 (WebAuthn) support
implementation("com.pingidentity.android:mfa:fido2:<version>")
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
val config = PushConfiguration {
    timeoutMs = 30000L
    enableCredentialCache = false
    logger = customLogger
}

// Using the DSL-style pattern with client directly
val pushClient = PushClient {
    timeoutMs = 30000L
    enableCredentialCache = false
    logger = customLogger
}

// Encryption is configured at the storage level:
val pushClient = PushClient {
    storage = SQLPushStorage {
        context = applicationContext
        passphraseProvider = NonePassphraseProvider() // For unencrypted storage
    }
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
pushClient.addCredentialFromUri(uri).onSuccess { credential ->
    // Handle the successfully created credential
    println("Created credential: ${credential.issuer}")
}.onFailure { exception ->
    // Handle error
    println("Failed to add credential: ${exception.message}")
}

// Generate a TOTP code
oathClient.generateCode(credentialId).onSuccess { code ->
    // Use the generated code
    displayCode(code)
}.onFailure { exception ->
    // Handle error
    showError("Failed to generate code: ${exception.message}")
}
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
val uri = "pushauth://push/Ping:john.doe@example.com?r=https://api.example.com/register&a=https://api.example.com/auth&s=your-shared-secret&d=user_id"
pushClient.addCredentialFromUri(uri).onSuccess { credential ->
    // Handle the successfully created credential
    println("Created credential: ${credential.issuer}")
}.onFailure { exception ->
    // Handle error
    println("Failed to add credential: ${exception.message}")
}

// Update the FCM device token
pushClient.setDeviceToken("fcm-token").onSuccess {
    // Device token updated successfully
    println("Device token updated")
}.onFailure { exception ->
    // Handle error
    println("Failed to update device token: ${exception.message}")
}

// Process an incoming push message
pushClient.processPushMessage(messageData).onSuccess { notification ->
    // Handle the processed notification
    println("Received notification: ${notification.id}")
}.onFailure { exception ->
    // Handle error
    println("Failed to process push message: ${exception.message}")
}

// Approve a push notification
pushClient.approveNotification(notification).onSuccess {
    // Notification approved successfully
    println("Notification approved")
}.onFailure { exception ->
    // Handle error
    println("Failed to approve notification: ${exception.message}")
}

// Deny a push notification
pushClient.denyNotification(notification).onSuccess {
    // Notification denied successfully
    println("Notification denied")
}.onFailure { exception ->
    // Handle error
    println("Failed to deny notification: ${exception.message}")
}
```

### FIDO2 (WebAuthn)

TBD

## Sample Applications

Check out our sample application demonstrating the use of the MFA module:

- [Authenticator Sample](https://github.com/ForgeRock/ping-android-sdk/tree/main/samples/authenticatorapp)

## API Reference

For detailed documentation, visit the [Ping SDKs](https://docs.pingidentity.com/sdks/latest/index.html).

## License

Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
This software may be modified and distributed under the terms of the MIT license. See the [LICENSE](../LICENSE) file for details.