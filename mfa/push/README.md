<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Ping Identity Logo" width="200">
  </a>
  <hr/>
</p>

# Ping SDK - Push MFA Module

The Push module provides support for push notification-based multi-factor authentication. It supports both PingAM and PingOne push notification formats and enables applications to:

## Features

- Support for PingAM push notification formats and PingOne push notification formats (via custom PushHandler)
- Register devices for push notifications
- Device registration via QR code
- Secure storage of push credentials
- Process incoming push notifications
- Support for simple approval, challenge-response, and biometric authentication flows
- Kotlin Result-based API for improved error handling

## Getting Started

### Prerequisites

- Android API level 24 or higher
- Firebase Cloud Messaging (FCM) configured for your application
- Push notification service from Ping Identity (PingAM or PingAIC)

### Installation

Add the following dependency to your app module's `build.gradle` file:

```gradle
dependencies {
    implementation 'com.pingidentity.sdk:mfa:push:1.0.0'
    
    // Firebase dependencies for push notifications
    implementation 'com.google.firebase:firebase-messaging:23.1.0'
}
```

## Usage

### Initialize the Push Client

Before using the Push MFA functionality, you need to initialize the `PushClient`. There are several ways to create and initialize a PushClient:

#### Basic Initialization
```kotlin
// Create a client with default configuration (not initialized)
val pushClient = PushClient.create()

// Initialize the client
pushClient.initialize()
```

#### Initialize with Custom Configuration
```kotlin
// Create with custom configuration using DSL-style builder
val pushClient = PushClient {
    enableCredentialCache = true
    timeoutMs = 30000
    // Any other configuration options
}
```

The DSL-style initialization automatically calls `initialize()` for you.

### Register a Device

You can register a device for push notifications in several ways:

#### Using a QR Code URI (for PingAM)

```kotlin
// Parse a push QR code URI (supported by PingAM)
val uri = "pushauth://push/issuer:user@example.com?key=ABCDEFGHIJK&c=https://example.com/push"

// Using onSuccess/onFailure callbacks
pushClient.addCredentialFromUri(uri).onSuccess { credential ->
    // Handle the successfully created credential
    println("Created credential: ${credential.issuer}")
}.onFailure { exception ->
    // Handle error
    println("Failed to add credential: ${exception.message}")
}

// Alternative: Using getOrThrow()
try {
    val credential = pushClient.addCredentialFromUri(uri).getOrThrow()
    // Use credential
} catch (e: Exception) {
    // Handle exception
}
```

#### Manual Registration (for PingOne or custom implementation)

```kotlin
// Create a new push credential manually
val credential = PushCredential(
    issuer = "MyApp",
    accountName = "user@example.com",
    serverUrl = "https://your-server.com/push",
    sharedSecret = "some-secret"
    // Other parameters as needed
)

// Save the credential
pushClient.saveCredential(credential).onSuccess { savedCredential ->
    // Register device token for this credential
    firebaseMessaging.getToken().addOnSuccessListener { token ->
        pushClient.setDeviceToken(token, savedCredential.id)
    }
}.onFailure { error ->
    // Handle error
}
```

### Process a Push Notification

When you receive a push notification from Firebase Cloud Messaging, you need to process it using the Push SDK:

```kotlin
// When a push notification is received (e.g., from Firebase)
val messageData = remoteMessage.data
val notification = pushClient.processNotification(messageData)

// Show notification to user based on type
if (notification != null) {
    when (notification.pushType) {
        PushType.DEFAULT -> showDefaultNotification(notification)
        PushType.CHALLENGE -> showChallengeNotification(notification)
        PushType.BIOMETRIC -> showBiometricPrompt(notification)
    }
}
```

### Handling Different Types of Push Notifications

The Push module supports several types of authentication flows:

#### Default Push Notifications

Simple approve/deny without additional verification:

```kotlin
// To approve
pushClient.approveNotification(notificationId)
    .onSuccess { success ->
        if (success) {
            // Notify user of successful authentication
        } else {
            // Handle failure
        }
    }
    .onFailure { error ->
        // Handle error
    }

// To deny
pushClient.denyNotification(notificationId)
    .onSuccess { success ->
        if (success) {
            // Notify user of successful denial
        } else {
            // Handle failure
        }
    }
    .onFailure { error ->
        // Handle error
    }
```

#### Challenge-based Push Notifications

These require the user to verify a challenge (usually numbers):

```kotlin
// The user sees matching numbers on both login screen and mobile device
// They enter or select the challenge response
val challengeResponse = userSelectedResponse // e.g., "80"

pushClient.approveChallengeNotification(notificationId, challengeResponse)
    .onSuccess { success ->
        if (success) {
            // Notify user of successful authentication
        } else {
            // Handle failure (possibly wrong challenge response)
        }
    }
    .onFailure { error ->
        // Handle error
    }
```

#### Biometric Push Notifications

These require biometric authentication:

```kotlin
// After successful biometric authentication
val authMethod = "fingerprint" // or "face", "iris", etc.

pushClient.approveBiometricNotification(notificationId, authMethod)
    .onSuccess { success ->
        if (success) {
            // Notify user of successful authentication
        } else {
            // Handle failure
        }
    }
    .onFailure { error ->
        // Handle error
    }
```

### Managing Push Notifications

#### Getting Pending Notifications

You can retrieve pending notifications that have not been approved or denied:

```kotlin
// Get all pending notifications
pushClient.getPendingNotifications().onSuccess { notifications ->
    if (notifications.isNotEmpty()) {
        // Display pending notifications to the user
        displayPendingNotifications(notifications)
    } else {
        // No pending notifications
        showEmptyState()
    }
}.onFailure { error ->
    // Handle error
    showError("Failed to retrieve notifications: ${error.message}")
}

// Get a specific notification by ID
pushClient.getNotification(notificationId).onSuccess { notification ->
    if (notification != null) {
        // Display the notification details
        showNotificationDetails(notification)
    } else {
        // Notification not found
        showNotFoundMessage()
    }
}.onFailure { error ->
    // Handle error
    showError("Failed to retrieve notification: ${error.message}")
}
```

### Device Token Management

```kotlin
// Update the device token when it changes
val deviceToken = firebaseMessaging.getToken().await()
pushClient.setDeviceToken(deviceToken)

// Update the device token for a specific credential
pushClient.setDeviceToken(deviceToken, credentialId)
```

### Managing Notification Cleanup

The Push module provides automatic cleanup functionality for push notifications through the `NotificationCleanupConfig` class. This helps prevent your app from accumulating too many push notification records, which can improve performance and reduce storage usage.

#### Configuring Notification Cleanup

You can configure how notifications are cleaned up when initializing the Push client:

```kotlin
// Create a client with custom notification cleanup configuration
val pushClient = PushClient {
    // Configure notification cleanup
    notificationCleanupConfig = NotificationCleanupConfig {
        // Choose a cleanup mode: NONE, COUNT_BASED, AGE_BASED, or HYBRID
        cleanupMode = NotificationCleanupConfig.CleanupMode.HYBRID
        
        // Maximum notifications to keep when using COUNT_BASED or HYBRID mode
        maxStoredNotifications = 50
        
        // Maximum age in days for notifications when using AGE_BASED or HYBRID mode
        maxNotificationAgeDays = 14
    }
}
```

#### Cleanup Modes

The SDK supports different cleanup strategies:

- `CleanupMode.NONE` - No automatic cleanup is performed
- `CleanupMode.COUNT_BASED` - Keeps a maximum number of notifications (deleting oldest first)
- `CleanupMode.AGE_BASED` - Deletes notifications older than a certain age
- `CleanupMode.HYBRID` - Applies both count and age limits

#### Default Configuration

If not specified, the default configuration uses:
- Keeps a maximum number of notifications (`CleanupMode.COUNT_BASED`)
- Maximum of 100 stored notifications (if using `COUNT_BASED`)
- Maximum notification age of 30 days (if using `AGE_BASED`)

