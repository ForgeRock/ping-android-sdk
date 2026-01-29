[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/pingidentity/pingone-mobile-sdk-android)

# PingOne MFA Authenticator Sample App

This sample application demonstrates how to implement multi-factor authentication using the Ping Identity SDK. The app allows users to register and manage both OATH credentials (TOTP/HOTP) and Push authentication credentials.

## Disclaimer

This application is a sample and not intended for production use. It is provided for educational purposes to demonstrate the use of the Ping Identity SDK.

## Features

### OATH Authentication
- **QR Code Scanning**: Register accounts by scanning QR codes 
- **TOTP Support**: Automatic generation of time-based one-time passwords with countdown timer

### Push Authentication
- **QR Code Registration**: Register for push authentication by scanning QR codes
- **Push Notifications**: Receive and respond to authentication requests
- **System Notifications**: Display system notifications when push requests are received
- **Direct Actions**: Approve or deny authentication requests directly from system notification tray (DEFAULT type)
- **Push Biometric Authentication**: Authenticate using fingerprint or face recognition (BIOMETRIC type)
- **Push Challenge Verification**: Verify challenge numbers for enhanced security (CHALLENGE type)

## Architecture overview

The Ping Authenticator App sample is a modular Android application built on Model-View-ViewModel architecture with Kotlin, Jetpack Compose, and the Ping SDK for secure multi-factor authentication (MFA).

```
┌─────────────────────────────┐
│      Presentation Layer     │  ← UI: Jetpack Compose screens, navigation
├─────────────────────────────┤
│        Domain Layer         │  ← ViewModels, business logic, state
├─────────────────────────────┤
│     Data/Service Layer      │  ← Managers, services, secure storage
├─────────────────────────────┤
│         SDK Layer           │  ← Ping SDK: push, oath, and journey modules
└─────────────────────────────┘
```

- **Presentation Layer**: Android Activities/Fragments for user interaction.
- **Domain Layer**: Handles business logic, orchestrates feature flows, and manages state.
- **Data/Service Layer**: Integrates with Ping SDK modules (`push`, `otp`).
- **SDK Layer**: Abstracts the complexity to deal with MFA capabilities and communication with Ping backend.

The application follows modern Android development practices:

- **Kotlin**: 100% Kotlin codebase
- **Jetpack Compose**: Declarative UI toolkit for building native UI
- **ViewModel**: Architecture component for managing UI-related data in a lifecycle conscious way
- **Coroutines**: For asynchronous operations
- **Navigation**: For handling navigation between screens
- **Material 3**: For modern, adaptive UI components
- **Firebase Cloud Messaging**: For receiving push notifications
- **Biometric**: For fingerprint/face recognition

## Implementation Details

### Code Structure Overview

```
src/main/kotlin/com/pingidentity/authenticatorapp/
├── PingOneMFApp.kt                 # App initialization
├── managers/
│   ├── AccountsManager.kt          # Pairing account and accounts retrieval from the SDK
│   └── OtpManager.kt               # OTP generation and auto-refresh
├── ui/
│   ├── AccountsScreen.kt           # Account management UI
│   ├── OtpScreen.kt                # OTP presentation screen
│   └── ...                         # Other Compose screens
├── data/
│   ├── MainViewModel.kt            # Acts as the central coordinator between the PingOne MFA SDK managers and the UI
│   ├── DiagnosticLogger.kt         # Logging
│   └── UserPreferences.kt          # Preferences
└── ...
```

**Key Classes & Structure:**

- `PingOneMFApp.kt`: Configures logging, initializes the PingOne MFA SDK, and registers the Firebase push token.
- `managers/`: Integrates Ping SDK modules.
- `managers/AccountsManager.kt`: wraps the PingOne MFA SDK to pair users and load MFA accounts.
- `managers/OtpManager.kt`: continuously fetches OTP codes from the PingOne MFA SDK, maintains their countdown lifecycle, and exposes the current OTP state to the UI via a reactive flow.
- `ui/`: Compose screens and components for account and notification management.
- `data/`: Models, preferences, logging.

### Push Module
- **Device Registration**: Registers device with Ping backend for push authentication.
- **Notification Handling**: Listens for push requests, displays actionable notifications.
- **User Actions**: Approve/deny requests from notification or app UI.
- **Result Reporting**: Communicates user decisions to Ping backend securely.

**Class:** `PushNotificationService.kt`

**Flow Diagram (textual):**
```
Push Request → PushNotificationService → SDK module → Notification UI → User Action → Ping Backend
```

#### Push Authentication Types

The app handles three different types of push authentication:

1. **DEFAULT**: Simple approval/denial directly from the notification
   ```kotlin
   // Approve a standard notification
   notification.approveNotification(notification, authMethod)
   ```
    ***important:***
If you're approving the notification from the notification banner button (notification action) you must call:
   ```kotlin
   // Approve a background notification
   PingOneMFA.approvePushNotificationFromBanner(notification)
   ```

2. **BIOMETRIC**: Authentication using biometric verification
   ```kotlin
   // Approve with biometric authentication
   notification.approveBiometricNotification(notification, authMethod)
   ```

3. **CHALLENGE**: Verification using challenge numbers
   ```kotlin
   // Get challenge numbers
   val numbers = pushNotification.getNumbersChallenge()
   
   // Approve with challenge response
   notification.approveNotification(notification, authMethod, challengeResponse)
   ```


### OTP Module
- **Token Retrieval**: Retrieves OTP from the SDK and displays it to the user.
- **Token Refreshment**: Refreshes the OTP code when it expires.

**Class:** `OtpManager.kt`

**Flow Diagram (textual):**
```
Enroll User → OtpManager → Ping One SDK → Token Retrieval → Display in UI → User enters code
```

### QR Code Scanning

The app uses CameraX and ML Kit to scan and decode QR codes.


## Getting Started

### Prerequisites

- Android Studio Koala | 2024.1.1 or newer
- Android SDK 29 or higher
- Gradle 8.7 or newer
- google-services.json file (to work with FCM push notifications)

### Building the App

1. Clone the repository
2. Open the project in Android Studio
3. Build and run on your device or emulator

## Testing

### Testing Functionality

To test the app's functionality, you need:

- A PingOne account with MFA enabled
- FCM configured for your Android application
- The app properly registered with FCM to receive push notifications


## Contributing

Contributions are welcome! Please read the [contributing guidelines](../../CONTRIBUTING.md) for more information.

## Troubleshooting

- **Push notifications are not being received**: 
  - Ensure that your device has a valid internet connection.
  - Verify that the device token is correctly registered with the push notification service.
  - Check the server logs to see if the push notification is being sent successfully.
- **QR code is not scanning**:
  - Make sure that the QR code is well-lit and in focus.
  - Try scanning the QR code from a different distance or angle.
  - Ensure that the QR code is in the correct format.

## License

Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
This software may be modified and distributed under the terms of the MIT license. See the [LICENSE](../LICENSE) file for details.