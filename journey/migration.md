# Journey Module Migration: Forgerock to Ping SDK

This document provides a comprehensive mapping of the Journey module from the legacy Forgerock SDK to the new Ping SDK. It is intended to be used as a reference for refactoring and migration efforts. The examples are based on real-world implementations and demonstrate the key architectural and API changes.


## Migration Overview

The primary architectural shift is the move from a **callback-based asynchronous model to a modern, coroutine-based approach**.

*   **Legacy (Callbacks):** The legacy SDK used a `NodeListener` with methods like `onSuccess`, `onException`, and `onCallbackReceived`. This led to nested callbacks and made the code harder to follow.

*   **New (Coroutines):** The new Ping SDK embraces Kotlin Coroutines. Methods like `start` and `next` are `suspend` functions. This allows for writing asynchronous code in a sequential, synchronous-looking manner, which greatly improves readability and maintainability. The different outcomes of an operation are handled by the sealed `Node` class (`ContinueNode`, `SuccessNode`, `ErrorNode`, `FailureNode`), which allows for exhaustive `when` statements.

## Quick Reference

| Legacy SDK | Modern Ping SDK | Key Changes |
|------------|-----------------|-------------|
| `FRSession.authenticate(context, journeyName, listener)` | `journey.start(journeyName)` | No context parameter, returns Node directly |
| `node.next(context, listener)` | `continueNode.next()` | Context captured at SDK init, suspend function |
| `FRUser.getCurrentUser()` | `journey.user()` | Async property access, nullable |
| `FRUser.getCurrentUser()?.logout()` | `journey.user()?.logout()` | Direct suspend function, no callback |
| `FRUser.getCurrentUser()?.getUserInfo(listener)` | `user.userinfo()` | Returns Result type, suspend function |
| `FRUser.getCurrentUser()?.accessToken` | `journey.user()?.token` | Direct property access, Token instead of AccessToken |
| `NodeListener` callbacks | Sealed `Node` types | ContinueNode, SuccessNode, ErrorNode, FailureNode |
| `onException(e)` callback | `is ErrorNode` or `is FailureNode` | Explicit error type discrimination |

---


## Example: SDK Initialization

#### Legacy
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
#### Modern
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
#### Legacy
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
#### Modern
```kotlin
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

---

## Example: Common Migration Patterns

Quick reference for common migration patterns:

### User Logout
#### Legacy
```kotlin
FRUser.getCurrentUser()?.logout()
```
#### Modern
```kotlin
journey.user()?.logout()
```

### Retrieving User Profile
#### Legacy
```kotlin
FRUser.getCurrentUser()?.getUserInfo(object : FRListener<UserInfo> {
    override fun onSuccess(result: UserInfo) { /* ... */ }
    override fun onException(e: Exception) { /* ... */ }
})
```
#### Modern
```kotlin
when (val result = journey.user()?.userinfo(false)) {
    is Result.Failure -> { /* ... */ }
    is Result.Success -> { /* ... */ }
}
```

### Access Token Management
#### Legacy
```kotlin
// Get Access Token
val accessToken = FRUser.getCurrentUser()?.accessToken

// Refresh Token
FRUser.getCurrentUser()?.refreshAccessToken(object : FRListener<AccessToken?> {
    override fun onSuccess(result: AccessToken?) { /* ... */ }
    override fun onException(e: Exception) { /* ... */ }
})
```

#### Modern
```kotlin
// Get Access Token
val token = journey.user()?.token

// Refresh Token
val result: Result<Token, OidcError> = journey.user()?.refresh()
```

---

## Example: Social Login
Configuration should be done on the server to enable different IDP such as apple, google, facebook.

### IDP Callback
#### Legacy
```kotlin

