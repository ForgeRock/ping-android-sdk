<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Logo">
  </a>
  <hr/>
</p>

# Design Concept

## Overview

The Device Binding module provides secure device registration and authentication capabilities for Android applications. It enables applications to bind cryptographic keys to devices and perform step-up authentication using various authentication methods including biometric, PIN, or no authentication.

The module follows a plugin-based architecture where different authentication types are handled by specific authenticator implementations, all unified under the `DeviceAuthenticator` interface.

## Core Architecture

### Module Dependency Diagram

```mermaid
graph TD
    subgraph Core ["Core Module"]
        BINDING[mfa:binding]
    end

    subgraph Optional ["Optional Modules"]
        UI[mfa:binding-ui]
        MIGRATION[mfa:binding-migration]
    end

    subgraph External ["External Dependencies"]
        COMPOSE[Jetpack Compose]
    end

    UI --> BINDING
    MIGRATION --> BINDING
    UI --> COMPOSE

    classDef coreModule fill:#e1f5fe,stroke:#0277bd,stroke-width:2px
    classDef optionalModule fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    classDef externalDep fill:#fff3e0,stroke:#f57c00,stroke-width:2px

    class BINDING coreModule
    class UI,MIGRATION optionalModule
    class COMPOSE externalDep
```

**Module Dependencies:**
- **mfa:binding-ui** → depends on **mfa:binding** (provides default UI components)
- **mfa:binding-migration** → depends on **mfa:binding** (provides legacy SDK migration)
- **mfa:binding** → depends on **BouncyCastle** (required for Application PIN authentication)
- **mfa:binding-ui** → depends on **Jetpack Compose** (for PIN collection dialog and user selection UI)
- **mfa:binding-migration** → accesses **Legacy ForgeRock SDK Data** (for automatic migration)

### Component Diagram

```mermaid
graph TB
    subgraph JI ["Journey Integration"]
        JC[Journey Callback]
        DBC[DeviceBindingCallback]
        DSVC[DeviceSigningVerifierCallback]
    end

    subgraph CL ["Configuration Layer"]
        CONF[DeviceBindingConfig]
        UKSC[UserKeyStorageConfig]
        APC[AppPinConfig]
        BAC[BiometricAuthenticatorConfig]
    end

    subgraph AL ["Authentication Layer"]
        DA[DeviceAuthenticator Interface]
        BA[BiometricAuthenticator]
        APA[AppPinAuthenticator]
        BOA[BiometricOnlyAuthenticator]
        BAFA[BiometricAllowFallbackAuthenticator]
        NA[None Authenticator]
    end

    subgraph CR ["Cryptographic Layer"]
        CK[CryptoKey]
        KP[KeyPair]
        AKS[Android KeyStore]
    end

    subgraph SL ["Storage Layer"]
        UKS[UserKeysStorage]
        EDSS[EncryptedDataStoreStorage]
        UK[UserKey]
    end

    subgraph UL ["UI Layer"]
        PC[PinCollector]
        UKD[UserKeyDialog]
        BP[BiometricPrompt]
    end

%% Main flow connections
    JC --> DBC
    JC --> DSVC
    DBC --> CONF
    DSVC --> CONF
    CONF --> DA
    CONF --> UKS
    DA --> CK
    DA --> UKS
    CK --> AKS
    UKS --> EDSS
    UKS --> UK

%% UI connections
    BA --> BP
    APA --> PC
    BOA --> BP
    BAFA --> BP

%% Inheritance relationships
    DA -.-> BA
    DA -.-> APA
    DA -.-> BOA
    DA -.-> BAFA
    DA -.-> NA
    BA --> BOA
    BA --> BAFA
```

### DeviceAuthenticator Class Hierarchy

```mermaid
classDiagram
    class DeviceAuthenticator {
        <<interface>>
        +DeviceBindingAuthenticationType type
        +suspend register(Context, Attestation): Result~KeyPair~
        +suspend authenticate(Context): Result~Pair~PrivateKey, CryptoObject?~~
        +isSupported(Context, Attestation): Boolean
        +suspend deleteKeys()
        +sign(SigningParameters): String
        +sign(UserKeySigningParameters): String
    }

    class CryptoKeyAware {
        <<interface>>
        +CryptoKey cryptoKey
    }

    class BiometricAuthenticator {
        +BiometricAuthenticatorConfig config
        +suspend register(Context, Attestation): Result~KeyPair~
        +suspend authenticate(Context): Result~Pair~PrivateKey, CryptoObject?~~
    }

    class BiometricOnlyAuthenticator {
        +suspend register(Context, Attestation): Result~KeyPair~
        +suspend authenticate(Context): Result~Pair~PrivateKey, CryptoObject?~~
    }

    class BiometricAllowFallbackAuthenticator {
        +suspend register(Context, Attestation): Result~KeyPair~
        +suspend authenticate(Context): Result~Pair~PrivateKey, CryptoObject?~~
    }

    class AppPinAuthenticator {
        +AppPinConfig config
        +suspend register(Context, Attestation): Result~KeyPair~
        +suspend authenticate(Context): Result~Pair~PrivateKey, CryptoObject?~~
        +suspend deleteKeys()
    }

    class None {
        +suspend register(Context, Attestation): Result~KeyPair~
        +suspend authenticate(Context): Result~Pair~PrivateKey, CryptoObject?~~
    }

    DeviceAuthenticator <|.. BiometricAuthenticator
    DeviceAuthenticator <|.. AppPinAuthenticator
    DeviceAuthenticator <|.. None
    BiometricAuthenticator <|-- BiometricOnlyAuthenticator
    BiometricAuthenticator <|-- BiometricAllowFallbackAuthenticator
    CryptoKeyAware <|.. BiometricAuthenticator
    CryptoKeyAware <|.. AppPinAuthenticator
    CryptoKeyAware <|.. None
```

