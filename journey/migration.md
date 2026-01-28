# Journey Module Migration: Forgerock to Ping SDK

This document provides a comprehensive mapping of the Journey module from the legacy Forgerock SDK to the new Ping SDK. It is intended to be used as a reference for refactoring and migration efforts.

## Migration Overview

The primary architectural shift is the move from a **callback-based asynchronous model to a modern, coroutine-based approach**.

*   **Legacy (Callbacks):** The legacy SDK used a `NodeListener` with methods like `onSuccess`, `onException`, and `onCallbackReceived`. This led to nested callbacks and made the code harder to follow.

*   **New (Coroutines):** The new Ping SDK embraces Kotlin Coroutines. Methods like `start` and `next` are `suspend` functions. This allows for writing asynchronous code in a sequential, synchronous-looking manner, which greatly improves readability and maintainability. The different outcomes of an operation are handled by the sealed `Node` class (`ContinueNode`, `SuccessNode`, `ErrorNode`, `FailureNode`), which allows for exhaustive `when` statements.

## Example: SDK Initialization

### Legacy
```kotlin
 val PingAM = FROptionsBuilder.build {
    server {
        url = "https://iam.dev.thrivent.com/am"
        realm = "alpha"
        cookieName = "c4891df37ce0971"
        timeout = 50
    }
    oauth {
        oauthClientId = "PingTest"
        oauthRedirectUri = "org.forgerock.demo://oauth2redirect"
        oauthScope = "openid profile email address"
        oauthSignOutRedirectUri = "org.forgerock.demo://oauth2redirect"
    }
    service {
        authServiceName = "sdkUsernamePasswordJourney"
    }
}
```
### Modern
```kotlin
val journey = Journey {
    logger = Logger.STANDARD
    serverUrl = "https://openam-sdks.forgeblocks.com/am"
    realm = "alpha"
    cookie = "5421aeddf91aa20"
    // Oidc as module
    module(Oidc) {
        clientId = "AndroidTest"
        discoveryEndpoint =
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration"
        scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
        redirectUri = "org.forgerock.demo:/oauth2redirect"
        //storage = dataStore
    }
}
```

## Example: Starting Authentication and Handling Nodes
### Legacy
```kotlin
private val nodeListener = object : NodeListener<FRSession> {
    override fun onSuccess(result: FRSession) {
        // Handle successful login
    }

    override fun onException(e: Exception) {
        // Handle error
    }

    override fun onCallbackReceived(node: Node) {
        // Process node and set callbacks
        node.next(context, this)
    }
}
FRSession.authenticate(context, "Login", nodeListener)
```
### Modern
```kotlin
// In a ViewModel, using viewModelScope
viewModelScope.launch {
    try {
        var node: Node = journey.start("Login")

        while (node is ContinueNode) {
            // Process node and set callbacks
            node = (node as ContinueNode).next()
        }

        when (node) {
            is SuccessNode -> { /* Handle success */ }
            is ErrorNode -> { /* Handle API error */ }
            is FailureNode -> { /* Handle exception */ }
        }
    } catch (e: Exception) {
        // Handle exceptions
    }
}
```

## Reference: Method Mapping

| Legacy Method | New Ping Method | Parameter Changes | Return Type |
| :--- | :--- | :--- | :--- |
| `FRSession.authenticate(context, journeyName, listener)` | `journey.start(journeyName)` | - `context` is no longer passed directly to every method. <br>- The `listener` is replaced by the `suspend` function's return value. | `void` (asynchronous with listener) -> `Node` (synchronous-style with coroutines) |
| `node.next(context, listener)` | `continueNode.next()` | - `context` is no longer passed directly. <br>- The `listener` is replaced by the `suspend` function's return value. | `void` (asynchronous with listener) -> `Node` (synchronous-style with coroutines) |
| `FRUser.getCurrentUser()?.logout()` | `journey.user()?.logout()` | The new SDK provides a nullable `user` object from the journey, on which logout can be called. | `void` -> `void` |
| `FRUser.getCurrentUser()?.getUserInfo(...)` | `user.userinfo()` | The `FRListener` is replaced by a `Result` object. | `void` (asynchronous with listener) -> `Result<UserInfo, Exception>` |
| `FRUser.getCurrentUser()?.accessToken` | `journey.user()?.token` | Direct property access vs. a nullable property on the user object. | `AccessToken` -> `Token?` |
| `FRUser.getCurrentUser()?.revokeAccessToken(...)` | `journey.user()?.revoke()` | The `FRListener` is replaced by a `suspend` function. | `void` (asynchronous with listener) -> `suspend` function |
| `FRUser.getCurrentUser()?.refreshAccessToken(...)` | `journey.user()?.refresh()` | The `FRListener` is replaced by a `Result` object. | `void` (asynchronous with listener) -> `Result<Token, OidcError>` |

## Reference: Data Model Translation

