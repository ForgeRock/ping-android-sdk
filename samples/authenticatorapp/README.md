# Ping Identity Authenticator Sample App

This sample application demonstrates how to implement OATH-based multi-factor authentication using the Ping Identity OathClient API. The app allows users to register and manage TOTP (Time-based One-Time Password) and HOTP (HMAC-based One-Time Password) accounts.

## Features

- **QR Code Scanning**: Register accounts by scanning QR codes containing OATH credentials
- **Manual Entry**: Manually enter account details
- **TOTP Support**: Automatic generation of time-based one-time passwords with countdown timer
- **HOTP Support**: Counter-based one-time passwords with refresh capability
- **Account Management**: View, organize, and delete accounts
- **Copy OTP**: Copy to clipboard functionality

## Architecture

The application follows modern Android development practices:

- **Kotlin**: 100% Kotlin codebase
- **Jetpack Compose**: Declarative UI toolkit for building native UI
- **ViewModel**: Architecture component for managing UI-related data in a lifecycle conscious way
- **Coroutines**: For asynchronous operations
- **Navigation**: For handling navigation between screens
- **Material 3**: For modern, adaptive UI components

## Implementation Details

### OathClient Usage

This application demonstrates the use of `OathClient` from the Ping Identity MFA OATH SDK:

```kotlin
// Initialize the OATH client
val oathClient = OathClient {
    enableCredentialCache = true
}

// Add an account from a QR code (otpauth URI)
val credential = oathClient.addCredentialFromUri(uri)

// Generate a TOTP/HOTP code
val codeInfo = oathClient.generateCodeWithValidity(credentialId)

// Access code and validity information
val code = codeInfo.code
val startTime = codeInfo.startTime
val endTime = codeInfo.endTime
```

### QR Code Scanning

The app uses CameraX and ML Kit to scan and decode QR codes containing otpauth:// URIs:

```
otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&algorithm=SHA1&digits=6&period=30
```

## Getting Started

### Prerequisites

- Android Studio Arctic Fox or newer
- Android SDK 24 or higher
- Gradle 7.0+

### Building the App

1. Clone the repository
2. Open the project in Android Studio
3. Build and run on your device or emulator

## Testing

To test the app's OATH functionality, you can:

1. Use any TOTP/HOTP QR code generator
2. Create test credentials using command line tools like `oathtool`
3. Use online TOTP testing services

## License

See the LICENSE file for details.
