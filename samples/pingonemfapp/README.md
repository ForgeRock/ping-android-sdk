[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/pingidentity/pingone-mobile-sdk-android)

# PingOne MFA Authenticator Sample App

This sample application demonstrates how to implement multi-factor authentication using the Ping Identity SDK. The app allows users to register and manage both OATH credentials (TOTP/HOTP) and Push authentication credentials.
It also includes sample DaVinci flows that can collect a PingOne MFA mobile payload or pair a device through DaVinci.

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

### DaVinci Integration
- **DaVinci Launcher**: Choose a configured DaVinci flow from the app drawer
- **Editable Configurations**: Edit client ID, discovery endpoint, scopes, redirect URI, display name, timeout, and storage file name in the sample app
- **Mobile Payload Collection**: Collect a PingOne MFA mobile payload and submit it through a DaVinci collector
- **Device Pairing Flow**: Pair a device through a DaVinci flow using the PingOne MFA SDK

## Architecture overview

The PingOne MFA Authenticator sample is a modular Android application built on Model-View-ViewModel architecture with Kotlin, Jetpack Compose, and the Ping SDK for secure multi-factor authentication (MFA).

```
┌─────────────────────────────┐
│      Presentation Layer     │  ← UI: Jetpack Compose screens and navigation
├─────────────────────────────┤
│        Domain Layer         │  ← ViewModels, business logic, state
├─────────────────────────────┤
│     Data/Service Layer      │  ← Managers, services, secure storage
├─────────────────────────────┤
│         SDK Layer           │  ← Ping SDK: push, oath, and DaVinci modules
└─────────────────────────────┘
```

- **Presentation Layer**: Jetpack Compose screens and navigation for user interaction.
- **Domain Layer**: Handles business logic, orchestrates feature flows, and manages state.
- **Data/Service Layer**: Integrates with Ping SDK modules, Firebase Cloud Messaging, and local preferences.
- **SDK Layer**: Abstracts MFA, DaVinci, and communication with Ping backend services.

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
src/main/kotlin/com/pingidentity/pingonemfapp/
├── PingOneMFApp.kt                 # App initialization
├── config/
│   ├── Env.kt                      # Editable DaVinci configuration UI
│   └── EnvViewModel.kt             # DaVinci configuration state and persistence
├── davinci/
│   ├── DaVinci.kt                  # DaVinci flow UI
│   ├── DaVinciViewModel.kt         # DaVinci flow state and node progression
│   └── collector/                  # PingOne MFA-specific DaVinci collectors
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
- `config/EnvViewModel.kt`: Stores editable DaVinci configurations as JSON in SharedPreferences and applies the selected configuration before a DaVinci flow starts.
- `davinci/`: Compose UI and ViewModels for running DaVinci flows from the selected configuration.
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
    ***Important:***
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
Enroll User → OtpManager → PingOne MFA SDK → Token Retrieval → Display in UI → User enters code
```

### QR Code Scanning

The app uses CameraX and ML Kit to scan and decode QR codes.

### DaVinci Module

The sample includes two editable DaVinci configurations:

1. **DaVinci Payload Flow Config**: launches a flow that collects a PingOne MFA mobile payload.
2. **DaVinci Pairing Flow Config**: launches a flow that pairs a device through PingOne MFA.

DaVinci is initialized only when the user selects a configuration from the DaVinci launcher. The selected configuration is then applied and persisted before the flow starts. Configuration edits are stored as JSON in SharedPreferences.


## Getting Started

### Prerequisites

- Android Studio Koala | 2024.1.1 or newer
- Android SDK 29 or higher
- Gradle 8.7 or newer
- A `google-services.json` file for the `com.pingidentity.pingonemfapp` application ID if you want to test FCM push notifications
- A PingOne environment with MFA configured
- DaVinci applications/flows configured with redirect URI `app://oauth2redirect` if you want to test the DaVinci launcher

### Building the App

1. Clone the repository
2. Add or replace `samples/pingonemfapp/google-services.json` with the Firebase configuration for your app
3. Open the project in Android Studio
4. Build and run on your device or emulator

You can also build from the command line:

```bash
./gradlew :samples:pingonemfapp:assembleDebug
```

### Running DaVinci Flows

1. Open the app drawer and choose **Launch DaVinci**.
2. Tap a configuration to apply it and start that flow.
3. To edit the defaults, choose **Edit Configurations**, update the fields, and tap **Apply**.

The sample ships with default configuration values for demonstration. Replace the client IDs, discovery endpoint, scopes, redirect URI, display name, timeout, and storage file name with values that match your PingOne/DaVinci environment.

## Testing

### Testing Functionality

To test the app's functionality, you need:

- A PingOne account with MFA enabled
- FCM configured for your Android application
- The app properly registered with FCM to receive push notifications
- DaVinci flows configured for the payload and/or pairing scenarios if testing the DaVinci launcher


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
- **DaVinci flow does not start**:
  - Confirm that you selected a configuration from **Launch DaVinci** before navigating to the flow.
  - Verify the client ID, discovery endpoint, scopes, and redirect URI in **Edit Configurations**.
  - Ensure the redirect URI configured in DaVinci matches `app://oauth2redirect`.

## License

Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
This software may be modified and distributed under the terms of the MIT license. See the [LICENSE](../../LICENSE) file for details.