// Callback: IdPCallback
if (callback is IdPCallback) {
    callback.signIn(context, handler, object : FRListener<String> {
        override fun onSuccess(result: String) {
            node.next(context, nodeListener)
        }
        override fun onException(e: Exception) {
            logger.error("Sign-in failed", e)
        }
    })
}
```

#### Modern
```kotlin
// Callback: IdpCallback
if (callback is IdpCallback) {
    callback.authorize(redirectUri) { result ->
        when (result) {
            is Result.Success -> { idpResult
                logger.info("Sign in successful ${idpResult.token}")
                node = node.next()
            }
            is Result.Failure -> {
                logger.error("IDP sign-in failed", result.error)
            }
        }
    }
}
```

---
## Example: WebAuthn Registration

#### Legacy
```kotlin
val callback = WebAuthRegistrationCallback()
callback.setResidentKeyRequirement(ResidentKeyRequirement.RESIDENT_KEY_DISCOURAGED)
callback.register(context, deviceName, node)
```

#### Modern
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

## Example: WebAuthn Authentication

#### Legacy
```kotlin
val callback = WebAuthAuthenticationCallback()
callback.authenticate(context, deviceName, node)
```

#### Modern
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

---

## Example: Device Binding Callback

#### Legacy
```kotlin
val callback = DeviceBindingCallback()
callback.bind(context, deviceName, object : FRListener<String> {
    override fun onSuccess(result: String) {
        logger.info("Device bound successfully")
        node.next(context, nodeListener)
    }
    override fun onException(e: Exception) {
        logger.error("Device binding failed", e)
    }
})
```

#### Modern
```kotlin
val result = callback.bind {
    this.deviceName = deviceName
    appPinConfig {
        pinCollector { prompt ->
            // Trigger UI to show dialog
            pinDeferred = CompletableDeferred()
            activePinPrompt = prompt
            // Suspend until user supplies PIN (or cancellation)
            pinDeferred!!.await().also {
                // Once consumed, clear state so dialog closes
                activePinPrompt = null
            }
        }
        //pinCollector = { prompt -> collectPin(prompt) }
    }
    biometricAuthenticatorConfig {
        keyGenParameterSpec {
            //setUnlockedDeviceRequired(true)
            //setUserAuthenticationValidWhileOnBody(true)
            //setUserPresenceRequired(true)
            //setIsStrongBoxBacked(false)
            //setInvalidatedByBiometricEnrollment(false)
        }
    }


}.onFailure {
}
```

## Example: Device Profiling Callback

#### Legacy
```
FRDeviceCollectorBuilder builder = FRDeviceCollector.builder();
if (metadata) {
    builder.collector(new MetadataCollector());
}
if (location) {
    builder.collector(new LocationCollector());
}

