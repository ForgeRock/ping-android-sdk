
[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Ping SDK - MFA Commons Module

The MFA Commons module provides the core foundation and shared functionality for all Multi-Factor Authentication (MFA) modules within the Ping Identity SDK. It includes base implementations for clients, storage, configuration, and other common utilities that are leveraged by specialized MFA modules like OATH, Push, and FIDO2.

## Features

- **Base MFA Client**: A base client implementation that provides common functionality for all MFA clients.
- **Secure Storage**: A common storage infrastructure for securely storing credentials and other sensitive data.
- **URI Parsing**: A flexible URI parser for handling different MFA registration URIs.
- **Common Exceptions**: A set of common exceptions for handling MFA-related errors.
- **Flexible Configuration**: A common configuration pattern for all MFA clients.
- **Common Utilities**: A set of common utilities that are leveraged by specialized MFA modules.

## Getting Started

### Prerequisites

- Android API level 24 or higher

### Installation

The MFA Commons module is included as a transitive dependency when you add any of the other MFA modules. You do not need to add it explicitly to your `build.gradle` file.

```gradle
// Included automatically with other MFA modules
implementation 'com.pingidentity.sdk:mfa-oath:<version>'
implementation 'com.pingidentity.sdk:mfa-push:<version>'
```

## Usage

The MFA Commons module is not intended to be used directly by applications. Instead, it provides the underlying functionality for the other MFA modules. For usage examples, please refer to the documentation for the specific MFA module you are using:

- [OATH Module](../oath/README.md)
- [Push Module](../push/README.md)

## Internal Storage

The MFA Commons module provides a common storage infrastructure that is used by all MFA modules. By default:

- Credentials are encrypted using the Android KeyStore system
- Credentials are persisted in a SQLite database
- Sensitive data is never stored in plain text

You can customize the storage behavior by creating a custom instance of `MfaStorage` and passing it to the specific MFA client you are using.

## Error Handling

The MFA Commons module provides a set of common exceptions that are used by all MFA modules. These exceptions are designed to provide a consistent error handling experience across all MFA methods. For more information on error handling, please refer to the documentation for the specific MFA module you are using.

## License

Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.
