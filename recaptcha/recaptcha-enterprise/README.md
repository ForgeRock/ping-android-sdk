[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# ReCaptcha Enterprise Module

> **Seamless integration of Google ReCaptcha Enterprise into Ping Identity's Journey workflows for Android.**

The ReCaptcha Enterprise module provides a flexible, extensible, and developer-friendly way to add Google ReCaptcha Enterprise verification to your authentication flows using the Journey plugin architecture.

---

## ✨ Features

- **🔌 Plugin Architecture**: Integrates seamlessly with the Journey framework for authentication orchestration
- **🛡️ Enterprise Security**: Adds advanced bot and abuse protection using Google ReCaptcha Enterprise
- **⚡ Asynchronous API**: Coroutine-based suspend functions for smooth, non-blocking UI operations
- **🧩 Extensible Provider**: Strategy pattern allows custom ReCaptcha client providers for testing and customization
- **📝 Type-Safe DSL**: Fluent, compile-time safe configuration using Kotlin DSL
- **📦 Modern Serialization**: Uses Kotlin serialization for efficient JSON handling
- **🎯 Action-Based**: Support for different ReCaptcha actions (LOGIN, SIGNUP, custom actions)
- **⏱️ Configurable Timeouts**: Customizable timeout settings for different network conditions

---

## 📋 Table of Contents

- [Overview](#overview)
- [Getting Started](#getting-started)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Usage Examples](#usage-examples)
- [API Reference](#api-reference)
- [Extensibility](#extensibility)
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

result.onSuccess { json ->
    // Proceed with authentication
    println("ReCaptcha verification successful: $json")
}.onFailure { error ->
    // Handle verification failure
    println("ReCaptcha verification failed: ${error.message}")
}
```

---

## Configuration

### Basic Configuration

Configure the callback using the provided DSL:

```kotlin
callback.verify {
    // Set the action type
    recaptchaAction = RecaptchaAction.LOGIN
    
    // Set timeout in milliseconds
    timeoutInMills = 10000L
    
    // Use default provider
    setProvider(DefaultRecaptchaClientProvider())
}
```

### Advanced Configuration

```kotlin
callback.verify {
    // Different action types
    recaptchaAction = RecaptchaAction.SIGNUP
    // or custom action
    recaptchaAction = RecaptchaAction.custom("PASSWORD_RESET")
    
    // Longer timeout for slower networks
    timeoutInMills = 15000L
    
    // Custom provider implementation
    setProvider(MyCustomRecaptchaProvider())
}
```

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `reCaptchaSiteKey` | `String` | From server | ReCaptcha Enterprise site key |
| `recaptchaAction` | `RecaptchaAction` | `RecaptchaAction.LOGIN` | Type of action being verified |
| `timeoutInMills` | `Long` | `10000L` | Timeout in milliseconds |
| `recaptchaClientProvider` | `RecaptchaClientProvider?` | `null` | Custom provider implementation |

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
            callback.verify {
                setProvider(DefaultRecaptchaClientProvider())
            }.onSuccess { result ->
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
    setProvider(DefaultRecaptchaClientProvider())
}
```

### Error Handling

```kotlin
callback.verify {
    setProvider(DefaultRecaptchaClientProvider())
}.fold(
    onSuccess = { json ->
        // Success: proceed with authentication
        handleSuccess(json)
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
- `journey: Journey` - The associated Journey instance

#### Methods

##### `suspend fun verify(block: ReCaptchaEnterpriseConfig.() -> Unit = {}): Result<JsonObject>`

Performs ReCaptcha Enterprise verification with optional configuration.

**Parameters:**
- `block` - Configuration DSL block (optional)

**Returns:**
- `Result<JsonObject>` - Success with verification token or failure with exception

**Throws:**
- `IllegalStateException` - If RecaptchaClientProvider is not configured or token is invalid
- `Exception` - For network or ReCaptcha service errors

### ReCaptchaEnterpriseConfig

DSL configuration class for ReCaptcha settings.

#### Properties

- `reCaptchaSiteKey: String` - ReCaptcha site key
- `recaptchaAction: RecaptchaAction` - Action type (LOGIN, SIGNUP, custom)
- `timeoutInMills: Long` - Timeout in milliseconds
- `recaptchaClientProvider: RecaptchaClientProvider?` - Provider implementation

#### Methods

##### `fun setProvider(provider: RecaptchaClientProvider)`

Sets a custom RecaptchaClientProvider implementation.

### RecaptchaClientProvider Interface

Interface for custom ReCaptcha client provider implementations.

#### Methods

##### `suspend fun fetchClient(application: Application, siteKey: String): RecaptchaClient`

Fetches a RecaptchaClient for the specified site key.

##### `suspend fun execute(client: RecaptchaClient, action: RecaptchaAction, timeoutInMillis: Long): String?`

Executes a ReCaptcha action and returns the verification token.

### DefaultRecaptchaClientProvider

Default implementation using Google's ReCaptcha Enterprise SDK.

---

## Extensibility

### Custom Provider Implementation

You can provide your own implementation of `RecaptchaClientProvider` for testing, custom logic, or enhanced functionality:

```kotlin
class CustomRecaptchaProvider : RecaptchaClientProvider {
    override suspend fun fetchClient(application: Application, siteKey: String): RecaptchaClient {
        // Custom client creation logic
        return Recaptcha.fetchClient(application, siteKey)
    }
    
    override suspend fun execute(client: RecaptchaClient, action: RecaptchaAction, timeoutInMillis: Long): String? {
        // Custom execution logic (e.g., retry, logging, analytics)
        return client.execute(action, timeoutInMillis).getOrNull()
    }
}

// Usage
callback.verify {
    setProvider(CustomRecaptchaProvider())
}
```

### Mock Provider for Testing

```kotlin
class MockRecaptchaProvider(private val shouldSucceed: Boolean = true) : RecaptchaClientProvider {
    override suspend fun fetchClient(application: Application, siteKey: String): RecaptchaClient {
        return mockk<RecaptchaClient>()
    }
    
    override suspend fun execute(client: RecaptchaClient, action: RecaptchaAction, timeoutInMillis: Long): String? {
        return if (shouldSucceed) "mock-token-123" else null
    }
}
```

---

## Testing

### Unit Testing

The module provides comprehensive test coverage. See `DefaultRecaptchaClientProviderTest` for examples of:

- Testing successful client fetching and token generation
- Testing error scenarios and exception handling
- Testing different ReCaptcha actions and timeout values
- Testing edge cases (empty site keys, zero/negative timeouts)

### Integration Testing

For integration tests, use the mock provider approach:

```kotlin
@Test
fun `test recaptcha verification flow`() = runTest {
    val callback = ReCaptchaEnterpriseCallback()
    callback.initForTest("recaptchaSiteKey", JsonPrimitive("test-site-key"))
    
    val result = callback.verify {
        setProvider(MockRecaptchaProvider(shouldSucceed = true))
    }
    
    assertTrue(result.isSuccess)
}
```

---

## Architecture

The module follows several design patterns:

- **Plugin/Callback Pattern**: Integrates with the Journey plugin system
- **Strategy Pattern**: Customizable RecaptchaClientProvider for different implementations
- **Builder Pattern**: DSL configuration for type-safe setup
- **Result Pattern**: Proper error handling with Kotlin's Result type

### Class Relationships

```
ReCaptchaEnterpriseCallback
    ├── ReCaptchaEnterpriseConfig (DSL)
    └── RecaptchaClientProvider (Strategy)
        └── DefaultRecaptchaClientProvider (Default Implementation)
```

For detailed architecture diagrams, see the [CONCEPT.md](CONCEPT.md) file.

---

## Troubleshooting

### Common Issues

#### 1. "RecaptchaClientProvider is not set" Error

**Cause:** No provider configured in the verify block.
**Solution:**
```kotlin
callback.verify {
    setProvider(DefaultRecaptchaClientProvider()) // Add this line
}
```

#### 2. "Invalid captcha token" Error

**Cause:** ReCaptcha verification failed or timed out.
**Solutions:**
- Check network connectivity
- Increase timeout value
- Verify site key configuration
- Check Google Cloud Console ReCaptcha settings

#### 3. Network/Timeout Issues

**Solutions:**
```kotlin
callback.verify {
    timeoutInMills = 20000L // Increase timeout
    setProvider(DefaultRecaptchaClientProvider())
}
```

#### 4. Site Key Issues

**Cause:** Invalid or misconfigured site key.
**Solutions:**
- Verify site key in Google Cloud Console
- Ensure key is enabled for your domain/app
- Check key type (Enterprise vs Standard)

### Debug Tips

1. Enable logging to see detailed error messages
2. Test with different timeout values
3. Verify network connectivity
4. Check Google Cloud Console for ReCaptcha configuration
5. Use mock providers for testing

---

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.
