# Journey Module Migration: Forgerock to Ping SDK

This document provides a comprehensive mapping of the Journey module from the legacy Forgerock SDK to the new Ping SDK. It is intended to be used as a reference for refactoring and migration efforts.

## 1. Architectural Shifts

The primary architectural shift is the move from a **callback-based asynchronous model to a modern, coroutine-based approach**.

*   **Legacy (Callbacks):** The legacy SDK used a `NodeListener` with methods like `onSuccess`, `onException`, and `onCallbackReceived`. This led to nested callbacks and made the code harder to follow.

*   **New (Coroutines):** The new Ping SDK embraces Kotlin Coroutines. Methods like `start` and `next` are `suspend` functions. This allows for writing asynchronous code in a sequential, synchronous-looking manner, which greatly improves readability and maintainability. The different outcomes of an operation are handled by the sealed `Node` class (`ContinueNode`, `SuccessNode`, `ErrorNode`, `FailureNode`), which allows for exhaustive `when` statements.

## 2. SDK Initialization

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

## 3. Starting Authentication & Handling Nodes
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

## 12. Method Mapping Table

| Legacy Method | New Ping Method | Parameter Changes | Return Type |
| :--- | :--- | :--- | :--- |
| `FRSession.authenticate(context, journeyName, listener)` | `journey.start(journeyName)` | - `context` is no longer passed directly to every method. <br>- The `listener` is replaced by the `suspend` function's return value. | `void` (asynchronous with listener) -> `Node` (synchronous-style with coroutines) |
| `node.next(context, listener)` | `continueNode.next()` | - `context` is no longer passed directly. <br>- The `listener` is replaced by the `suspend` function's return value. | `void` (asynchronous with listener) -> `Node` (synchronous-style with coroutines) |
| `FRUser.getCurrentUser()?.logout()` | `journey.user()?.logout()` | The new SDK provides a nullable `user` object from the journey, on which logout can be called. | `void` -> `void` |
| `FRUser.getCurrentUser()?.getUserInfo(...)` | `user.userinfo()` | The `FRListener` is replaced by a `Result` object. | `void` (asynchronous with listener) -> `Result<UserInfo, Exception>` |
| `FRUser.getCurrentUser()?.accessToken` | `journey.user()?.token` | Direct property access vs. a nullable property on the user object. | `AccessToken` -> `Token?` |
| `FRUser.getCurrentUser()?.revokeAccessToken(...)` | `journey.user()?.revoke()` | The `FRListener` is replaced by a `suspend` function. | `void` (asynchronous with listener) -> `suspend` function |
| `FRUser.getCurrentUser()?.refreshAccessToken(...)` | `journey.user()?.refresh()` | The `FRListener` is replaced by a `Result` object. | `void` (asynchronous with listener) -> `Result<Token, OidcError>` |

## 13. Data Model Translation

| Legacy SDK Class | New Ping SDK Model | Description |
| :--- | :--- | :--- |
| `FRSession` | `SuccessNode` / `Journey` | The `FRSession` object, which represents a successful login, is now represented by a `SuccessNode` returned by the journey. The `Journey` object itself holds the session state. |
| `Node` | `ContinueNode` | The `Node` object in the legacy SDK, which contains callbacks for user input, is now represented by a `ContinueNode`. |
| `Exception` in `onException` | `ErrorNode` / `FailureNode` | Errors and exceptions are now handled through sealed classes `ErrorNode` (for API errors) and `FailureNode` (for exceptions). |
| `Callback` | `Callback` | The `Callback` classes are similar in both SDKs, but the new SDK has a more structured approach to handling them within the `ContinueNode`. |
| `FROptions` | `JourneyConfig` | The SDK initialization options have been streamlined into a new `JourneyConfig` class with a builder-style configuration. |
| `UserInfo` | `UserInfo` | The `UserInfo` model remains, but it is now retrieved synchronously or with coroutines. |
| `AccessToken` | `Token` | The `AccessToken` model is now named `Token`. |

## 14. Package Name Changes

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

## 7. Context Parameter Removal

### What Changed
The legacy SDK required passing Android `Context` to most methods. The Ping SDK handles context internally through dependency injection at SDK initialization time.

### Legacy Pattern
```kotlin
FRSession.authenticate(context, journeyName, nodeListener)
node.next(context, nodeListener)
```

