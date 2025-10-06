[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Device Binding

The Device Binding module provides secure device registration and authentication capabilities for Android applications.
It enables applications to bind cryptographic keys to devices and perform authentication using biometric, PIN, or other
authentication methods.

---

## Table of Contents

- [Integrating the SDK into your project](#integrating-the-sdk-into-your-project)
- [Module Overview](#module-overview)
- [Core Components](#core-components)
- [Device Binding Operations](#device-binding-operations)
  - [DeviceBindingCallback.bind()](#devicebindingcallbackbind)
  - [Configuration Options](#configuration-options)
- [Device Signing Verification](#device-signing-verification)
  - [DeviceSigningVerifierCallback.sign()](#devicesigningverifiercallbacksign)
  - [Signing Configuration](#signing-configuration)
  - [Custom Claims](#custom-claims)
- [Configuration Reference](#configuration-reference)
  - [DeviceBindingConfig](#devicebindingconfig)
  - [Storage Configuration](#storage-configuration)
  - [Authenticator Configuration](#authenticator-configuration)
  - [JWT Configuration](#jwt-configuration)
- [Authentication Types](#authentication-types)
  - [Biometric Authentication](#biometric-authentication)
  - [Application PIN](#application-pin)
  - [No Authentication](#no-authentication)
- [Error Handling](#error-handling)
  - [Common Error Types](#common-error-types)
  - [Critical Error Scenarios](#critical-error-scenarios)
  - [Error Handling Pattern](#error-handling-pattern)
- [Examples](#examples)
  - [Basic Device Binding](#basic-device-binding)
  - [Advanced Configuration](#advanced-configuration)
  - [Device Signing](#device-signing)

---

## Integrating the SDK into your project

To add the Device Binding module as a dependency to your project, include the following in your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation(project(":mfa:binding"))
}
```

### Additional Dependencies

If you plan to use **Application PIN** authentication, you must also add the [BouncyCastle](https://mvnrepository.com/artifact/org.bouncycastle/bcprov-jdk18on) dependency:

```kotlin
dependencies {
    implementation(project(":mfa:binding"))
    implementation("org.bouncycastle:bcpkix-jdk18on:1.82") // Required for Application PIN
}
```

### UI Components

For enhanced user experience with default UI components, add the binding-ui module:

```kotlin
dependencies {
    implementation(project(":mfa:binding"))
    implementation(project(":mfa:binding-ui")) // Provides default UI components
    implementation("org.bouncycastle:bcpkix-jdk18on:1.82") // Required for Application PIN
}
```

When **binding-ui** is included, the SDK automatically provides:

- **Application PIN Collection**: A Jetpack Compose dialog for secure PIN entry with:
  - Custom PIN input field with masked characters
  - Show/hide PIN visibility toggle
  - Cancel and confirm buttons
  - Error handling and validation feedback

- **User Key Selection**: A default UI for multi-user scenarios that displays:
  - List of available user keys with user information
  - Selection interface when multiple users have registered devices
  - User-friendly key identification (username, creation date, etc.)

These UI components are automatically used when no custom implementation is provided, offering a consistent user experience across applications.

### Legacy SDK Migration

For applications migrating from the Legacy ForgeRock SDK, add the binding-migration module for automatic key migration:

```kotlin
dependencies {
    implementation(project(":mfa:binding"))
    implementation(project(":mfa:binding-migration")) // Automatic Legacy SDK migration
    implementation(project(":mfa:binding-ui")) // Optional: Default UI components
    implementation("org.bouncycastle:bcpkix-jdk18on:1.82") // Required for Application PIN
}
```

When **binding-migration** is included, the SDK automatically:

- **Detects Legacy Keys**: Scans for existing ForgeRock SDK device binding keys and metadata
- **Seamless Migration**: Automatically migrates keys to the new SDK format during application startup
- **Preserves User Experience**: Users don't need to re-register their devices after SDK upgrade
- **One-Time Process**: Migration occurs once and removes legacy data after successful migration
- **Backward Compatibility**: Ensures smooth transition from Legacy SDK without data loss

The migration process runs transparently in the background and requires no additional configuration or user intervention.

---

## Module Overview

The Device Binding module provides two main operations:

### Device Binding

Registers a new device by creating cryptographic keys and proving device possession through signed JWTs.

### Device Signing Verification

Proves device possession using existing keys by signing server challenges for step-up authentication.

### Key Features

- **Multiple Authentication Types**: Biometric, Biometric with Device Credential, Application PIN, or None
  authentication
- **Hardware Security**: Leverages Android KeyStore for secure key storage
- **JWT-based Proof**: Industry-standard JWT tokens for device verification
- **Flexible Configuration**: Extensive customization options
- **Journey Integration**: Seamless integration with authentication flows

---

## Core Components

| Component                       | Purpose                     | Use Case                  |
|---------------------------------|-----------------------------|---------------------------|
| `DeviceBindingCallback`         | Initial device registration | First-time device binding |
| `DeviceSigningVerifierCallback` | Device verification         | Step-up authentication    |
| `DeviceBindingConfig`           | Configuration management    | Customizing behavior      |
| `DeviceAuthenticator`           | Authentication handling     | User verification         |

---

## Device Binding Operations

### DeviceBindingCallback.bind()

The `bind()` method performs complete device registration with the following lifecycle:

1. **Validation**: Checks device support for authentication type
2. **Cleanup**: Removes existing keys for the user
3. **Key Generation**: Creates new cryptographic key pair
4. **Authentication**: Verifies user identity
5. **JWT Signing**: Creates signed proof of possession
6. **Storage**: Saves user key metadata

```kotlin
suspend fun bind(config: DeviceBindingConfig.() -> Unit = {}): Result<String>
```

#### Basic Usage

```kotlin
import com.pingidentity.device.binding.journey.DeviceBindingCallback

// Simple device binding with default configuration
val result = deviceBindingCallback.bind()

result.onSuccess { jwt ->
    // Device successfully bound, JWT contains proof
    println("Device bound successfully: $jwt")
}.onFailure { error ->
    // Handle binding failure
    println("Binding failed: ${error.message}")
}
```

### Configuration Options

The `bind()` method accepts a configuration block for extensive customization:

```kotlin
val result = deviceBindingCallback.bind {
    // Device identification
    deviceName = "John's Phone"
    deviceIdentifier = DefaultDeviceIdentifier

    // Cryptographic settings
    signingAlgorithm = "RS256"

    // Timing configuration
    issueTime = { Instant.now() }
    expirationTime = { timeout -> Instant.now().plusSeconds(timeout.toLong()) }

    // Storage configuration
    userKeyStorage {
        storage {
            fileName = "user_keys"
        }
    }

    // Authentication configuration
    biometricAuthenticatorConfig {
        promptInfo = {
            setTitle("Device Registration")
            setSubtitle("Secure your account")
            setDescription("Use your fingerprint to register this device")
        }
    }
}
```

---

## Device Signing Verification

### DeviceSigningVerifierCallback.sign()

The `sign()` method proves device possession using existing keys:

1. **Validation**: Validates custom claims
2. **Key Lookup**: Finds appropriate user key
3. **Authentication**: Verifies user identity
4. **Challenge Signing**: Signs server challenge
5. **JWT Creation**: Creates verification JWT

```kotlin
suspend fun sign(config: DeviceBindingConfig.() -> Unit = {}): Result<String>
```

#### Basic Usage

```kotlin
import com.pingidentity.device.binding.journey.DeviceSigningVerifierCallback

// Simple device signing
val result = deviceSigningVerifierCallback.sign()

result.onSuccess { jwt ->
    // Challenge successfully signed
    println("Challenge signed: $jwt")
}.onFailure { error ->
    // Handle signing failure
    println("Signing failed: ${error.message}")
}
```

### Signing Configuration

```kotlin
val result = deviceSigningVerifierCallback.sign {
    // Signing algorithm
    signingAlgorithm = "RS512"

    appPinConfig {
        pinRetry = 3
        pinCollector {
            "1234".toCharArray()
        }
        prompt = Prompt("App Pin", "Enter your app pin", "App pin is required")
    }

    // User key selection strategy
    userKeySelector { keys ->
        // Select most recently created key
        keys.maxByOrNull { it.createdAt } ?: keys.first()
    }

    // Custom verification claims
    claims {
        put("transaction_id", "txn_12345")
        put("timestamp", System.currentTimeMillis())
        put("location", getCurrentLocation())
    }

    // Authentication configuration
    biometricAuthenticatorConfig {
        promptInfo = {
            setTitle("Verify Transaction")
            setDescription("Confirm this transaction with your fingerprint")
        }
    }
}
```

### Custom Claims

Add verification context to signed JWTs:

```kotlin
deviceSigningVerifierCallback.sign {
    claims {
        // Transaction details
        put("amount", "100.00")
        put("recipient", "john@example.com")
        put("currency", "USD")

        // Device context
        put("ip_address", getClientIP())
        put("user_agent", getUserAgent())

        // Security context
        put("risk_score", calculateRiskScore())
        put("session_id", getSessionId())
    }
}
```

---

## Configuration Reference

### DeviceBindingConfig

The main configuration class with comprehensive options:

```kotlin
class DeviceBindingConfig {
    // Device identification
    var deviceIdentifier: DeviceIdentifier = DefaultDeviceIdentifier
    var deviceName: String = Build.MODEL

    // Cryptographic settings
    var signingAlgorithm: String = "RS512"

    // Timing functions
    var issueTime: () -> Instant = { Instant.now() }
    var notBeforeTime: () -> Instant = { Instant.now() }
    var expirationTime: (Int) -> Instant = { Instant.now().plusSeconds(it.toLong()) }

    // Logging
    var logger: Logger = Logger.logger
}
```

### Storage Configuration

Configure user key metadata storage:

```kotlin
userKeyStorage {
    // File configuration
    fileName = "my_device_keys"

    // Encryption settings
    keyAlias = "device_key_encryption"
    strongBoxPreferred = true

    // Cache strategy
    cacheStrategy = CacheStrategy.NO_CACHE
}
```

### Authenticator Configuration

#### Biometric Configuration

```kotlin
biometricAuthenticatorConfig {
    promptInfo = {
        setTitle("Authenticate")
        setSubtitle("Device Security")
        setDescription("Use your biometric to authenticate")
        setNegativeButtonText("Cancel")
    }

    keyGenParameterSpec = {
        setUserAuthenticationRequired(true)
        setUserAuthenticationValidityDurationSeconds(5 * 60) // 5 minutes
        setInvalidatedByBiometricEnrollment(true)
    }
}
```

#### Application PIN Configuration

```kotlin
appPinConfig {
    // Custom PIN collector
    pinCollector = { prompt ->
        collectPin(
            activity = currentActivity,
            title = prompt.title,
            subtitle = prompt.subtitle,
            description = prompt.description
        )
    }

    // Storage configuration
    storage {
        fileName = "app_pin_storage"
    }
}
```

### JWT Configuration

```kotlin
// Signing algorithm
signingAlgorithm = "RS256" // or "RS384", "RS512"

// Timing configuration
issueTime = { Instant.now() }
notBeforeTime = { Instant.now().minusSeconds(5) }
expirationTime = { timeout ->
    Instant.now().plusSeconds(timeout.toLong())
}

// Custom claims
claims {
    put("iss", "my-app")
    put("aud", "my-service")
    put("custom_data", mapOf("key" to "value"))
}
```

---

## Authentication Types

The Device Binding module supports four distinct authentication types, each offering different levels of security and
user experience. The authentication type determines how users must verify their identity to access cryptographic keys
stored on the device.

### Biometric Authentication

#### Biometric Only

- **Type**: `BIOMETRIC_ONLY`
- **Description**: Requires strict biometric authentication with no fallback options
- **Security Level**: High
- **User Experience**: Streamlined for devices with reliable biometric sensors
- **Behavior**:
  - Only accepts biometric authentication (fingerprint, face recognition, iris scan)
  - Fails immediately if biometric authentication is unavailable or unsuccessful
  - No option to fall back to device PIN, pattern, or password
  - Ideal for high-security applications where biometric verification is mandatory
- **Use Cases**: Financial applications, enterprise security, medical applications
- **Device Requirements**: Must have functional biometric sensors and enrolled biometric data

#### Biometric with Fallback

- **Type**: `BIOMETRIC_ALLOW_FALLBACK`
- **Description**: Prefers biometric authentication but allows fallback to device credentials
- **Security Level**: Medium to High
- **User Experience**: Flexible with multiple authentication options
- **Behavior**:
  - Primary authentication method is biometric (fingerprint, face, etc.)
  - If biometric fails or is unavailable, users can use device credentials
  - Device credentials include PIN, pattern, password set at the system level
  - Provides better accessibility and usability
- **Use Cases**: Consumer applications, general-purpose authentication, accessibility-focused apps
- **Device Requirements**: Biometric sensors preferred but not required; device lock screen must be configured

### Application PIN

- **Type**: `APPLICATION_PIN`
- **Description**: Requires a custom PIN managed entirely by the application
- **Security Level**: Medium
- **User Experience**: Consistent across all devices regardless of hardware capabilities
- **Behavior**:
  - Uses an application-specific PIN separate from device credentials
  - PIN is collected through custom UI provided by the application
  - PIN data is securely stored using encrypted storage mechanisms
  - Independent of device biometric capabilities or system-level authentication
- **Use Cases**:
  - Devices without biometric capabilities
  - Applications requiring custom authentication flows
  - Scenarios where users prefer PIN over biometric authentication
  - Cross-platform consistency requirements
- **Device Requirements**: None - works on all Android devices

### No Authentication

- **Type**: `NONE`
- **Description**: No user authentication required to access cryptographic keys
- **Security Level**: Low
- **User Experience**: Seamless with no authentication prompts
- **Behavior**:
  - Keys are immediately accessible without any user verification
  - No authentication prompts or delays
  - Cryptographic operations proceed without user interaction
  - Relies solely on device possession for security
- **Use Cases**:
  - Applications with alternative security measures
- **Security Considerations**:
  - Anyone with device access can use the cryptographic keys
- **Device Requirements**: None

---

## Error Handling

### Common Error Types

| Error | Description | Handling |
|-------|-------------|----------|
| `DeviceNotSupportedException` | Device lacks required capabilities | Show alternative auth method |
| `DeviceNotRegisteredException` | No keys found for signing | Redirect to registration |
| `TimeoutCancellationException` | Operation exceeded timeout | Allow retry with longer timeout |
| `InvalidClaimException` | Reserved claim names used | Fix custom claims |
| `AbortException` | Operation aborted by user action | Handle gracefully, don't show error |
| `BiometricAuthenticationException` | Biometric authentication failed | Retry or show fallback auth |
| `InvalidCredentialException` | Invalid credentials provided | Prompt for correct credentials |
| `CancellationException` | Coroutine operation cancelled | Re-thrown to preserve cancellation semantics |

### Critical Error Scenarios

#### Biometric Enrollment Invalidation

When `setInvalidatedByBiometricEnrollment` is set to `true` in the key generation parameters, enrolling a new fingerprint will cause the existing private key to become permanently invalidated.

**Scenario**: User has bound device with `BiometricOnly` authentication and later enrolls a new fingerprint.

**Error**: `KeyPermanentlyInvalidatedException` will be thrown during signing operations.

**Resolution**: User must perform device binding again to generate new keys.

```kotlin
// Configuration that makes keys sensitive to biometric changes
biometricAuthenticatorConfig {
    // This setting makes keys invalid when new biometrics are enrolled
    setInvalidatedByBiometricEnrollment = true
    authenticationType = DeviceBindingAuthenticationType.BIOMETRIC_ONLY
}

// When user enrolls new fingerprint, subsequent signing will fail
deviceSigningCallback.sign().onFailure { error ->
    when (error) {
        is KeyPermanentlyInvalidatedException -> {
            // Keys are permanently invalidated due to biometric enrollment
            logger.w("Device keys invalidated by biometric enrollment")
            redirectToDeviceBinding() // User must re-bind device
        }
    }
}
```

#### Complete Authentication Removal

When all biometric authentication methods and device PIN are removed from the device, the Android KeyStore automatically removes associated private keys.

**Scenario**: User removes all fingerprints, face unlock, and device PIN/pattern/password from device settings.

**Error**: `DeviceNotRegisteredException` will be thrown when attempting to sign.

**Resolution**: User must perform device binding again to create new keys with available authentication methods.

```kotlin
// When all device authentication is removed, keys are deleted by Android KeyStore
deviceSigningCallback.sign().onFailure { error ->
    when (error) {
        is DeviceNotRegisteredException -> {
            logger.w("No device keys found - likely removed due to authentication changes")
            showMessage("Please set up device security and re-register your device")
            redirectToDeviceBinding() // User must re-bind device
        }
    }
}
```

### Error Handling Pattern

```kotlin
deviceBindingCallback.bind().fold(
    onSuccess = { jwt ->
        // Handle success
        processBindingSuccess(jwt)
    },
    onFailure = { error ->
        when (error) {
            is DeviceNotSupportedException -> {
                logger.w("Device not supported: ${error.message}")
                showFallbackAuthentication()
            }
            is TimeoutCancellationException -> {
                logger.w("Binding operation timed out")
                retryWithLongerTimeout()
            }
            else -> {
                logger.e("Binding failed", error)
                showGenericError(error.message)
            }
        }
    }
)
```

---

## Examples

### Basic Device Binding

```kotlin
class DeviceBindingActivity : AppCompatActivity() {

    private lateinit var deviceBindingCallback: DeviceBindingCallback

    private suspend fun performDeviceBinding() {
        val result = deviceBindingCallback.bind()

        result.onSuccess { jwt ->
            // Send JWT to server for verification
            showSuccess("Device registered successfully")
            // Proceed with app flow with ContinueNode.next()
        }.onFailure { error ->
            showError("Registration failed: ${error.message}")
            // The clientError is populated, proceed with the app flow with ContinueNode.next()
        }
    }
}
```

### Advanced Configuration

```kotlin
private suspend fun advancedDeviceBinding() {
    val result = deviceBindingCallback.bind {
        // Custom device naming
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        // Enhanced security
        signingAlgorithm = "RS256"

        // Storage configuration
        userKeyStorage {
            storage {
                fileName = "secure_device_keys"
                strongBoxPreferred = true
                cacheStrategy = CacheStrategy.NO_CACHE
            }
        }

        // Biometric configuration
        biometricAuthenticatorConfig {
            promptInfo = {
                setTitle("Register Device")
                setSubtitle("Security Setup")
                setDescription("Your biometric will secure this device")
                setConfirmationRequired(false)
            }
        }

        // Custom claims for enhanced verification
        claims {
            put("app_version", BuildConfig.VERSION_NAME)
            put("device_model", Build.MODEL)
            put("registration_time", System.currentTimeMillis())
            put("client_ip", getClientIPAddress())
        }

        // Custom timing
        expirationTime = { timeout ->
            Instant.now().plusSeconds((timeout * 1.5).toLong())
        }
    }

    // Handle result
    processBindingResult(result)
}
```

### Device Signing

```kotlin
private suspend fun performDeviceSigning() {
  val result = deviceSigningVerifierCallback.sign {
    // Transaction-specific claims
    claims {
      put("transaction_id", getCurrentTransactionId())
      put("amount", getTransactionAmount())
      put("timestamp", System.currentTimeMillis())
    }

    // User key selection for multiple accounts
    userKeySelector { keys ->
      // Show selection UI or apply business logic
      selectKeyForCurrentContext(keys)
    }

    // Authentication prompt
    biometricAuthenticatorConfig {
      promptInfo = {
        setTitle("Verify Transaction")
        setDescription("Confirm the transaction with your fingerprint")
      }
    }
  }

  result.onSuccess { signedJWT ->
    // Proceed with app flow with ContinueNode.next()
  }.onFailure { error ->
    handleSigningError(error)
    // The clientError is populated, proceed with the app flow with ContinueNode.next()
  }
}
```

---