#### Manual Cleanup

You can also trigger notification cleanup manually:

```kotlin
// Clean up notifications for all credentials
pushClient.cleanupNotifications()
    .onSuccess { count ->
        println("Removed $count old notifications")
    }

// Clean up notifications for a specific credential
pushClient.cleanupNotifications(credentialId)
    .onSuccess { count ->
        println("Removed $count old notifications for credential $credentialId")
    }
```

### Clean Up

If you need to release resources used by the PushClient, you can call the `close()` method:

```kotlin
// Clean up resources when no longer needed
pushClient.close()
```

## Error Handling

The Push module uses Kotlin's Result API for error handling, providing a more functional approach:

```kotlin
// Using onSuccess/onFailure
pushClient.addCredentialFromUri(uri)
    .onSuccess { credential ->
        // Success path
    }
    .onFailure { exception ->
        when (exception) {
            is IllegalArgumentException -> showError() // Handle invalid URI format
            is MfaException -> showError() // Handle general MFA errors
            is NetworkException -> showError() // Handle network connectivity issues
            else -> showError() // Handle other exceptions
        }
    }

// Using fold for combined handling
pushClient.addCredentialFromUri(uri).fold(
    onSuccess = { credential -> 
        // Handle success
    },
    onFailure = { exception ->
        // Handle failure
    }
)

// Using runCatching for additional operations
runCatching { 
    pushClient.addCredentialFromUri(uri).getOrThrow()
}.onSuccess { credential ->
    // Do something with credential
}.onFailure { exception ->
    // Handle error
}
```

## Internal Storage

The Push module uses the MFA common storage infrastructure to securely store credentials and notifications. By default:

- Credentials, notifications, and device token are encrypted using the Android KeyStore system
- Data is persisted in a SQLite database
- Sensitive data is never stored in plain text

### Customizing Storage with SQLPushStorage

You can customize the storage behavior by creating a custom instance of SQLPushStorage and passing it to the PushClient:

```kotlin
// Create a custom storage instance with specific parameters
val customStorage = SQLPushStorage {
    context = applicationContext
    databaseName = "my_custom_push_db.db"
    passphraseProvider = NonePassphraseProvider()
}

// Create the client with the custom storage
val pushClient = PushClient {
    storage = customStorage
    enableCredentialCache = true
}
```

The custom storage options include:

- **context**: Android application context (required)
- **encryptionEnabled**: Whether to encrypt the database (default: true)
- **databaseName**: Custom database name for the SQLite database (optional)
- **databaseVersion**: Custom database version (default: 1)
- **passphraseProvider**: Custom passphrase provider for database encryption (default: uses Android KeyStore)

## Advanced Usage

### Custom Push Handler Implementation

If you need to support a custom push notification format, you can implement the `PushHandler` interface:

```kotlin
class CustomPushHandler : PushHandler {
    override fun canHandle(messageData: Map<String, String>): Boolean {
        // Check if this handler can process the given payload
        return payload.containsKey("custom_push_key")
    }
    
    override fun parseMessage(messageData: Map<String, Any>): Map<String, Any> {
        // Process the push payload and create a PushNotification
        val notificationId = payload["message_id"] ?: return null
        val issuer = payload["issuer"] ?: "Unknown"
        val message = payload["message"] ?: "Authentication request"
        
        // Populate and return a map associated with PushNotification fields
        return mapOf(
            "messageId" to notificationId,
            "message" to message,
            "issuer" to issuer,
            "pushType" to PushType.DEFAULT // or CHALLENGE, BIOMETRIC based on payload
        )
    }
    
    override fun sendApproval(
        credential: PushCredential,
        notification: PushNotification,
        params: Map<String, Any>
    ): Boolean {
        // Implement the approval logic
        return runCatching {
            // Make network request to approve the authentication
            // ...
            true
        }
    }
    
    override suspend fun sendDenial(
        credential: PushCredential,
        notification: PushNotification,
        params: Map<String, Any>
    ): Boolean {
        // Implement the denial logic
        return runCatching {
            // Make network request to deny the authentication
            // ...
            true
        }
    }

    override suspend fun setDeviceToken(
        credential: PushCredential,
        deviceToken: String,
        params: Map<String, Any>
    ): Boolean {
        // Implement the logic to set the device token for the credential
        return runCatching {
            // Make network request to register the device token
            // ...
            true
        }
    }
    
    override suspend fun register(
        credential: PushCredential,
        params: Map<String, Any>,
    ): Boolean {
        // Implement the registration logic
        return runCatching {
            // Make network request to register the credential
            // ...
            true
        }
    }
}

// Register your custom handler when initializing the PushClient
val pushClient = PushClient {
    customHandlers = listOf(CustomPushHandler())
}
```

