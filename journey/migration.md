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

## Example: Social Login (Google)

### Legacy
```kotlin
import org.forgerock.android.auth.idp.GoogleSignInHandler

// First callback: SelectIdPCallback
if (callback is SelectIdPCallback) {
    val providersArray = callback.providers
    // Display providers to user and get selection
    val googleProvider = providersArray.first { it.provider == "google" }
    callback.setProvider(googleProvider)
    
    node.next(context, nodeListener)
}

// Second callback: IdPCallback
if (callback is IdPCallback) {
    val handler = GoogleSignInHandler()
    callback.signIn(context, handler, object : FRListener<String> {
        override fun onSuccess(result: String) {
            node.next(context, nodeListener)
        }
        override fun onException(e: Exception) {
            logger.error("Google sign-in failed", e)
        }
    })
}
```

### Modern
```kotlin
import com.pingidentity.idp.callback.SelectIdpCallback
import com.pingidentity.idp.callback.IdpCallback

// First callback: SelectIdpCallback
if (callback is SelectIdpCallback) {
    val providersArray = callback.providers
    // Display providers to user and get selection
    val googleProvider = providersArray.first { it.provider == "google" }
    callback.setProvider(googleProvider)
    
    node = node.next()
}

// Second callback: IdpCallback
if (callback is IdpCallback) {
    callback.idpSignIn(activity) { result ->
        when (result) {
            is Result.Success -> {
                logger.info("Google sign-in successful")
                node = node.next()
            }
            is Result.Failure -> {
                logger.error("Google sign-in failed", result.error)
            }
        }
    }
}
```

## Example: Social Login (Facebook)

### Legacy
```kotlin
import org.forgerock.android.auth.idp.FacebookSignInHandler

// First callback: SelectIdPCallback
if (callback is SelectIdPCallback) {
    val providersArray = callback.providers
    // Display providers to user and get selection
    val facebookProvider = providersArray.first { it.provider == "facebook" }
    callback.setProvider(facebookProvider)
    
    node.next(context, nodeListener)
}

// Second callback: IdPCallback
if (callback is IdPCallback) {
    val handler = FacebookSignInHandler()
    callback.signIn(context, handler, object : FRListener<String> {
        override fun onSuccess(result: String) {
            node.next(context, nodeListener)
        }
        override fun onException(e: Exception) {
            logger.error("Facebook sign-in failed", e)
        }
    })
}
```

### Modern
```kotlin
import com.pingidentity.idp.callback.SelectIdpCallback
import com.pingidentity.idp.callback.IdpCallback

// First callback: SelectIdpCallback
if (callback is SelectIdpCallback) {
    val providersArray = callback.providers
    // Display providers to user and get selection
    val facebookProvider = providersArray.first { it.provider == "facebook" }
    callback.setProvider(facebookProvider)
    
    node = node.next()
}

// Second callback: IdpCallback
if (callback is IdpCallback) {
    callback.idpSignIn(activity) { result ->
        when (result) {
            is Result.Success -> {
                logger.info("Facebook sign-in successful")
                node = node.next()
            }
            is Result.Failure -> {
                logger.error("Facebook sign-in failed", result.error)
            }
        }
    }
}
```

## Example: Social Login (Apple)

### Legacy
```kotlin
import androidx.credentials.CredentialManager

// First callback: SelectIdPCallback
if (callback is SelectIdPCallback) {
    val providersArray = callback.providers
    // Display providers to user and get selection
    val appleProvider = providersArray.first { it.provider == "apple" }
    callback.setProvider(appleProvider)
    
    node.next(context, nodeListener)
}

// Second callback: IdPCallback
if (callback is IdPCallback) {
    // Legacy SDK relies on external Apple Sign In library
    // Manual implementation needed
    node.next(context, nodeListener)
}
```

### Modern
```kotlin
import com.pingidentity.idp.callback.SelectIdpCallback
import com.pingidentity.idp.callback.IdpCallback

// First callback: SelectIdpCallback
if (callback is SelectIdpCallback) {
    val providersArray = callback.providers
    // Display providers to user and get selection
    val appleProvider = providersArray.first { it.provider == "apple" }
    callback.setProvider(appleProvider)
    
    node = node.next()
}

// Second callback: IdpCallback
if (callback is IdpCallback) {
    callback.idpSignIn(activity) { result ->
        when (result) {
            is Result.Success -> {
                logger.info("Apple sign-in successful")
                node = node.next()
            }
            is Result.Failure -> {
                logger.error("Apple sign-in failed", result.error)
            }
        }
    }
}
```
---
## Example: WebAuthn Registration

### Legacy
```kotlin
val callback = WebAuthRegistrationCallback()
callback.setResidentKeyRequirement(ResidentKeyRequirement.RESIDENT_KEY_DISCOURAGED)
callback.register(context, deviceName, node)
```

### Modern
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

### Legacy
```kotlin
val callback = WebAuthAuthenticationCallback()
callback.authenticate(context, deviceName, node)
```