### Modern Pattern
```kotlin
var node: Node = journey.start("Login")
while (node is ContinueNode) {
    node = node.next()  // No context parameter needed
}
```

### Migration Guidance
- **SDK Initialization**: Context is provided once when the Journey is created
- **No Parameter Changes**: Methods no longer require context to be passed
- **Permission Handling**: Use Android's permission APIs within callbacks if needed
- **Device Operations**: Storage and device context are managed internally by the SDK

### Key Migration Point
In legacy SDK, context was required for every operation. In the Ping SDK, context is captured during initialization and reused internally.

---

## 8. Error Handling Strategy

### Understanding Node Types

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
    try {
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
    } catch (e: Exception) {
        logger.error("Unexpected exception", e)
        state.update { it.copy(error = "Unexpected error: ${e.message}") }
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

## 9. Step-up Authentication with PolicyAdvice

### What is PolicyAdvice?
PolicyAdvice is used for re-authentication when an existing session doesn't meet current security requirements. This is typically triggered by policy violations or when accessing sensitive resources.

### Use Cases
1. **MFA Challenge**: User needs to provide additional authentication factor
2. **Elevated Permissions**: Accessing sensitive data requires re-authentication
3. **Session Timeout**: Session expired and needs refreshing
4. **Risk Assessment**: Unusual login pattern requires additional verification
5. **Policy Violation**: Access attempt violates security policy

### Legacy Pattern
```kotlin
val policyAdvice = PolicyAdvice(adviceCode)
FRSession.getCurrentSession().authenticate(context, policyAdvice, nodeListener)
```

### Modern Pattern
```kotlin
val policyAdvice = PolicyAdvice(adviceCode)

viewModelScope.launch {
    try {
        var node: Node = journey.start("Login", advice = policyAdvice)
        
        while (node is ContinueNode) {
            node = node.next()
        }
        
        when (node) {
            is SuccessNode -> {
                logger.info("Step-up authentication successful")
                state.update { it.copy(session = node) }
            }
            is ErrorNode -> {
                logger.warn("Step-up failed: ${node.errorMessage}")
                state.update { it.copy(error = node.errorMessage) }
            }
            is FailureNode -> {
                logger.error("Step-up error", node.exception)
                state.update { it.copy(error = "Step-up authentication failed") }
            }
        }
    } catch (e: Exception) {
        logger.error("Step-up exception", e)
    }
}
```

### Real-World Implementation
```kotlin
class AuthViewModel(private val journey: Journey) : ViewModel() {
    fun startStepUpAuth(adviceCode: String) {
        val advice = PolicyAdvice(adviceCode)
        viewModelScope.launch {
            var node: Node = journey.start("StepUpAuth", advice = advice)
            processAuthenticationNode(node)
        }
    }
    
    private suspend fun processAuthenticationNode(initialNode: Node) {
        var node = initialNode
        
        while (node is ContinueNode) {
            // Display callbacks and process user input
            node = node.next()
        }
        
        when (node) {
            is SuccessNode -> handleStepUpSuccess()
            is ErrorNode -> handleStepUpError(node.errorMessage)
            is FailureNode -> handleStepUpFailure(node.exception)
        }
    }
}
```

### Migration Checklist
- [ ] Identify all places using PolicyAdvice in legacy code
- [ ] Update to pass advice parameter in journey.start()
- [ ] Update error handling to use new Node types
- [ ] Test step-up auth flows end-to-end
- [ ] Verify session handling after successful step-up

---

## 10. WebAuthn Configuration

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

### Resident Key Options & When to Use

| Option | Use Case | Benefit | Limitation |
|--------|----------|---------|-----------|
| **REQUIRED** | High-security apps | Faster login experience | Requires device storage |
| **PREFERRED** | Most applications | Best UX where available | Fallback needed |
| **DISCOURAGED** | Passwordless-only | Highest security | Credential doesn't persist |

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

### Server Configuration
Configure WebAuthn policy in AM/Ping console:
1. Navigate to Authentication → Policies
2. Select WebAuthn policy
3. Set "Resident Key Requirement" to your preferred setting
4. Save and test with your app

The resident key requirement will now be enforced by the server for all WebAuthn operations without needing code changes.

---

## 11. Implementation Examples

Here are before-and-after comparisons for common development scenarios.

## 15. User Logout
**Legacy**
```kotlin
FRUser.getCurrentUser()?.logout()
```
**Modern**
```kotlin
journey.user()?.logout()
```

## 16. Retrieving User Profile
**Legacy**
```kotlin
 FRUser.getCurrentUser()?.getUserInfo(object : FRListener<UserInfo> {
    override fun onSuccess(result: UserInfo) { /* ... */ }
    override fun onException(e: Exception) { /* ... */ }
})
```
**Modern**
```kotlin
when (val result = journey.user()?.userinfo(false)) {
  is Result.Failure -> { /* ... */ }
  is Result.Success -> { /* ... */ }
}
```

## 17. Access Token Management
**Legacy**
```kotlin
// Get Access Token
val accessToken = FRUser.getCurrentUser()?.accessToken

// Revoke Access Token
FRUser.getCurrentUser()?.revokeAccessToken(object : FRListener<Void?> {
    override fun onSuccess(result: Void?) { /* ... */ }
    override fun onException(e: Exception) { /* ... */ }
})

// Refresh Access Token
FRUser.getCurrentUser()?.refreshAccessToken(object : FRListener<AccessToken?> {
    override fun onSuccess(result: AccessToken?) { /* ... */ }
    override fun onException(e: Exception) { /* ... */ }
})
```
**Modern**
```kotlin
// Get Access Token
val token = journey.user()?.token

// Revoke Access Token (suspend function)
journey.user()?.revoke()

// Refresh Access Token
val result: Result<Token, OidcError> = journey.user()?.refresh()
```

## 18. WebAuthn Registration
**Legacy**
```kotlin
try {
    val callback = WebAuthRegistrationCallback()
    callback.setResidentKeyRequirement(ResidentKeyRequirement.RESIDENT_KEY_DISCOURAGED)
    callback.register(context, deviceName, node)
} catch (e: Exception) {
    // ...
}
```
**Modern**
```kotlin
val callback = FidoRegistrationCallback()
// callback.setResidentKeyRequirement is no longer supported directly in the same way
callback.register(deviceName).onSuccess {
    // ...
}.onFailure {
    // ...
}
```

## 19. WebAuthn Authentication
**Legacy**
```kotlin
try {
    val callback = WebAuthAuthenticationCallback()
    callback.authenticate(context, deviceName, node)
} catch (e: Exception) {
    // ...
}
```
**Modern**
```kotlin
val callback = FidoAuthenticationCallback()
callback.authenticate().onSuccess {
    // ...
}.onFailure {
    // ...
}
```

## 20. Centralize Login
**Legacy**
```kotlin
FRUser.browser().appAuthConfigurer().customTabsIntent {
    it.setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
}.appAuthConfiguration { appAuthConfiguration ->
}
    .done()
    .login(fragmentActivity,
        object : FRListener<FRUser> {
            override fun onSuccess(result: FRUser) {
            }
            override fun onException(e: Exception) {
            }
        })
```
**Modern**
```kotlin
var web = OidcWeb {
    logger = Logger.WARN
    module(com.pingidentity.oidc.module.Oidc) {
        clientId = oidcConfig.clientId
        discoveryEndpoint = oidcConfig.discoveryEndpoint
        scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
        redirectUri = oidcConfig.redirectUri
        signOutRedirectUri = oidcConfig.signOutRedirectUri
        loginHint = oidcConfig.loginHint
        state = oidcConfig.state
        nonce = oidcConfig.nonce
        acrValues = oidcConfig.acrValues
        prompt = oidcConfig.prompt
        display = oidcConfig.display
        uiLocales = oidcConfig.uiLocales
        additionalParameters = oidcConfig.additionalParameters
    }
    module(Web) {
        //Showcase Customization
        customTabsCustomizer = {
            setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
        }
        authTabCustomizer = {
            setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
        }
    }
}
web.authorize {}
    .onSuccess { user ->
    }.onFailure { throwable ->
    }
```

## 21. Build.gradle and Dependencies

To integrate the new Ping Identity SDK, you will need to update your `build.gradle.kts` file with the following dependencies. The SDK is modular, so you can include only the components you need.

### Example `build.gradle.kts`

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

### Example `libs.versions.toml`

```toml
[versions]
ping-sdk = "2.0.0-beta1"

[libraries]
ping-sdk-journey = { group = "com.pingidentity.sdks", name = "journey", version.ref = "ping-sdk" }
ping-sdk-orchestrate = { group = "com.pingidentity.sdks", name = "orchestrate", version.ref = "ping-sdk" }
ping-sdk-oidc = { group = "com.pingidentity.sdks", name = "oidc", version.ref = "ping-sdk" }
ping-sdk-device-profile = { group = "com.pingidentity.sdks", name = "device-profile", version.ref = "ping-sdk" }
ping-sdk-binding = { group = "com.pingidentity.sdks", name = "binding", version.ref = "ping-sdk" }
ping-sdk-push = { group = "com.pingidentity.sdks", name = "push", version.ref = "ping-sdk" }
ping-sdk-protect = { group = "com.pingidentity.sdks", name = "protect", version.ref = "ping-sdk" }
ping-sdk-davinci = { group = "com.pingidentity.sdks", name = "davinci", version.ref = "ping-sdk" }
ping-sdk-browser = { group = "com.pingidentity.sdks", name = "browser", version.ref = "ping-sdk" }
ping-sdk-utils = { group = "com.pingidentity.sdks", name = "utils", version.ref = "ping-sdk" }
ping-sdk-logger = { group = "com.pingidentity.sdks", name = "logger", version.ref = "ping-sdk" }
ping-sdk-storage = { group = "com.pingidentity.sdks", name = "storage", version.ref = "ping-sdk" }
ping-sdk-network = { group = "com.pingidentity.sdks", name = "network", version.ref = "ping-sdk" }
ping-sdk-device-id = { group = "com.pingidentity.sdks", name = "device-id", version.ref = "ping-sdk" }
ping-sdk-device-root = { group = "com.pingidentity.sdks", name = "device-root", version.ref = "ping-sdk" }
ping-sdk-device-profile = { group = "com.pingidentity.sdks", name = "device-profile", version.ref = "ping-sdk" }
ping-sdk-migration = { group = "com.pingidentity.sdks", name = "migration", version.ref = "ping-sdk" }
ping-sdk-oath = { group = "com.pingidentity.sdks", name = "oath", version.ref = "ping-sdk" }
ping-sdk-device-client = { group = "com.pingidentity.sdks", name = "device-client", version.ref = "ping-sdk" }
ping-sdk-binding-ui = { group = "com.pingidentity.sdks", name = "binding-ui", version.ref = "ping-sdk" }
ping-sdk-journey-plugin = { group = "com.pingidentity.sdks", name = "journey-plugin", version.ref = "ping-sdk" }
ping-sdk-davinci-plugin = { group = "com.pingidentity.sdks", name = "davinci-plugin", version.ref = "ping-sdk" }
ping-sdk-commons = { group = "com.pingidentity.sdks", name = "commons", version.ref = "ping-sdk" }
ping-sdk-android = { group = "com.pingidentity.sdks", name = "android", version.ref = "ping-sdk" }
```

### Available Libraries

The following libraries are available in the Ping Identity SDK. You can find the latest versions on [Maven Central](https://central.sonatype.com/namespace/com.pingidentity.sdks).

| Library | Description |
| :--- | :--- |
| `journey` | Core library for handling authentication journeys. |
| `orchestrate` | Handles the orchestration of journey nodes. |
| `oidc` | Provides OpenID Connect (OIDC) functionality. |
| `device-profile` | Enables device profiling for risk assessment. |
| `binding` | Used for binding devices to user accounts. |
| `push` | Manages push notifications for multi-factor authentication. |
| `protect` | Integrates with PingOne Protect for advanced fraud detection. |
| `davinci` | Allows integration with PingOne DaVinci orchestration flows. |
| `browser` | Utilities for handling web-based authentication flows. |
| `utils` | Common utility classes used across the SDK. |
| `logger` | A logging library for the SDK. |
| `storage` | Provides secure storage for SDK data. |
| `network` | Handles network requests for the SDK. |
| `device-id` | Provides a unique device identifier. |
| `device-root` | Detects if the device is rooted or jailbroken. |
| `migration` | Assists with migrating from older SDK versions. |
| `oath` | Implements the OATH (Initiative for Open Authentication) standard. |
| `device-client` | A client for device-related operations. |
| `binding-ui` | UI components for device binding. |
| `journey-plugin` | A plugin for extending journey functionality. |
| `davinci-plugin` | A plugin for extending DaVinci integration. |
| `commons` | Common classes for multi-factor authentication. |
| `android` | Core Android components for the SDK. |