## Design Patterns

### Factory Pattern
The module uses a factory pattern for creating appropriate authenticators based on authentication type:

```kotlin
internal fun authenticator(
    type: DeviceBindingAuthenticationType,
    prompt: Prompt
): DeviceAuthenticator {
    return if (::deviceAuthenticator.isInitialized) {
        deviceAuthenticator(type)
    } else {
        when (type) {
            BIOMETRIC_ONLY -> BiometricOnlyAuthenticator(createBiometricConfig(prompt))
            BIOMETRIC_ALLOW_FALLBACK -> BiometricAllowFallbackAuthenticator(createBiometricConfig(prompt))
            APPLICATION_PIN -> AppPinAuthenticator(createAppPinConfig(prompt))
            NONE -> None()
        }
    }
}
```

### Strategy Pattern
Different authentication strategies are encapsulated in separate authenticator classes, allowing runtime selection based on configuration.

### Template Method Pattern
The `DeviceAuthenticator` interface defines the template for authentication operations, with concrete implementations providing specific behaviors.

## Sequence Diagrams

### Device Binding Flow

```mermaid
sequenceDiagram
    participant App as Application
    participant DBC as DeviceBindingCallback
    participant Config as DeviceBindingConfig
    participant Auth as DeviceAuthenticator
    participant CK as CryptoKey
    participant AKS as Android KeyStore
    participant Storage as UserKeysStorage
    participant UI as Authentication UI

    App->>DBC: bind(config)
    DBC->>Config: apply configuration
    DBC->>Auth: isSupported(context, attestation)
    Auth-->>DBC: true

    DBC->>Auth: deleteKeys() [cleanup]
    Auth->>AKS: delete existing keys

    DBC->>Auth: register(context, attestation)
    Auth->>CK: keyBuilder()
    Auth->>CK: create(spec)
    CK->>AKS: generateKeyPair()
    AKS-->>CK: KeyPair
    CK-->>Auth: KeyPair
    Auth-->>DBC: Result<KeyPair>

    DBC->>Auth: authenticate(context)
    Auth->>UI: show authentication prompt
    UI-->>Auth: user authenticated
    Auth->>AKS: getPrivateKey()
    AKS-->>Auth: PrivateKey
    Auth-->>DBC: Result<PrivateKey, CryptoObject>

    DBC->>Auth: sign(SigningParameters)
    Auth->>Auth: create JWT
    Auth->>Auth: sign with private key
    Auth-->>DBC: signed JWT

    DBC->>Storage: save(UserKey)
    Storage-->>DBC: success

    DBC-->>App: Result<String> [signed JWT]
```

### Device Signing Verification Flow

```mermaid
sequenceDiagram
    participant App as Application
    participant DSVC as DeviceSigningVerifierCallback
    participant Config as DeviceBindingConfig
    participant Storage as UserKeysStorage
    participant Auth as DeviceAuthenticator
    participant UI as Authentication UI
    participant AKS as Android KeyStore

    App->>DSVC: sign(config)
    DSVC->>Config: apply configuration
    DSVC->>DSVC: validate(customClaims)

    alt userId specified
        DSVC->>Storage: findByUserId(userId)
    else no userId
        DSVC->>Storage: findAll()
        DSVC->>Config: userKeySelector(keys)
        Config-->>DSVC: selected UserKey
    end
    Storage-->>DSVC: UserKey

    DSVC->>Config: authenticator(authType, prompt)
    Config-->>DSVC: DeviceAuthenticator

    DSVC->>Auth: authenticate(context)
    Auth->>UI: show authentication prompt
    UI-->>Auth: user authenticated
    Auth->>AKS: getPrivateKey(keyAlias)
    AKS-->>Auth: PrivateKey
    Auth-->>DSVC: Result<PrivateKey, CryptoObject>

    DSVC->>Auth: sign(UserKeySigningParameters)
    Auth->>Auth: create JWT with custom claims
    Auth->>Auth: sign with private key
    Auth-->>DSVC: signed JWT

    DSVC-->>App: Result<String> [signed JWT]
```