### Custom Storage Implementation

You can implement a custom storage solution as an alternative to the default `SQLPushStorage` by implementing the `PushStorage` interface:

```kotlin
class MyCustomStorage : PushStorage {
    override suspend fun storePushCredential(credential: PushCredential) {
        // Implement storing the credential
    }
    
    override suspend fun retrievePushCredential(credentialId: String): PushCredential? {
        // Implement retrieving a credential by ID
        return null
    }
    
    override suspend fun removePushCredential(credentialId: String): Boolean {
        // Implement deleting a credential by ID
        return true
    }
    
    override suspend fun getAllPushCredentials(): List<PushCredential> {
        // Implement listing all stored credentials
        return emptyList()
    }
    
    override suspend fun storePushNotification(notification: PushNotification) {
        // Implement storing a notification
    }
    
    override suspend fun retrievePushNotification(notificationId: String): PushNotification? {
        // Implement retrieving a notification by ID
        return null
    }
    
    override suspend fun removePushNotification(notificationId: String): Boolean {
        // Implement deleting a notification by ID
        return true
    }
    
    // Implement other required methods...
}
```

## Extended Implementation Example

Following there are some comprehensive examples of integrating Push MFA into your application. 

Please note that these examples are simplified and may require additional error handling, UI updates, and other 
considerations based on your specific application requirements. For a more complete implementation, refer to the 
sample applications provided.

### Initializing the Push Client and Handling Push Notifications

```kotlin
class PushMainActivity : AppCompatActivity() {
    private lateinit var pushClient: PushClient
    private lateinit var credentialAdapter: CredentialAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_push_mfa)
        
        // Initialize Push client
        lifecycleScope.launch {
            try {
                pushClient = PushClient {
                    encryptionEnabled = true
                    enableCredentialCache = true
                }
                
                // Load all credentials
                loadCredentials()
            } catch (e: Exception) {
                showError("Failed to initialize: ${e.message}")
            }
        }
        
        // Set up QR code scanner button
        binding.btnScanQr.setOnClickListener {
            startQrCodeScan()
        }
        
        // Set up recycler view for credentials
        credentialAdapter = CredentialAdapter { credential ->
            // Show credential details
            showCredentialDetails(credential)
        }
        binding.recyclerViewCredentials.adapter = credentialAdapter
        
        // Set up pending notifications button
        binding.btnPendingNotifications.setOnClickListener {
            showPendingNotifications()
        }
    }
    
    private fun loadCredentials() {
        lifecycleScope.launch {
            pushClient.getCredentials().onSuccess { credentials ->
                if (credentials.isEmpty()) {
                    binding.emptyStateView.isVisible = true
                } else {
                    binding.emptyStateView.isVisible = false
                    credentialAdapter.submitList(credentials)
                }
            }.onFailure { error ->
                showError("Failed to load credentials: ${error.message}")
            }
        }
    }
    
    private fun startQrCodeScan() {
        // Launch QR code scanner (implementation depends on your QR code scanner library)
        qrCodeScanner.startScan { result ->
            if (result != null) {
                processQrCode(result)
            }
        }
    }
    
    private fun processQrCode(qrCode: String) {
        lifecycleScope.launch {
            pushClient.addCredentialFromUri(qrCode).onSuccess { credential ->
                showMessage("Credential added: ${credential.issuer}")
                
                // Register Firebase token
                firebaseMessaging.getToken().addOnSuccessListener { token ->
                    lifecycleScope.launch {
                        pushClient.setDeviceToken(token, credential.id)
                            .onSuccess {
                                loadCredentials() // Refresh list
                            }
                    }
                }
            }.onFailure { error ->
                showError("Failed to add credential: ${error.message}")
            }
        }
    }
    
    private fun showPendingNotifications() {
        lifecycleScope.launch {
            pushClient.getPendingNotifications().onSuccess { notifications ->
                if (notifications.isNotEmpty()) {
                    // Start activity to show pending notifications
                    val intent = Intent(this@PushMfaActivity, NotificationListActivity::class.java)
                    startActivity(intent)
                } else {
                    showMessage("No pending notifications")
                }
            }.onFailure { error ->
                showError("Failed to get notifications: ${error.message}")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            pushClient.close()
        }
    }
    
    // Helper methods
    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
    }
}
```