### Modern
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

### Legacy
```kotlin
// Legacy SDK used device binding from separate module
import org.forgerock.android.auth.device.DeviceBindingCallback

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

### Modern
```kotlin
// Modern SDK with coroutine-based device binding
import com.pingidentity.device.binding.DeviceBindingCallback
import com.pingidentity.device.binding.Prompt

@Composable
fun DeviceBindingCallback(
    viewModel: DeviceBindingCallbackViewModel,
    onNext: () -> Unit
) {
    var deviceName by remember { mutableStateOf(Build.MODEL) }
    
    // PIN Dialog (shown when ViewModel requests it)
    viewModel.activePinPrompt?.let { prompt ->
        PinCollectorDialog(
            prompt = prompt,
            onPinEntered = { pin ->
                viewModel.submitPin(pin.toCharArray())
            }
        )
    }
    
    Column {
        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Device Name") }
        )
        Button(
            onClick = {
                viewModel.bind(deviceName = deviceName) { result ->
                    result.onSuccess { onNext() }
                    result.onFailure { it.printStackTrace() }
                }
            }
        ) {
            Text("Bind Device")
        }
    }
}
```

## Example: Device Profiling Callback

### Legacy
```kotlin
// Legacy SDK had limited device profiling capabilities
// Required manual collection of device information
import org.forgerock.android.auth.device.DeviceProfileCallback

val callback = DeviceProfileCallback()
// Manual implementation of device collectors
callback.collect(context)
```

### Modern
```kotlin
// Modern SDK with comprehensive device profiling
import com.pingidentity.device.profile.DeviceProfileCallback
import com.pingidentity.device.profile.DeviceProfileConfig
import com.pingidentity.device.profile.collector.*

@Composable
fun DeviceProfileCallback(
    deviceProfileCallback: DeviceProfileCallback,
    onNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = true) {
        scope.launch {
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
            isLoading = false
            onNext()
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Gathering Device Profile...")
            }
        }
    }
}
```

## Example: PingOne Protect Evaluation

### Legacy
```kotlin
// Legacy SDK had basic risk assessment
// Limited to simple threat detection
val callback = ProtectCallback()
callback.evaluate(context, object : FRListener<String> {
    override fun onSuccess(result: String) {
        logger.info("Threat evaluation complete")
        node.next(context, nodeListener)
    }
    override fun onException(e: Exception) {
        logger.error("Evaluation failed", e)
    }
})
```

### Modern
```kotlin
// Modern SDK with advanced PingOne Protect evaluation
import com.pingidentity.protect.journey.PingOneProtectEvaluationCallback

@Composable
fun PingOneProtectEvaluation(
    field: PingOneProtectEvaluationCallback,
    onNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember(field) { mutableStateOf(true) }

    LaunchedEffect(key1 = field) {
        scope.launch {
            try {
                val startTime = System.currentTimeMillis()
                field.collect() // Advanced threat assessment
                val taskDuration = System.currentTimeMillis() - startTime

                // Ensure minimum display time for user feedback
                val remainingTime = 2000 - taskDuration
                if (remainingTime > 0) {
                    delay(remainingTime)
                }
                isLoading = false
                onNext()
            } catch (e: Exception) {
                logger.error("Protect evaluation failed", e)
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Evaluating device risk...")
            }
        }
    }
}
```

---

## Example: Protecting API Requests (PingOne Protect)

### Legacy
```kotlin
// Legacy SDK required manual risk token inclusion
val riskToken = ProtectCollector.getInstance().getRiskToken()
val headers = mapOf("X-Risk-Token" to riskToken)
// Manual header management
makeApiRequest(url, headers)
```

### Modern
```kotlin
// Modern SDK automatically includes protect signals
import com.pingidentity.protect.Protect

// Automatic risk assessment and signal collection
Protect.start() // Initializes threat assessment

// Risk signals automatically included in authentication journey
val callback = PingOneProtectInitializeCallback()
callback.initialize()  // Collects behavioral data, device signals, etc.

// Automatic retry with adaptive authentication if needed
when (node) {
    is ContinueNode -> {
        // Protect signals already included
        node = node.next()
    }
    is ErrorNode -> {
        if (node.needsRiskEvaluation) {
            // Trigger additional evaluation if high risk detected
            Protect.evaluate()
        }
    }
}
```

---

## Example: ReCAPTCHA Enterprise

### Legacy
```kotlin
import com.google.android.recaptcha.Recaptcha
import org.forgerock.android.auth.callback.ReCaptchaEnterpriseCallback