builder.build().collect(context, new FRListener<JSONObject>() {
    @Override
    public void onSuccess(JSONObject result) {
        setValue(result.toString());
        Listener.onSuccess(listener, null);
    }

    @Override
    public void onException(Exception e) {
        Listener.onException(listener, e);
    }
}
```

#### Modern
```kotlin
val result = deviceProfileCallback.collect {
    collectors {
        clear()
        add(PlatformCollector())      // OS, device model, security
        add(HardwareCollector())       // CPU, memory, display
        add(NetworkCollector())        // Network connectivity
        add(TelephonyCollector)        // Carrier information
        add(BluetoothCollector)        // Bluetooth support
        add(BrowserCollector)          // Browser/WebView info
    }
}
```

## Example: Device Identifier
#### Legacy
```kotlin
DeviceIdentifier.builder().context(applicationContext).build().identifier
```

#### Modern
```kotlin
DefaultDeviceIdentifier.id()
```

## Example: PingOne Protect
### Initialization

#### Legacy
```kotlin
val nodeListenerFuture = object : PingOneProtectInitializeCallback {
    val nodeListener: NodeListener<FRSession?> = this
    override fun onCallbackReceived(node: Node) {
        node.getCallback(PingOneProtectInitializeCallback::class.java)?.let {
            it.start(context)
        }
        return
    }
} 
```
#### Modern
```kotlin
val callback = PingOneProtectInitializeCallback()
callback.start().onSuccess {
    logger.info("PingOne Protect initialization successful")
}.onFailure { error ->
    logger.error("PingOne Protect initialization failed", error)
}
```

### Evaluation
```kotlin
val nodeListenerFuture: PingOneProtectNodeListener = object : PingOneProtectNodeListener(
    context, "evaluate-default"
) {
    val nodeListener: NodeListener<FRSession?> = this
    override fun onCallbackReceived(node: Node) {
        node.getCallback(PingOneProtectEvaluationCallback::class.java)?.let {
            it.getData(context)
            node.next(context, nodeListener)
            return
        }
        super.onCallbackReceived(node)
    }
}
FRSession.authenticate(context, authenticationTree, nodeListenerFuture)
```

#### Modern
```kotlin
val callback = PingOneProtectEvaluationCallback()
callback.collect().onSuccess {
    logger.info("PingOne Protect evaluation successful")
}.onFailure { error ->
    logger.error("PingOne Protect evaluation failed", error)
}
```
---

## Example: ReCAPTCHA Enterprise

#### Legacy
```kotlin
val callback = ReCaptchaEnterpriseCallback()
callback.execute(application = application)
```

#### Modern
```kotlin
val reCaptchaEnterpriseCallback = ReCaptchaEnterpriseCallback()
reCaptchaEnterpriseCallback.verify {
    // Optionally customize the configuration here
    // config.payload = mapOf("custom_key" to "custom_value")
}.onSuccess { result ->
    logger.info("ReCAPTCHA Token Result: $result")
    onNext()
}.onFailure { error ->
    logger.error("ReCAPTCHA Verification Failed: ${error.message}", error)
    onNext() // Proceed to next step (or handle error differently)
}
```

## Example: Resume Authentication Flow (Suspended Email Node)

#### Legacy
```kotlin
// In MainActivity or Activity handling deep links
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val resumeUri = intent?.data // Contains 'suspendedId' parameter
    if (resumeUri != null) {
        // Resume authentication with URI
        FRSession.authenticate(context, resumeUri, nodeListener)
    }
}

private val nodeListener = object : NodeListener<FRSession> {
    override fun onSuccess(result: FRSession) {
        logger.info("Authentication successful after resume")
        navigateToHome()
    }
    override fun onCallbackReceived(node: Node) {
        // Handle node
        node.next(context, this)
    }
    override fun onException(e: Exception) {
        logger.error("Resume authentication failed", e)
    }
}
```

#### Modern
```kotlin
// In Activity or ViewModel handling deep links
val resumeUri = intent?.data // Contains 'suspendedId' parameter
if (resumeUri != null) {
    try {
        // Resume authentication flow
        var node: Node = journey.resume(uri = resumeUri)
        
        // Process callbacks
        node = node.next()
        
        when (node) {
            is SuccessNode -> {
                logger.info("Authentication successful after resume")
            }
            is ErrorNode -> {
                logger.warn("Resume authentication error: ${node.errorMessage}")
                showError(node.errorMessage)
            }
            is FailureNode -> {
                logger.error("Resume authentication failed", node.exception)
                showError("Authentication failed. Please try again.")
            }
        }
    } catch (e: Exception) {
        logger.error("Resume authentication exception", e)
        showError("An error occurred during authentication")
    }
}
```

## Example: Checking Current User Session

#### Legacy
```kotlin
// Check if user is authenticated
val currentUser = FRUser.getCurrentUser()
if (currentUser != null) {
    logger.info("User is authenticated: ${currentUser.id}")
    // User is logged in
} else {
    logger.info("No authenticated user")
    // Show login screen
}