### Implement a FirebaseMessagingService to receive push notifications

```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val pushClient by lazy { PushClient.create() }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Check if this is a Push MFA notification
        val messageData = remoteMessage.data
        pushClient.processNotification(messageData).onSuccess { notification ->
            if (notification != null) {
                // This is a Push MFA notification that the SDK can handle
                when (notification.pushType) {
                    PushType.DEFAULT -> showDefaultNotification(notification)
                    PushType.CHALLENGE -> showChallengeNotification(notification)
                    PushType.BIOMETRIC -> showBiometricPrompt(notification)
                }
            } else {
                // Not a Push MFA notification, handle it as a regular notification
                handleRegularNotification(remoteMessage)
            }
        }.onFailure { error ->
            Log.e("PushService", "Failed to process notification", error)
            // Handle the error or process as a regular notification
            handleRegularNotification(remoteMessage)
        }
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        
        // Update all registered Push MFA credentials with the new token
        pushClient.setDeviceToken(token)
    }
    
    // Helper methods to display notifications to the user
    private fun showDefaultNotification(notification: PushNotification) {
        // Create and display a notification to the user
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Authentication Request")
            .setContentText("Tap to approve or deny this login attempt")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            
        // Create an intent to open your authentication activity
        val intent = Intent(this, AuthenticationActivity::class.java).apply {
            putExtra("notificationId", notification.id)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        notificationBuilder.setContentIntent(pendingIntent)
        
        // Show the notification
        with(NotificationManagerCompat.from(this)) {
            notify(notification.id.hashCode(), notificationBuilder.build())
        }
    }
    
    // Similar methods for challenge and biometric notifications
    // ...
}
```

### Biometric Authentication Activity

```kotlin
class BiometricAuthActivity : AppCompatActivity() {
    private lateinit var pushClient: PushClient
    private lateinit var notificationId: String
    private lateinit var biometricPrompt: BiometricPrompt
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_biometric_auth)
        
        pushClient = PushClient.create()
        notificationId = intent.getStringExtra("notificationId") ?: return
        
        setupBiometricAuthentication()
        
        // Get notification details and prompt for biometric auth
        lifecycleScope.launch {
            pushClient.getNotification(notificationId).onSuccess { notification ->
                if (notification != null && notification.pushType == PushType.BIOMETRIC) {
                    // Display notification details
                    binding.tvIssuer.text = notification.issuer
                    binding.tvMessage.text = notification.message
                    
                    // Show biometric prompt
                    showBiometricPrompt()
                }
            }
        }
    }
    
    private fun setupBiometricAuthentication() {
        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                
                // Determine authentication method from result
                val authMethod = when (result.authenticationType) {
                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_FINGERPRINT -> "fingerprint"
                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_FACE -> "face"
                    else -> "biometric"
                }
                
                // Approve the notification with the authentication method
                lifecycleScope.launch {
                    pushClient.approveBiometricNotification(notificationId, authMethod)
                        .onSuccess { success -> 
                            if (success) {
                                showMessage("Biometric authentication successful")
                                finish()
                            } else {
                                showError("Failed to authenticate")
                            }
                        }
                        .onFailure { error ->
                            showError("Error: ${error.message}")
                        }
                }
            }
            
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                showError("Authentication error: $errString")
                
                // Deny the notification or allow retry
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    lifecycleScope.launch {
                        pushClient.denyNotification(notificationId)
                    }
                    finish()
                }
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                showMessage("Authentication failed, please try again")
            }
        }
        
        biometricPrompt = BiometricPrompt(this, executor, callback)
    }
    
    private fun showBiometricPrompt() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authentication Required")
            .setSubtitle("Verify your identity to approve this login")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
            
        biometricPrompt.authenticate(promptInfo)
    }
}
```