if (callback is ReCaptchaEnterpriseCallback) {
    // Optional payload customization
    callback.setPayload(mapOf(
        "firewallPolicyEvaluation" to false
    ))
    
    // Optional custom error code
    callback.setClientError("custom_client_error")
    
    Recaptcha.getClient(context).executeAsync("login")
        .addOnSuccessListener { token ->
            logger.info("ReCAPTCHA verification successful")
            node.next(context, nodeListener)
        }
        .addOnFailureListener { error ->
            logger.error("ReCAPTCHA verification failed", error)
            node.next(context, nodeListener)
        }
}
```

### Modern
```kotlin
import com.pingidentity.recaptcha.enterprise.ReCaptchaEnterpriseCallback

@Composable
fun ReCaptchaEnterpriseCallback(
    reCaptchaEnterpriseCallback: ReCaptchaEnterpriseCallback,
    onNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = true) {
        scope.launch {
            reCaptchaEnterpriseCallback.verify {
                // Optionally customize the configuration here
                // config.payload = mapOf("custom_key" to "custom_value")
            }.onSuccess { result ->
                logger.info("ReCAPTCHA Token Result: $result")
                isLoading = false
                onNext()
            }.onFailure { error ->
                logger.error("ReCAPTCHA Verification Failed: ${error.message}", error)
                isLoading = false
                onNext() // Proceed to next step (or handle error differently)
            }
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "ReCAPTCHA verification in progress...")
            }
        }
    }
}
```

## Example: Resume Authentication Flow

### Legacy
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

### Modern
```kotlin
// In Activity or ViewModel handling deep links
viewModelScope.launch {
    val resumeUri = intent?.data // Contains 'suspendedId' parameter
    if (resumeUri != null) {
        try {
            // Resume authentication flow
            var node: Node = journey.resume(uri = resumeUri)
            
            while (node is ContinueNode) {
                // Process callbacks
                node = node.next()
            }
            
            when (node) {
                is SuccessNode -> {
                    logger.info("Authentication successful after resume")
                    navigateToHome()
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
}
```

## Example: Checking Current User Session

### Legacy
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

### Modern
```kotlin
// Check if user is authenticated
viewModelScope.launch {
    try {
        val user = journey.user()
        if (user != null) {
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
        } else {
            logger.info("No authenticated user")
            // Show login screen
            state.update { it.copy(requiresLogin = true) }
        }
    } catch (e: Exception) {
        logger.error("Session check failed", e)
    }
}
```

---

## Example: Centralized Login (Browser-based OIDC)

### Legacy
```kotlin
import org.forgerock.android.auth.FRUser

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
            navigateToHome()
        }
        override fun onException(e: Exception) {
            logger.error("Browser login failed", e)
            showError("Login failed")
        }
    })
```

### Modern
```kotlin
import com.pingidentity.oidc.module.Oidc
import com.pingidentity.oidc.OidcWeb

var oidcWeb = OidcWeb {
    logger = Logger.WARN
    module(Oidc) {
        clientId = oidcConfig.clientId
        discoveryEndpoint = oidcConfig.discoveryEndpoint
        scopes = mutableSetOf("openid", "email", "profile")
        redirectUri = oidcConfig.redirectUri
        signOutRedirectUri = oidcConfig.signOutRedirectUri
    }
    module(com.pingidentity.oidc.module.Web) {
        customTabsCustomizer = {
            setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
        }
    }
}

viewModelScope.launch {
    oidcWeb.authorize()
        .onSuccess { user ->
            logger.info("Browser login successful")
            state.update { it.copy(user = user, isLoggedIn = true) }
            navigateToHome()
        }
        .onFailure { error ->
            logger.error("Browser login failed", error)
            state.update { it.copy(error = error.message) }
        }
}
```

## Example: Centralized Logout (Browser-based OIDC)

### Legacy
```kotlin
import org.forgerock.android.auth.FRUser

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

### Modern
```kotlin
import com.pingidentity.oidc.OidcWeb

viewModelScope.launch {
    try {
        val oidcWeb = /* initialized OidcWeb instance */
        oidcWeb.user()?.logout()
        
        logger.info("Browser logout successful")
        state.update { it.copy(isLoggedIn = false, user = null) }
        navigateToLogin()
    } catch (e: Exception) {
        logger.error("Browser logout failed", e)
        // Still navigate to login even if logout fails
        navigateToLogin()
    }
}
```

## Example: Getting Access Token After Session

### Legacy
```kotlin
import org.forgerock.android.auth.FRUser

val currentUser = FRUser.getCurrentUser()
if (currentUser != null) {
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
                    navigateToLogin()
                }
            })
        }
    })
}
```

### Modern
```kotlin
import com.pingidentity.journey.user

viewModelScope.launch {
    try {
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
                            logger.error("Token refresh failed", result.error)
                            // Token refresh failed, require re-authentication
                            navigateToLogin()
                        }
                    }
                } else {
                    // Token is still valid
                    makeAuthenticatedRequest(token)
                }
            } else {
                logger.warn("No access token available")
                navigateToLogin()
            }
        } else {
            logger.warn("No authenticated user")
            navigateToLogin()
        }
    } catch (e: Exception) {
        logger.error("Error retrieving access token", e)
        navigateToLogin()
    }
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
