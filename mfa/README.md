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

## Architecture Overview

The MFA module follows a modular architecture with a common foundation layer and specialized submodules for each authentication method:

```
mfa/
├── binding/           # Device Binding implementation
├── commons/           # Common foundation for all MFA methods
├── oath/              # OATH (TOTP/HOTP) implementation
├── push/              # Push notification authentication
├── fido2/             # FIDO2 (WebAuthn) implementation
└── ...
```

Each submodule can be used independently or in combination with others, allowing for a flexible implementation that meets your application's specific needs.

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All Rights Reserved