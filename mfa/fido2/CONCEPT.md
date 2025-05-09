[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# FIDO2 Module Design Concept

This document explains the internal design and architecture of the FIDO2 module, focusing on how DaVinci's Collectors
and Journey's Callbacks depend on the Fido2 instance, and how it integrates with the AndroidX Credentials library.

## Overview

The FIDO2 module serves as a bridge between Ping Identity's authentication flows (DaVinci and Journey) and Android's
native FIDO2 capabilities through the AndroidX Credentials library. The `Fido2` singleton acts as a proxy, abstracting
the complexity of the underlying credential management system while providing a clean, consistent API for authentication
operations.

## Architecture Components

### Component Diagram

```mermaid
graph TB
  subgraph "Application Layer"
    App[Mobile App]
  end

  subgraph "Ping Identity SDK"
    subgraph "DaVinci Flow"
      DVC[DaVinci Collectors]
      Fido2RegCol[Fido2RegistrationCollector]
      Fido2AuthCol[Fido2AuthenticationCollector]
    end

    subgraph "Journey Flow"
      JC[Journey Callbacks]
      Fido2RegCB[Fido2RegistrationCallback]
      Fido2AuthCB[Fido2AuthenticationCallback]
    end

    subgraph "FIDO2 Core"
      Fido2Singleton[Fido2 Singleton]
    end
  end

  subgraph "Platform Interface"
    CredMgr[AndroidX Credential Manager / AuthenticationServices Framework]
  end

%% Dependencies
  App --> DVC
  App --> JC
  DVC --> Fido2RegCol
  DVC --> Fido2AuthCol
  JC --> Fido2RegCB
  JC --> Fido2AuthCB

  Fido2RegCol --> Fido2Singleton
  Fido2AuthCol --> Fido2Singleton
  Fido2RegCB --> Fido2Singleton
  Fido2AuthCB --> Fido2Singleton

  Fido2Singleton --> CredMgr

%% Styling
  classDef pingSDK fill:#e1f5fe
  classDef core fill:#f3e5f5
  classDef android fill:#e8f5e8
  classDef server fill:#fff3e0

  class DVC,JC,Fido2RegCol,Fido2AuthCol,Fido2RegCB,Fido2AuthCB pingSDK
  class Fido2Singleton,Transform core
  class CredMgr,External android
  class Server,RP server
```

## Design Principles

### 1. Singleton Pattern

The `Fido2` object is implemented as a singleton to:

- Ensure consistent state management across the application
- Provide a single point of access to FIDO2 operations
- Optimize resource usage by reusing the same instance

### 2. Proxy Pattern

The `Fido2` singleton acts as a proxy to the AndroidX Credentials library:

- **Abstraction**: Hides the complexity of AndroidX Credentials API
- **Error Handling**: Provides consistent error handling and reporting

### 3. Dependency Injection

Both DaVinci Collectors and Journey Callbacks depend on the `Fido2` singleton:

- **Loose Coupling**: Components depend on the abstraction, not implementation
- **Testability**: Easy to mock the `Fido2` instance for unit testing
- **Consistency**: All authentication flows use the same underlying FIDO2 operations

## Data Flow

### FIDO2 Registration Flow

```mermaid
sequenceDiagram
  participant App as Mobile App
  participant SDK as Ping SDK
  participant Server as Auth Server
  participant RegCol as Fido2Registration<br/>Collector/Callback
  participant Fido2 as Fido2 Singleton
  participant CredMgr as AndroidX<br/>Credential Manager
  App ->> SDK: Start Registration Flow
  SDK ->> Server: Start Registration Flow
  Server ->> SDK: Registration Challenge (JSON)
  SDK ->> RegCol: Create Fido2Registration
  RegCol ->> RegCol: Process Registration Node
  RegCol ->> SDK: Registration Collector/Callback
  SDK ->> App: Registration Collector/Callback
  App ->> RegCol: register()
  RegCol ->> Fido2: register(credentialRequest)
  Fido2 ->> CredMgr: createCredential(context, request)
  Note over CredMgr: User interaction:<br/>- Select authenticator<br/>- Biometric verification<br/>- Create credential
  CredMgr ->> Fido2: CreatePublicKeyCredentialResponse
  Fido2 ->> RegCol: Result<JsonObject>
  RegCol ->> RegCol: Process response
  RegCol ->> App: Registration Complete Result<JsonObject>
  App ->> SDK: next()
  SDK ->> Server: process node
```

### FIDO2 Authentication Flow

Similar to the registration flow, but using `Fido2AuthenticationCollector` or `Fido2AuthenticationCallback` and calling
`authenticate()` instead of `register()`.

## Key Components Explained

### Fido2 Singleton

- **Location**: `com.pingidentity.fido2.Fido2`
- **Purpose**: Central coordinator for all FIDO2 operations
- **Responsibilities**:
  - Manage AndroidX Credential Manager lifecycle
  - Handle errors and exceptions consistently
  - Ensure thread safety for concurrent operations

### DaVinci Collectors

- **Fido2RegistrationCollector**: Handles credential registration in DaVinci flows
- **Fido2AuthenticationCollector**: Handles credential authentication in DaVinci flows
- **Integration**: Called automatically when DaVinci flow encounters FIDO2 nodes

### Journey Callbacks

- **Fido2RegistrationCallback**: Handles credential registration in Journey flows
- **Fido2AuthenticationCallback**: Handles credential authentication in Journey flows
- **Integration**: Triggered when Journey tree contains FIDO2 authentication nodes