### Biometric Authentication Flow

```mermaid
sequenceDiagram
    participant BA as BiometricAuthenticator
    participant BP as BiometricPrompt
    participant AKS as Android KeyStore
    participant System as Android System

    BA->>AKS: getPrivateKey(keyAlias)
    AKS-->>BA: PrivateKey

    BA->>System: Signature.getInstance("SHA256withRSA")
    System-->>BA: Signature

    BA->>System: signature.initSign(privateKey)

    BA->>BP: BiometricPrompt.Builder()
    BA->>BP: setTitle(), setSubtitle(), setDescription()
    BA->>BP: setCryptoObject(signature)
    BA->>BP: authenticate(cryptoObject, callback)

    BP->>System: show biometric prompt
    System-->>BP: user provides biometric
    BP->>System: verify biometric
    System-->>BP: authentication result

    BP-->>BA: onAuthenticationSucceeded(cryptoObject)
    BA-->>BA: Result<PrivateKey, CryptoObject>
```

## Key Components

### DeviceBindingCallback
**Purpose**: Handles initial device registration
**Lifecycle**:
1. Validation of device support
2. Cleanup of existing keys
3. Key pair generation
4. User authentication
5. JWT signing with device proof
6. Metadata storage

### DeviceSigningVerifierCallback
**Purpose**: Proves device possession for step-up authentication
**Lifecycle**:
1. Custom claims validation
2. User key lookup/selection
3. User authentication
4. Challenge signing
5. JWT creation with verification proof

### DeviceAuthenticator Implementations

| Implementation | Authentication Type | Use Case |
|----------------|-------------------|----------|
| `BiometricOnlyAuthenticator` | BIOMETRIC_ONLY | Strict biometric authentication |
| `BiometricAllowFallbackAuthenticator` | BIOMETRIC_ALLOW_FALLBACK | Biometric with device credential fallback |
| `AppPinAuthenticator` | APPLICATION_PIN | Custom PIN authentication |
| `None` | NONE | No authentication required |

### CryptoKey
**Purpose**: Manages Android KeyStore operations
**Features**:
- RSA 2048-bit key generation
- Hardware-backed security when available
- Automatic key alias generation via SHA-256 hashing
- Certificate chain access for attestation

### UserKeysStorage
**Purpose**: Manages user key metadata persistence
**Features**:
- Encrypted storage using DataStore
- Thread-safe operations
- Automatic cleanup
- Support for multiple users

## Security Considerations

### Key Management
- Private keys never leave the Android KeyStore
- Hardware security module (HSM) support when available
- Automatic key alias generation prevents key ID exposure
- Secure key deletion with proper cleanup

### Authentication Types
- **Biometric**: Leverages Android BiometricPrompt API
- **APP PIN**: Custom PIN with secure storage
- **None**: No authentication (use only for low-security scenarios)

### JWT Security
- Industry-standard JWT tokens for device proof
- Configurable signing algorithms (RS256, RS384, RS512)
- Custom claims support for enhanced verification
- Proper timing claims (iat, nbf, exp)

## Error Handling

### Exception Hierarchy

| Exception | Description | Recovery Strategy |
|-----------|-------------|-------------------|
| `DeviceNotSupportedException` | Device lacks required capabilities | Fallback authentication |
| `DeviceNotRegisteredException` | No keys found for operation | Redirect to registration |
| `InvalidClaimException` | Reserved JWT claim names used | Fix custom claims |
| `BiometricAuthenticationException` | Biometric authentication failed | Retry or fallback |
| `AbortException` | User cancelled operation | Graceful handling |

### Error Recovery
- Automatic cleanup on failure
- Partial operation recovery
- Graceful degradation for unsupported features
- Comprehensive logging for debugging

## Configuration Architecture

### DeviceBindingConfig
Central configuration class using DSL pattern:
- Device identification settings
- Cryptographic algorithm selection
- Storage configuration
- Authentication method setup
- JWT timing and claims
- Custom authenticator factories

### Extensibility
- Plugin-based authenticator architecture
- Custom storage backend support
- Configurable JWT claims
- Custom user key selection strategies
- Override-able timing functions

## Integration Points

### Journey Framework
- Seamless integration with Journey authentication flows
- Automatic callback registration via AndroidX Startup
- Configuration through Journey callback properties

### Android Platform
- Android KeyStore integration
- BiometricPrompt API usage
- DataStore for secure storage
- Activity lifecycle awareness

### UI Components
- Jetpack Compose-based dialogs
- PIN collection UI
- User key selection interface
- Biometric prompt integration