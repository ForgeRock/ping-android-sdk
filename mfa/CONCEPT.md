<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Ping Identity Logo" width="200">
  </a>
  <hr/>
</p>

# Unified Multi-Factor Authentication (MFA) Module Concept

## Overview

The Unified Multi-Factor Authentication (MFA) module provides a comprehensive framework for implementing various authentication methods in Android applications using the Ping Identity SDK. The module is designed with flexibility, security, and ease-of-use as primary objectives, offering support for multiple authentication factors including OATH (TOTP/HOTP), Push notifications, FIDO2 (WebAuthn), and others.

The module is designed to be forward-compatible, allowing for future authentication methods to be added without disrupting existing integrations. It also provides a smooth migration path for applications currently using other authentication libraries.

## Goals

- Create a unified API for various MFA methods to simplify developer experience
- Provide secure storage and management of authentication credentials
- Support multiple authentication protocols (OATH, Push notifications, FIDO2, and others)
- Ensure backward compatibility with existing implementations
- Maintain a high level of security for sensitive operations
- Enable easy integration with Ping Identity products and services

## Architecture

The MFA module follows a modular architecture with a common foundation layer and specialized submodules for each authentication method. This design allows developers to include only the MFA methods they need, minimizing the application's footprint.

### Key Components

#### Commons Module

The commons module provides the foundation for all MFA implementations, including:

- Base interfaces and abstract classes for MFA clients
- Configuration management
- Secure storage functionality
- Exception handling
- Common utilities

#### Authentication Method-specific Modules

Each authentication method has its own module that implements the specific protocols and functionality required:

- **OATH Module**: Implements Time-based One-Time Password (TOTP) and HMAC-based One-Time Password (HOTP)
- **Push Module**: Implements Push notification-based authentication
- **FIDO2 Module**: Implements FIDO2 WebAuthn standard for passwordless authentication

## Class Diagrams

### Commons Module

```
┌───────────────────┐     ┌────────────────────┐
│   MfaClient       │     │  MfaConfiguration  │
│   (interface)     │     │                    │
└─────────┬─────────┘     └────────────────────┘
          │                         ▲
          │                         │
┌─────────▼─────────┐     ┌─────────┴──────────┐
│  BaseMfaClient    │     │MfaConfiguration.   │
│  (abstract)       │◄────┤Builder             │
└─────────┬─────────┘     └────────────────────┘
          │
          ├─────────────┬──────────────┐
          │             │              │
┌─────────▼──────┐ ┌────▼─────┐  ┌─────▼─────┐
│  OathClient    │ │PushClient│  │Fido2Client│
└────────────────┘ └──────────┘  └───────────┘

┌───────────────────┐     ┌────────────────────┐
│  MfaStorageClient │     │  StorageType       │
│  (interface)      │     │  (enum)            │
└─────────┬─────────┘     └────────────────────┘
          │
          │
┌─────────▼─────────┐
│ SQLStorageClient  │
└───────────────────┘
```

### OATH Module

```
┌───────────────────┐     ┌────────────────────┐
│   MfaOathClient   │     │  OathCredential    │
│   (interface)     │     │                    │
└─────────┬─────────┘     └────────────────────┘
          │                         ▲
          │                         │
┌─────────▼─────────┐     ┌─────────┴──────────┐
│    OathClient     │     │   OathType         │
│                   │     │   (enum)           │
└─────────┬─────────┘     └────────────────────┘
          │
          │
┌─────────▼─────────┐
│   OathService     │
└───────────────────┘
```

### Push Module

```
┌───────────────────┐     ┌────────────────────┐
│   MfaPushClient   │     │   PushCredential   │
│   (interface)     │     │                    │
└─────────┬─────────┘     └────────────────────┘
          │                         ▲
          │                         │
┌─────────▼─────────┐     ┌─────────┴──────────┐
│    PushClient     │     │   PushNotification │
│                   │◄────┤                    │
└─────────┬─────────┘     └────────────────────┘
          │
          │
┌─────────▼─────────┐
│   PushService     │
└───────────────────┘
```

### FIDO2 Module

```
┌───────────────────┐     ┌────────────────────┐
│   Fido2Client     │     │   Fido2Credential  │
│   (interface)     │     │                    │
└─────────┬─────────┘     └────────────────────┘
          │                         ▲
          │                         │
┌─────────▼─────────┐     ┌─────────┴──────────┐
│    Fido2Client    │     │   Fido2Options     │
│    Implementation │     │                    │
└─────────┬─────────┘     └────────────────────┘
          │
          │
┌─────────▼─────────┐
│   Fido2Service    │
└───────────────────┘
```