// Get user info
currentUser?.getUserInfo(object : FRListener<UserInfo> {
    override fun onSuccess(result: UserInfo) {
        logger.info("User info: ${result.name}")
    }
    override fun onException(e: Exception) {
        logger.error("Failed to get user info", e)
    }
})
```

#### Modern
```kotlin
journey.user()?.let {
    logger.info("User is authenticated: ${user.id}")
    // User is logged in

    // Get user info asynchronously
    when (val result = user.userinfo(false)) {
        is Result.Success -> {
            val userInfo = result.value
            logger.info("User info: ${userInfo.name}")
            state.update { it.copy(userInfo = userInfo) }
        }
        is Result.Failure -> {
            logger.error("Failed to get user info", result.error)
            state.update { it.copy(error = "Failed to load user info") }
        }
    }
} ?: run {
    logger.warn("No authenticated user")
    null
}
```

---

## Example: Centralized Login (Browser-based OIDC)

#### Legacy
```kotlin
FRUser.browser().appAuthConfigurer().customTabsIntent {
    it.setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
}.appAuthConfiguration { appAuthConfiguration ->
    // Additional configuration
}
.done()
.login(fragmentActivity,
    object : FRListener<FRUser> {
        override fun onSuccess(result: FRUser) {
            logger.info("Browser login successful")
        }
        override fun onException(e: Exception) {
            logger.error("Browser login failed", e)
        }
    })
```

#### Modern
```kotlin
var oidcWeb = OidcWeb {
    // Existing Oidc config
}

oidcWeb.authorize {
    // Additional config
}.onSuccess { user ->
        logger.info("Browser login successful")
    }
    .onFailure { error ->
        logger.error("Browser login failed", error)
    }
```

## Example: Centralized Logout (Browser-based OIDC)

#### Legacy
```kotlin
FRUser.browser().logout(fragmentActivity,
    object : FRListener<Void?> {
        override fun onSuccess(result: Void?) {
            logger.info("Browser logout successful")
            navigateToLogin()
        }
        override fun onException(e: Exception) {
            logger.error("Browser logout failed", e)
            // Still navigate to login even if logout fails
            navigateToLogin()
        }
    })
```

#### Modern
```kotlin
val oidcWeb = /* initialized OidcWeb instance */
oidcWeb.user()?.logout()
```

## Example: Getting Access Token After Session

#### Legacy
```kotlin
val currentUser = FRUser.getCurrentUser()
currentUser.getAccessToken(object : FRListener<AccessToken> {
    override fun onSuccess(result: AccessToken) {
        val token = result.value
        val expiresAt = result.expiresAt
        logger.info("Token expires at: $expiresAt")

        // Use token for API requests
        makeAuthenticatedRequest(token)
    }
    override fun onException(e: Exception) {
        logger.error("Failed to get access token", e)

        // Try to refresh token
        currentUser.refreshAccessToken(object : FRListener<AccessToken?> {
            override fun onSuccess(result: AccessToken?) {
                if (result != null) {
                    makeAuthenticatedRequest(result.value)
                }
            }
            override fun onException(e: Exception) {
                logger.error("Token refresh failed", e)
                // Token expired, require re-authentication
            }
        })
    }
})
```

#### Modern
```kotlin
val user = journey.user()
if (user != null) {
    // Get current access token
    val token = user.token
    if (token != null) {
        logger.info("Token expires at: ${token.expiresAt}")

        // Check if token is expired or about to expire
        if (token.isExpired || token.expiresIn < 60) {
            // Refresh token
            when (val result = user.refresh()) {
                is Result.Success -> {
                    val refreshedToken = result.value
                    logger.info("Token refreshed")
                    makeAuthenticatedRequest(refreshedToken)
                }
                is Result.Failure -> {
                    // Token refresh failed, require re-authentication
                    logger.error("Token refresh failed", result.error)
                    // navigate to login
                }
            }
        } else {
            // Token is still valid
            makeAuthenticatedRequest(token)
        }
    } else {
        logger.warn("No access token available")
        // navigate to login
    }
} else {
    logger.warn("No authenticated user")
    // navigate to login
}
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