### Challenge Authentication Activity

```kotlin
class ChallengeAuthActivity : AppCompatActivity() {
    private lateinit var pushClient: PushClient
    private lateinit var notificationId: String
    private var challengeOptions: List<String>? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_challenge_auth)
        
        pushClient = PushClient.create()
        notificationId = intent.getStringExtra("notificationId") ?: return
        
        // Get notification details
        lifecycleScope.launch {
            pushClient.getNotification(notificationId).onSuccess { notification ->
                if (notification != null && notification.pushType == PushType.CHALLENGE) {
                    // Display notification details
                    binding.tvChallenge.text = notification.challenge
                    
                    // Setup challenge options if available
                    challengeOptions = notification.challengeOptions
                    if (!challengeOptions.isNullOrEmpty()) {
                        setupChallengeOptions(challengeOptions!!)
                    }
                }
            }
        }
    }
    
    private fun setupChallengeOptions(options: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, options)
        binding.challengeList.adapter = adapter
        binding.challengeList.setOnItemClickListener { _, _, position, _ ->
            val selectedChallenge = options[position]
            submitChallengeResponse(selectedChallenge)
        }
    }
    
    private fun submitChallengeResponse(response: String) {
        lifecycleScope.launch {
            pushClient.approveChallengeNotification(notificationId, response)
                .onSuccess { success -> 
                    if (success) {
                        showMessage("Challenge verified successfully")
                        finish()
                    } else {
                        showError("Incorrect challenge response")
                    }
                }
                .onFailure { error ->
                    showError("Error: ${error.message}")
                }
        }
    }
}
```

### Default Authentication Activity

```kotlin
class AuthenticationActivity : AppCompatActivity() {
    private lateinit var pushClient: PushClient
    private lateinit var notificationId: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authentication)
        
        pushClient = PushClient.create()
        notificationId = intent.getStringExtra("notificationId") ?: return
        
        // Get notification details
        lifecycleScope.launch {
            pushClient.getNotification(notificationId).onSuccess { notification ->
                if (notification != null) {
                    // Display notification details
                    binding.tvIssuer.text = notification.issuer
                    binding.tvMessage.text = notification.message
                    binding.tvTimestamp.text = DateFormat.getDateTimeInstance().format(notification.timestamp)
                }
            }
        }
        
        // Set up approve button
        binding.btnApprove.setOnClickListener {
            lifecycleScope.launch {
                pushClient.approveNotification(notificationId)
                    .onSuccess { success -> 
                        if (success) {
                            showMessage("Authentication successful")
                            finish()
                        } else {
                            showError("Failed to authenticate")
                        }
                    }
                    .onFailure { error ->
                        showError("Error: ${error.message}")
                    }
            }
        }
        
        // Set up deny button
        binding.btnDeny.setOnClickListener {
            lifecycleScope.launch {
                pushClient.denyNotification(notificationId)
                    .onSuccess { success -> 
                        if (success) {
                            showMessage("Authentication denied")
                            finish()
                        } else {
                            showError("Failed to deny authentication")
                        }
                    }
                    .onFailure { error ->
                        showError("Error: ${error.message}")
                    }
            }
        }
    }
}
```


## License

Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.