| Legacy SDK Class | New Ping SDK Model | Description |
| :--- | :--- | :--- |
| `FRSession` | `SuccessNode` / `Journey` | The `FRSession` object, which represents a successful login, is now represented by a `SuccessNode` returned by the journey. The `Journey` object itself holds the session state. |
| `Node` | `ContinueNode` | The `Node` object in the legacy SDK, which contains callbacks for user input, is now represented by a `ContinueNode`. |
| `Exception` in `onException` | `ErrorNode` / `FailureNode` | Errors and exceptions are now handled through sealed classes `ErrorNode` (for API errors) and `FailureNode` (for exceptions). |
| `Callback` | `Callback` | The `Callback` classes are similar in both SDKs, but the new SDK has a more structured approach to handling them within the `ContinueNode`. |
| `FROptions` | `JourneyConfig` | The SDK initialization options have been streamlined into a new `JourneyConfig` class with a builder-style configuration. |
| `UserInfo` | `UserInfo` | The `UserInfo` model remains, but it is now retrieved synchronously or with coroutines. |
| `AccessToken` | `Token` | The `AccessToken` model is now named `Token`. |

## Example: Package Import Changes

The package names for callbacks and other classes have been updated to reflect the new modular structure of the Ping SDK.

### Legacy
```kotlin
import org.forgerock.android.auth.callback.NameCallback
import org.forgerock.android.auth.callback.PasswordCallback
import org.forgerock.android.auth.callback.WebAuthnAuthenticationCallback
import org.forgerock.android.auth.callback.WebAuthnRegistrationCallback
// ... and others
```

### Modern
```kotlin
import com.pingidentity.journey.callback.NameCallback
import com.pingidentity.journey.callback.PasswordCallback
import com.pingidentity.fido.journey.FidoAuthenticationCallback
import com.pingidentity.fido.journey.FidoRegistrationCallback
// ... and others, now in more specific modules like 'device.binding', 'device.profile', 'fido', 'idp', 'protect', 'recaptcha'.
```

## Example: Error Handling with Node Types

The Ping SDK uses sealed classes to represent different outcomes of an authentication operation.

#### SuccessNode
- **When**: Authentication completed successfully
- **Contains**: Session and user information
- **Action**: Transition to authenticated state
- **Code**:
```kotlin
is SuccessNode -> {
    logger.info("Authentication successful")
    saveSession(node)
}
```

#### ContinueNode
- **When**: More steps required (callbacks need to be filled)
- **Contains**: List of callbacks for user input
- **Action**: Display callback UI and call next()
- **Code**:
```kotlin
is ContinueNode -> {
    displayCallbacks(node.callbacks)
    val nextNode = node.next()
}
```

#### ErrorNode
- **When**: API/Server returned an expected error response
- **Difference from FailureNode**: Expected error from authentication logic (user error, not system error)
- **Examples**: Invalid credentials, locked account, MFA failed, policy violation
- **Contains**: Error code and message from server
- **Action**: User-recoverable, allow retry
- **Code**:
```kotlin
is ErrorNode -> {
    logger.error("Authentication failed: ${node.errorMessage}")
    showUserFriendlyError(node.errorMessage)
    state.update { it.copy(error = node.errorMessage, allowRetry = true) }
}
```

#### FailureNode
- **When**: Unexpected exception occurred (system/network error)
- **Difference from ErrorNode**: Unexpected exception, not part of normal auth flow
- **Examples**: Network timeout, JSON parse error, SDK configuration error
- **Contains**: Exception object with details
- **Action**: Non-recoverable situation, may suggest app restart
- **Code**:
```kotlin
is FailureNode -> {
    logger.error("Unexpected error occurred", node.exception)
    showSystemError("An unexpected error occurred. Please try again.")
    state.update { it.copy(error = node.exception.message, allowRetry = false) }
}
```

### Complete Error Handling Example
```kotlin
viewModelScope.launch {
    var node: Node = journey.start("Login")

    while (node is ContinueNode) {
        node = node.next()
    }

    when (node) {
        is SuccessNode -> {
            logger.info("Login successful")
            state.update { it.copy(isLoggedIn = true, error = null) }
        }
        is ErrorNode -> {
            logger.warn("Auth error: ${node.errorMessage}")
            state.update { it.copy(error = node.errorMessage) }
        }
        is FailureNode -> {
            logger.error("System error", node.exception)
            state.update { it.copy(error = "System error: ${node.exception.message}") }
        }
    }
}
```

### Legacy vs Modern Comparison
| Aspect | Legacy | Modern |
|--------|--------|--------|
| **Error handling** | `onException(e: Exception)` callback | `is FailureNode` in when statement |
| **API errors** | Caught in `onException` | `is ErrorNode` with error message |
| **User recovery** | Manual decision in callback | Different handling for ErrorNode vs FailureNode |
| **Control flow** | Scattered across callbacks | Sequential in try-catch block |

---

## Example: WebAuthn Configuration

### Resident Key Requirements

#### Legacy Approach
Resident key requirements were set on the callback in code:
```kotlin
val callback = WebAuthRegistrationCallback()
callback.setResidentKeyRequirement(ResidentKeyRequirement.RESIDENT_KEY_DISCOURAGED)
callback.register(context, deviceName, node)
```

