[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Device ID Module

The Device ID Module provides utilities for generating and retrieving unique device identifiers.
It supports multiple strategies, such as Android ID and keystore-based identifiers, ensuring
flexibility and security for various use cases.

## Features

- **Multiple Identifier Strategies**:
    - `AndroidIDDeviceIdentifier`: Uses Android's system-provided device identifier
    - `DefaultDeviceIdentifier`: Uses Android KeyStore for secure, persistent device identification
    - `LegacyDeviceIdentifier`: Provides compatibility with older device identification schemes
- **Secure Implementation**: Uses Android's security features like KeyStore for storing sensitive
  identifiers
- **Thread-Safe**: All implementations are thread-safe with lazy initialization patterns
- **Custom Implementation**: Define your own identifier strategy by implementing the
  `DeviceIdentifier` interface.
- **Simple API**: A consistent and easy-to-use interface for retrieving device identifiers.

## Installation

Add the module to your project using Gradle:

```gradle
implementation("com.pingidentity.sdks:device-id:<latest_version>") 
```

## Usage

### Retrieving Device Identifiers

The module provides built-in implementations for retrieving device identifiers:

#### Android ID

Use the `AndroidIDDeviceIdentifier` to retrieve the Android ID. The `ANDROID_ID` is a 64-bit unique
identifier for each device:

```kotlin
// Get the Android ID-based identifier
val androidId = AndroidIDDeviceIdentifier.id
```

**Note:**

- On devices running Android 8.0 (API level 26) and higher, `ANDROID_ID` is unique to each
  combination of app-signing key, user, and device.
- The Android ID persists across app reinstalls but may change on factory reset.

#### Default Device Identifier

The `DefaultDeviceIdentifier` provides a secure, persistent identifier using the Android KeyStore:

```kotlin
// Get the KeyStore-based identifier
val deviceId = DefaultDeviceIdentifier.id
```

The `DefaultDeviceIdentifier`:

- First attempts to retrieve a legacy identifier from the KeyStore using the Android ID as the key
  alias, if a legacy identifier is found, it is used for backward compatibility, if not found,
  creates and stores a new key pair in the Android KeyStore
- Uses the public key as the basis for the device identifier
- Hashes the key data using SHA-256 for added security
- Caches the result for efficient access

#### Legacy Device Identifier

For applications transitioning from older SDKs, the `LegacyDeviceIdentifier` provides compatibility:

```kotlin
// Get the legacy identifier (compatible with older implementations)
val legacyId = LegacyDeviceIdentifier.id
```

This implementation:

- Attempts to retrieve a key from the KeyStore using the Android ID as the key alias
- If found, creates a composite identifier compatible with legacy systems
- If not found, falls back to the `DefaultDeviceIdentifier`

### Comparison of Device Identifier Strategies

| Feature                    | AndroidIDDeviceIdentifier                              | DefaultDeviceIdentifier                                                                                 | LegacyDeviceIdentifier                                                                                  |
|----------------------------|--------------------------------------------------------|---------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| **Identifier Source**      | `Settings.Secure.ANDROID_ID`                           | KeyStore-generated RSA key pair                                                                         | KeyStore + Android ID combination or DefaultDeviceIdentifier                                            |
| **Uniqueness**             | Unique per app-signing key, user, and device (API 26+) | Unique to the app and device                                                                            | Unique to the app and device. Compatible with legacy systems                                            |
| **Persistence**            | Persists across app reinstalls                         | Generate new ID when app reinstall.                                                                     | Depends on key existence, falls back to DefaultDeviceIdentifier                                         |
| **Behavior on New Device** | Different ID per device                                | Different ID per device                                                                                 | Different ID per device                                                                                 |
| **Behavior on Reinstall**  | Consistent (API 26+)                                   | Consistent if KeyStore entries persist. However, in general KeyStore will be removed when uninstall App | Consistent if KeyStore entries persist. However, in general KeyStore will be removed when uninstall App |
| **Behavior on Data Clear** | Consistent                                             | Change when KeyStore entries are cleared                                                                | Change when KeyStore entries are cleared                                                                |
| **Security**               | System-managed, read-only                              | Cryptographically secure, hardware-backed when available                                                | Combination of system ID and cryptographic material                                                     |

### Custom Implementation

You can define a custom device identifier by implementing the `DeviceIdentifier` interface:

```kotlin
// Example: Custom Device Identifier
class CustomDeviceIdentifier : DeviceIdentifier {
    override val id: String by lazy {
        // Generate a custom identifier based on your specific requirements
        val uniqueData = "your-unique-data-source"
        MessageDigest.getInstance("SHA-256")
            .digest(uniqueData.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

// Use your custom identifier
val customId = CustomDeviceIdentifier.id
```

## Best Practices

- **Choose the right identifier for your use case**: Different identifiers have different
  persistence and security properties.
- **Handle gracefully**: Be prepared to handle cases where the device identifier changes.
- **Combine with other factors**: For authentication purposes, combine device identifiers with other
  factors like user credentials.

© Copyright 2025-2026 Ping Identity Corporation. All Rights Reserved
