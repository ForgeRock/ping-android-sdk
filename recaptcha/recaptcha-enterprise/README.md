[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# ReCaptcha Enterprise Module

> **Seamless integration of Google ReCaptcha Enterprise into Ping Identity's Journey workflows for Android.**

The ReCaptcha Enterprise module provides a flexible and developer-friendly way to add Google ReCaptcha Enterprise verification to your authentication flows using the Journey plugin architecture.

---

## ✨ Features

- **🔌 Plugin Architecture**: Integrates seamlessly with the Journey framework for authentication orchestration
- **🛡️ Enterprise Security**: Adds advanced bot and abuse protection using Google ReCaptcha Enterprise
- **⚡ Asynchronous API**: Coroutine-based suspend functions for smooth, non-blocking UI operations
- **📝 Type-Safe DSL**: Fluent, compile-time safe configuration using Kotlin DSL
- **📦 Modern Serialization**: Uses Kotlin serialization for efficient JSON handling
- **🎯 Action-Based**: Support for different ReCaptcha actions (LOGIN, SIGNUP, custom actions)
- **⏱️ Configurable Timeouts**: Customizable timeout settings for different network conditions
- **📊 Custom Payload Support**: Send additional metadata with verification requests for risk assessment
- **📝 Integrated Logging**: Configurable logger with multiple log levels (DEBUG, INFO, WARN, ERROR)
- **✅ Comprehensive Tests**: 17 unit tests covering all major scenarios with 100% pass rate

---

## 📋 Table of Contents

- [Overview](#overview)
- [Getting Started](#getting-started)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Usage Examples](#usage-examples)
- [API Reference](#api-reference)
- [Testing](#testing)
- [Architecture](#architecture)
- [Troubleshooting](#troubleshooting)
- [License](#license)

---

## Overview

This module enables you to add Google ReCaptcha Enterprise verification to your authentication journeys. It is designed to be used as a callback within the Journey plugin system, providing a secure and customizable way to verify user actions and protect against bots and abuse.

The module automatically handles:
- ReCaptcha client initialization and management
- Token generation and validation
- Error handling and recovery
- Integration with Journey authentication flows

---

## Getting Started

### Prerequisites

- Android API level 21 or higher
- Google ReCaptcha Enterprise site key configured in Google Cloud Console
- Ping Identity Journey SDK integrated in your project

### Installation

Add the module to your project dependencies (see the main SDK documentation for details).

---

## Quick Start
**Add the module dependency** to your project:

```kotlin
dependencies {
    implementation("com.pingidentity.recaptcha.enterprise:x.y.z")
}
```

```kotlin
// In your Journey flow
val callback = journey.next() as ReCaptchaEnterpriseCallback

// Simple verification with defaults
val result = callback.verify()

result.onSuccess { token ->
    // Proceed with authentication
    println("ReCaptcha verification successful: $token")
}.onFailure { error ->
    // Handle verification failure
    println("ReCaptcha verification failed: ${error.message}")
}
```

---

## Configuration

> **Note:** All configuration options are set through the `ReCaptchaEnterpriseConfig` DSL block passed to the `verify()` method. The callback itself only receives the `reCaptchaSiteKey` from the server - all other settings are configured at verification time.

### Basic Configuration

Configure the callback using the provided DSL:

```kotlin
callback.verify {
    // Set the action type
    recaptchaAction = RecaptchaAction.LOGIN
    
    // Set timeout in milliseconds
    timeoutInMills = 10000L
}
```

### Advanced Configuration

All configuration options are set via the `ReCaptchaEnterpriseConfig` DSL block:

```kotlin
callback.verify {
    // Different action types
    recaptchaAction = RecaptchaAction.SIGNUP
    // or custom action
    recaptchaAction = RecaptchaAction.custom("PASSWORD_RESET")
    
    // Longer timeout for slower networks
    timeoutInMills = 15000L
    
    // Add custom payload for risk assessment
    customPayload = buildJsonObject {
        put("userId", "user123")
        put("deviceId", "device456")
        put("metadata", "additional-info")
    }
    
    // Enable debug logging
    logger = Logger.DEBUG
}
```

### Configuration with Custom Payload

Send additional metadata with your verification request:

```kotlin
callback.verify {
    recaptchaAction = RecaptchaAction.LOGIN
    
    // Include user context for better risk assessment
    customPayload = buildJsonObject {
        put("userId", currentUser.id)
        put("accountAge", currentUser.accountAgeDays)
        put("previousFailures", loginAttempts)
        put("deviceFingerprint", deviceInfo.fingerprint)
    }
}
```

### Logging Configuration

Control logging levels for different environments:

```kotlin
// Development: detailed debugging
callback.verify {
    logger = Logger.DEBUG
}

// Production: warnings and errors only
callback.verify {
    logger = Logger.WARN
}

// Testing: info level
callback.verify {
    logger = Logger.INFO
}
```

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `reCaptchaSiteKey` | `String` | From server | ReCaptcha Enterprise site key (set by server, read-only) |
| `recaptchaAction` | `RecaptchaAction` | `RecaptchaAction.LOGIN` | Type of action being verified (configurable via config) |
| `timeoutInMills` | `Long` | `10000L` | Timeout in milliseconds (configurable via config) |
| `customPayload` | `JsonObject?` | `null` | Optional metadata to send with verification (configurable via config) |
| `logger` | `Logger` | `Logger.WARN` | Logger instance for verification process (configurable via config) |

---

## Usage Examples

### Basic Usage in Compose UI

```kotlin
@Composable
fun ReCaptchaScreen(callback: ReCaptchaEnterpriseCallback, onNext: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(key1 = true) {
        scope.launch {
            callback.verify().onSuccess { result ->
                println("Verification successful: $result")
                isLoading = false
                onNext()
            }.onFailure { error ->
                println("Verification failed: ${error.message}")
                isLoading = false
                // Handle error appropriately
            }
        }
    }
    
    if (isLoading) {
        CircularProgressIndicator()
    }
}
```

### Custom Action Usage

```kotlin
// For different user actions
callback.verify {
    recaptchaAction = when (userAction) {
        "login" -> RecaptchaAction.LOGIN
        "signup" -> RecaptchaAction.SIGNUP
        "reset_password" -> RecaptchaAction.custom("PASSWORD_RESET")
        else -> RecaptchaAction.LOGIN
    }
    timeoutInMills = 12000L
}
```

### Real-World Example with Custom Payload

```kotlin
// E-commerce checkout verification
callback.verify {
    recaptchaAction = RecaptchaAction.custom("CHECKOUT")
    timeoutInMills = 20000L
    
    customPayload = buildJsonObject {
        put("cartValue", cartTotal.toString())
        put("itemCount", cart.items.size)
        put("isFirstPurchase", user.orderHistory.isEmpty())
        put("paymentMethod", selectedPaymentMethod)
    }
    
    logger = if (BuildConfig.DEBUG) Logger.DEBUG else Logger.WARN
}

// High-risk operation verification
callback.verify {
    recaptchaAction = RecaptchaAction.custom("ACCOUNT_DELETE")
    
    customPayload = buildJsonObject {
        put("accountAge", user.accountAgeDays)
        put("hasActiveSubscription", user.hasActiveSubscription)
        put("requestedBy", "user")
    }
    
    logger = Logger.INFO
}
```

### Error Handling

```kotlin
callback.verify().fold(
    onSuccess = { token ->
        // Success: proceed with authentication
        handleSuccess(token)
    },
    onFailure = { error ->
        when (error) {
            is IllegalStateException -> {
                // Configuration or token validation error
                showError("Verification configuration error")
            }
            is RuntimeException -> {
                // Network or ReCaptcha service error
                showError("Network error, please try again")
            }
            else -> {
                // Unknown error
                showError("Verification failed, please try again")
            }
        }
    }
)
```

---

## API Reference

### ReCaptchaEnterpriseCallback

The main callback class for handling ReCaptcha Enterprise verification.

#### Properties

- `reCaptchaSiteKey: String` - The site key (read-only, set by server)
- `logger: Logger` - Logger instance for the callback (read-only, default: `Logger.WARN`)

#### Methods

##### `suspend fun verify(block: ReCaptchaEnterpriseConfig.() -> Unit = {}): Result<String>`

Performs ReCaptcha Enterprise verification with optional configuration.

**Parameters:**
- `block` - Configuration DSL block (optional)

**Returns:**
- `Result<String>` - Success with verification token or failure with exception

**Throws:**
- `Exception` - For network or ReCaptcha service errors (UNKNOWN_ERROR)

**Example:**
```kotlin
val result = callback.verify {
    recaptchaAction = RecaptchaAction.SIGNUP
    timeoutInMills = 15000L
    customPayload = buildJsonObject {
        put("userId", "123")
    }
    logger = Logger.DEBUG
}
```

### ReCaptchaEnterpriseConfig

DSL configuration class for ReCaptcha settings.

#### Properties

All properties are configured via the `ReCaptchaEnterpriseConfig` DSL block passed to `verify()`:

- `recaptchaAction: RecaptchaAction` - Action type (LOGIN, SIGNUP, or custom via `RecaptchaAction.custom()`). Default: `RecaptchaAction.LOGIN`
- `timeoutInMills: Long` - Timeout in milliseconds. Default: `10000L`
- `customPayload: JsonObject?` - Optional metadata to send with verification. Default: `null`
- `logger: Logger` - Logger instance for verification process. Default: `Logger.WARN`

---

## Testing

### Test Coverage

The module includes **17 comprehensive unit tests** with a **100% pass rate**, covering:

✅ **Initialization Tests**
- Site key initialization with correct property names
- Ignoring unrelated property names
- Config default values validation

✅ **Success Scenarios**
- Basic verification with default configuration
- Custom actions (LOGIN, SIGNUP) with custom timeouts
- Custom timeout configuration
- Custom payload support
- All configuration options combined
- Config inheritance from callback properties

✅ **Failure Scenarios**
- Empty token handling
- Execute failure handling
- FetchClient exception handling

### Unit Testing Best Practices

For testing your application's use of the callback, mock the callback to avoid native library dependencies:

```kotlin
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

class MyAuthViewModelTest {
    
    @Test
    fun `test successful recaptcha verification`() = runTest {
        // Mock the callback
        val mockCallback = mockk<ReCaptchaEnterpriseCallback>()
        coEvery { mockCallback.verify(any()) } returns Result.success("mock_token_12345")
        
        // Test your application logic
        val viewModel = AuthViewModel(mockCallback)
        viewModel.performLogin()
        
        // Assert your expectations
        assertTrue(viewModel.loginSuccess)
    }
    
    @Test
    fun `test failed verification with custom payload`() = runTest {
        val mockCallback = mockk<ReCaptchaEnterpriseCallback>()
        coEvery { mockCallback.verify(any()) } returns 
            Result.failure(Exception("UNKNOWN_ERROR"))
        
        val viewModel = AuthViewModel(mockCallback)
        viewModel.performLogin()
        
        // Verify error handling
        assertTrue(viewModel.showError)
        assertEquals("Verification failed", viewModel.errorMessage)
    }
    
    @Test
    fun `test verification with custom payload`() = runTest {
        val mockCallback = mockk<ReCaptchaEnterpriseCallback>(relaxed = true)
        coEvery { mockCallback.verify(any()) } returns Result.success("payload_token")
        
        val result = mockCallback.verify {
            customPayload = buildJsonObject {
                put("userId", "12345")
                put("metadata", "test-data")
            }
        }
        
        assertTrue(result.isSuccess)
        assertEquals("payload_token", result.getOrNull())
    }
}
```

### Important Testing Notes

⚠️ **Native Dependencies**: The Google ReCaptcha Enterprise SDK has native dependencies that cannot be properly mocked in standard unit tests. Always mock the callback itself rather than trying to mock the internal ReCaptcha SDK.

✅ **Recommended Approach**: Mock the `ReCaptchaEnterpriseCallback` at the boundary of your application code to test your logic without invoking the actual ReCaptcha SDK.

🧪 **Integration Testing**: For end-to-end testing with real ReCaptcha verification, use Android instrumented tests with a valid test site key configured in Google Cloud Console.

---

## Architecture

The module follows several design patterns:

- **Plugin/Callback Pattern**: Integrates with the Journey plugin system
- **Builder Pattern**: DSL configuration for type-safe setup
- **Result Pattern**: Proper error handling with Kotlin's Result type

### Class Relationships

```
ReCaptchaEnterpriseCallback
    └── ReCaptchaEnterpriseConfig (DSL)
```

For detailed architecture diagrams, see the [CONCEPT.md](CONCEPT.md) file.

---

## Troubleshooting

### Common Issues

#### 1. "UNKNOWN_ERROR" Error

**Cause:** ReCaptcha client fetch failed or unexpected error during verification.
**Solutions:**
- Check network connectivity
- Verify site key configuration in Google Cloud Console
- Ensure Google Play Services is installed and up-to-date
- Check Google Cloud Console ReCaptcha settings
- Enable debug logging to see detailed error messages

**Example:**
```kotlin
callback.verify {
    logger = Logger.DEBUG // Enable detailed logging
    timeoutInMills = 20000L // Increase timeout
}
```

#### 2. Empty Token / Verification Failure

**Cause:** ReCaptcha verification returned empty or invalid token.
**Solutions:**
- Check network connectivity
- Increase timeout value for slower networks
- Verify the action type is appropriate
- Check if ReCaptcha Enterprise is properly configured

**Example:**
```kotlin
callback.verify {
    timeoutInMills = 20000L // Increase timeout
    recaptchaAction = RecaptchaAction.LOGIN // Use appropriate action
    logger = Logger.DEBUG // Enable logging
}
```

#### 3. Network/Timeout Issues

**Cause:** Slow network or restrictive timeout.
**Solutions:**
```kotlin
callback.verify {
    timeoutInMills = 30000L // 30 seconds for very slow networks
    logger = Logger.WARN
}
```

#### 4. Site Key Issues

**Cause:** Invalid or misconfigured site key.
**Solutions:**
- Verify site key in Google Cloud Console
- Ensure key is enabled for your domain/app
- Check key type (must be Enterprise, not Standard)
- Verify the key is registered for Android platform

#### 5. Custom Payload Issues

**Cause:** Invalid JSON payload or serialization errors.
**Solutions:**
```kotlin
// Ensure valid JSON structure
callback.verify {
    customPayload = buildJsonObject {
        // Use proper types
        put("stringValue", "text")
        put("intValue", 123)
        put("boolValue", true)
        put("doubleValue", 45.67)
    }
}
```

### Debug Tips

1. **Enable Debug Logging**
   ```kotlin
   callback.verify {
       logger = Logger.DEBUG
   }
   ```

2. **Test with Increased Timeout**
   ```kotlin
   callback.verify {
       timeoutInMills = 30000L
   }
   ```

3. **Verify Network Connectivity**
   - Ensure device has internet access
   - Check firewall/proxy settings
   - Test on different networks

4. **Validate Google Cloud Configuration**
   - Site key is correct and active
   - ReCaptcha Enterprise is enabled
   - Android app is registered
   - API keys are properly configured

5. **Check Custom Payload Size**
   - Keep payloads reasonably small
   - Avoid sensitive data in payloads
   - Test without payload first to isolate issues

### Error Codes

| Error Code | Description | Common Causes |
|------------|-------------|---------------|
| `UNKNOWN_ERROR` | All verification failures | Network issues, invalid configuration, ReCaptcha service errors, client setup failures, empty tokens, verification timeouts |

### Getting Help

If you continue to experience issues:
1. Enable DEBUG logging and capture logs
2. Verify your Google Cloud Console ReCaptcha configuration
3. Test with a minimal configuration first
4. Check the [CONCEPT.md](CONCEPT.md) for architecture details
5. Review the test cases in `ReCaptchaEnterpriseCallbackTest` for examples

---

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.