#### Modern Approach
Resident key configuration is now managed server-side through AM/Ping policy. The SDK automatically respects the server's requirements without overriding.

### What Changed
- **Removed**: `setResidentKeyRequirement()` method on callback
- **Added**: Server-side policy configuration
- **Benefit**: Centralized security policy, no app redeployment needed for changes

### Migration Path
1. **Remove** `setResidentKeyRequirement()` calls from your code
2. **Configure** resident key policy on AM/Ping admin console
3. **Test** that server policy is applied correctly during WebAuthn registration

### Modern WebAuthn Registration Example
```kotlin
val callback = FidoRegistrationCallback()
callback.register(deviceName)
    .onSuccess { result ->
        logger.info("WebAuthn registration successful")
        state.update { it.copy(registered = true) }
    }
    .onFailure { error ->
        logger.error("WebAuthn registration failed", error)
        state.update { it.copy(error = error.message) }
    }
```

### Modern WebAuthn Authentication Example
```kotlin
val callback = FidoAuthenticationCallback()
callback.authenticate()
    .onSuccess { result ->
        logger.info("WebAuthn authentication successful")
        state.update { it.copy(authenticated = true) }
    }
    .onFailure { error ->
        logger.error("WebAuthn authentication failed", error)
        state.update { it.copy(error = error.message) }
    }
```

## Example: Common Migration Patterns

Quick reference for common migration patterns:

### User Logout
```kotlin
// Legacy
FRUser.getCurrentUser()?.logout()

// Modern
journey.user()?.logout()
```

### Retrieving User Profile
```kotlin
// Legacy
FRUser.getCurrentUser()?.getUserInfo(object : FRListener<UserInfo> {
    override fun onSuccess(result: UserInfo) { /* ... */ }
    override fun onException(e: Exception) { /* ... */ }
})

// Modern
when (val result = journey.user()?.userinfo(false)) {
    is Result.Failure -> { /* ... */ }
    is Result.Success -> { /* ... */ }
}
```

### Access Token Management
```kotlin
// Legacy - Get Access Token
val accessToken = FRUser.getCurrentUser()?.accessToken

// Modern - Get Access Token
val token = journey.user()?.token

// Legacy - Refresh Token
FRUser.getCurrentUser()?.refreshAccessToken(object : FRListener<AccessToken?> {
    override fun onSuccess(result: AccessToken?) { /* ... */ }
    override fun onException(e: Exception) { /* ... */ }
})

// Modern - Refresh Token
val result: Result<Token, OidcError> = journey.user()?.refresh()
```

---

## Configuration: Gradle Dependencies

To integrate the new Ping Identity SDK, update your `build.gradle.kts` with dependencies. The SDK is modular, so include only what you need.

### Dependencies
```kotlin
dependencies {
    // Core SDK
    implementation(libs.ping.sdk.journey)
    implementation(libs.ping.sdk.orchestrate)

    // Optional Modules
    implementation(libs.ping.sdk.oidc) // For OIDC
    implementation(libs.ping.sdk.device.profile) // For device profiling
    implementation(libs.ping.sdk.binding) // For device binding
    implementation(libs.ping.sdk.push) // For push notifications
    implementation(libs.ping.sdk.protect) // For PingOne Protect
    implementation(libs.ping.sdk.davinci) // For DaVinci integration

    // UI and other utilities
    implementation(libs.ping.sdk.browser)
    implementation(libs.ping.sdk.utils)
    implementation(libs.ping.sdk.logger)
    implementation(libs.ping.sdk.storage)
    implementation(libs.ping.sdk.network)
}
```

### Available Libraries

The following libraries are available in the Ping Identity SDK. You can find the latest versions on [Maven Central](https://central.sonatype.com/namespace/com.pingidentity.sdks).

| Library | Description |
| :--- | :--- |
| `android` | Core Android components for the SDK. |
| `binding` | Used for binding devices to user accounts. |
| `binding-ui` | UI components for device binding. |
| `browser` | Utilities for handling web-based authentication flows. |
| `commons` | Common classes for multi-factor authentication. |
| `davinci` | Allows integration with PingOne DaVinci orchestration flows. |
| `davinci-plugin` | A plugin for extending DaVinci integration. |
| `device-client` | A client for device-related operations. |
| `device-id` | Provides a unique device identifier. |
| `device-profile` | Enables device profiling for risk assessment. |
| `device-root` | Detects if the device is rooted or jailbroken. |
| `journey` | Core library for handling authentication journeys. |
| `journey-plugin` | A plugin for extending journey functionality. |
| `logger` | A logging library for the SDK. |
| `migration` | Assists with migrating from older SDK versions. |
| `network` | Handles network requests for the SDK. |
| `oath` | Implements the OATH (Initiative for Open Authentication) standard. |
| `oidc` | Provides OpenID Connect (OIDC) functionality. |
| `orchestrate` | Handles the orchestration of journey nodes. |
| `protect` | Integrates with PingOne Protect for advanced fraud detection. |
| `push` | Manages push notifications for multi-factor authentication. |
| `storage` | Provides secure storage for SDK data. |
| `utils` | Common utility classes used across the SDK. |