## Module Interaction

```
                 ┌─────────────────────────────┐
                 │      Application Layer      │
                 └───────────────┬─────────────┘
                                 │
                                 ▼
            ┌─────────────────────────────────────┐
            │  MFA Module Integration Interface   │
            └──────┬──────────────────┬───────────┘
                   │                  │
         ┌─────────▼──────┐  ┌────────▼────────┐
         │  MFA Commons   │  │  Multi-factor   │
         │  Foundation    │◄─┤  Orchestration  │
         └─────┬──────────┘  └────────┬────────┘
               │                      │
     ┌─────────┴──────────┬───────────┴───────┐
     ▼                    ▼                   ▼
┌─────────┐         ┌─────────┐         ┌─────────┐
│  OATH   │         │  Push   │         │  FIDO2  │
│ Module  │         │ Module  │         │ Module  │
└────┬────┘         └────┬────┘         └────┬────┘
     │                   │                   │
     ▼                   ▼                   ▼
┌─────────┐         ┌──────────┐         ┌─────────┐
│ Security│         │Network & │         │ WebAuthn│
│ Layer   │         │ Firebase │         │   API   │
└─────────┘         └──────────┘         └─────────┘
```

## Key Workflows

### MFA Client Initialization

```
┌───────────────┐    ┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│  Application  │    │   MfaClient   │    │StorageClient  │    │  Core Module  │
└───────┬───────┘    └───────┬───────┘    └───────┬───────┘    └───────┬───────┘
        │                    │                    │                    │
        │ create()           │                    │                    │
        ├───────────────────►│                    │                    │
        │                    │                    │                    │
        │                    │ initialize()       │                    │
        │ initialize()       │ internal           │                    │
        ├───────────────────►│                    │                    │
        │                    │                    │                    │
        │                    │ initializeStorage()│                    │
        │                    ├───────────────────►│                    │
        │                    │                    │                    │
        │                    │                    │ setupStorage()     │
        │                    │                    ├───────────────────►│
        │                    │                    │                    │
        │                    │                    │ Success            │
        │                    │                    │◄───────────────────┤
        │                    │                    │                    │
        │                    │ Success            │                    │
        │                    │◄───────────────────┤                    │
        │                    │                    │                    │
        │                    │ initializeClient() │                    │
        │                    ├────────────────────┤                    │
        │                    │                    │                    │
        │ Success            │                    │                    │
        │◄───────────────────┤                    │                    │
        │                    │                    │                    │
```

### Credential Management

```
┌───────────────┐    ┌───────────────┐    ┌───────────────┐    ┌────────────────┐
│  Application  │    │   MfaClient   │    │ Secure Storage│    │MFA Type Service│
└───────┬───────┘    └───────┬───────┘    └───────┬───────┘    └───────┬────────┘
        │                    │                    │                    │
        │ addCredential()    │                    │                    │
        ├───────────────────►│                    │                    │
        │                    │                    │                    │
        │                    │ checkInitialized() │                    │
        │                    ├───────────────────►│                    │
        │                    │                    │                    │
        │                    │ processCredential()│                    │
        │                    ├───────────────────────────────────────► │
        │                    │                    │                    │
        │                    │                    │                    │
        │                    │                    │                    │ validate()
        │                    │                    │                    ├─────────►
        │                    │                    │                    │
        │                    │                    │                    │ prepare()
        │                    │                    │                    ├─────────►
        │                    │                    │                    │
        │                    │                    │ storeCredential()  │
        │                    │                    │◄───────────────────┤
        │                    │                    │                    │
        │                    │                    │ Success            │
        │                    │                    ├───────────────────►│
        │                    │                    │                    │
        │                    │ Success            │                    │
        │                    │◄────────────────────────────────────────┤
        │                    │                    │                    │
        │ Credential         │                    │                    │
        │◄───────────────────┤                    │                    │
        │                    │                    │                    │
```

## Security Considerations

### Secure Storage

The MFA module uses encryption to protect sensitive credential data:

1. **Key Storage**: Cryptographic keys are stored in the Android KeyStore system
2. **Encrypted Storage**: Credentials are encrypted before being stored in the database
3. **Memory Protection**: In-memory credential caching is disabled by default and can be enabled only when explicitly configured

### Authentication Integrity

1. **Tamper Protection**: Credentials include integrity verification mechanisms
2. **Signature Validation**: Push notifications include signatures that are validated before processing
3. **FIDO2 Attestation**: FIDO2 credentials include attestation data for validation


## License

Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.
