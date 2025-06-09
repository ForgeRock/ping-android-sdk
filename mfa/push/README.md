<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Ping Identity Logo" width="200">
  </a>
  <hr/>
</p>

# Ping SDK - Push MFA Module

The Push MFA module provides functionality for implementing push notification-based multi-factor authentication in Android applications. This module enables applications to register for push credentials, process incoming push notifications, and respond to authentication requests.

## Features

- Push credential management (register, update, delete)
- Push notification processing
- Authentication approval and denial
- FCM device token management

## Getting Started

### Prerequisites

- Android API level 24 or higher
- Firebase Cloud Messaging (FCM) configured in your application
- Firebase Messaging dependency in your project

### Installation

Add the following dependency to your app module's `build.gradle` file:

```gradle
dependencies {
    implementation 'com.pingidentity.sdk:mfa-push:1.0.0'
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
    timeoutMs = 60000
    encryptionEnabled = false
    // Any other configuration options
}
```

The DSL-style initialization automatically calls `initialize()` for you.

### Register a Push Credential

Register a new Push credential:

```kotlin
val uri = "pushauth://push/Example:user?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E"
val credential = pushClient.addCredentialFromUri(uri)
```

### Automatic Token Synchronization

When you register a new PushCredential, the current FCM token will be automatically associated with that credential. You don't need to call `updateDeviceToken` separately after registering a credential.

### Update FCM Device Token

When your application receives a new FCM token, you should update it in the SDK:

```kotlin
// In your FirebaseMessagingService implementation
override fun onNewToken(token: String) {
    super.onNewToken(token)
    
    // Create and initialize a PushClient
    val pushClient = PushClient.create()
    pushClient.initialize()
    
    // Update the token in the SDK
    val updated = pushClient.updateDeviceToken(token)
    
    Log.d(TAG, "FCM token updated: $updated")
}
```

### Process Push Notifications

When your application receives a push notification from FCM, process it:

```kotlin
// In your FirebaseMessagingService implementation
override fun onMessageReceived(remoteMessage: RemoteMessage) {
    val pushClient = PushClient.create()
    pushClient.initialize()
    
    // Process the push notification
    val notification = pushClient.processPushMessage(remoteMessage.data)
    
    if (notification != null) {
        // Show notification to the user
        showAuthenticationNotification(notification)
    }
}
```

### Approve or Deny Authentication Requests

```kotlin
// Approve a notification
lifecycleScope.launch {
    val approved = pushClient.approveNotification(notification)
    if (approved) {
        // Show Success
    } else {
        // Show Error
    }
}
```

```kotlin
// Deny a notification
lifecycleScope.launch {
    val denied = pushClient.denyNotification(notification)
    if (denied) {
        // Show Denied Confirmation
    } else {
        // Show Error
    }
}
```

### Example: Extended FCM Implementation Example

```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val TAG = "MyFirebaseMessagingService"
    private var pushClient: PushClient? = null
    
    override fun onCreate() {
        super.onCreate()
        
        // Create and initialize client
        val pushClient = PushClient.create()
        pushClient.initialize()
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        
        // Update the token in a background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update token
                val updated = pushClient.updateDeviceToken(token)
                Log.d(TAG, "FCM token updated: $updated")
                
                // Clean up
                pushClient.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token", e)
            }
        }
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        if (remoteMessage.data.isNotEmpty()) {
            // Process in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val notification = pushClient.processPushMessage(remoteMessage.data)
                    if (notification != null) {
                        // Switch to main thread to show UI
                        withContext(Dispatchers.Main) {
                            showAuthenticationNotification(notification)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process push message", e)
                }
            }
        }
    }
}
```

## License

Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.
