
[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Device Root Module
The Device Root Module provides utilities for determining the root status of an Android device.
It helps in identifying whether a device is rooted, which is crucial for maintaining security
and integrity in mobile applications.

## Features
- **Root Detection**: Check if the device is rooted using various strategies.
- **Multiple Detection Methods**:
    - `BasicRootDetection`: Simple checks for common root indicators.
    - `AdvancedRootDetection`: More comprehensive checks for root status.
- **Thread-Safe**: All implementations are thread-safe with lazy initialization patterns.
- **Custom Implementation**: Define your own root detection strategy by implementing the
  `TamperDetector` interface.
- **Simple API**: A consistent and easy-to-use interface for checking root status.


## Installation

Add the module to your project using Gradle:

```gradle
implementation("com.pingidentity.sdks:device-root:<latest_version>") 
```

## Usage

### Retrieving Tamper Detectors

The module provides built-in implementations for checking if a device is rooted.

#### Basic Root Detection
Use the `TamperDetector` to perform simple checks for common root indicators:

```kotlin
// Check if the device is rooted using basic detection
val isRooted = TamperDetector.isTampered()
```

The `DefaultTamperDetector` performs checks such as:

**Build & System Property Analysis:**
- [BuildTagsDetector] - Checks for test-keys in build tags indicating unofficial firmware
- [DangerousPropertyDetector] - Examines system properties for insecure configurations

**File System Analysis:**
- [BusyBoxProgramFileDetector] - Detects BusyBox Unix utilities package
- [RootProgramFileDetector] - Searches for core root binaries (su, magisk)
- [RootApkDetector] - Looks for root management APK files in system directories
- [NativeDetector] - Uses JNI-based low-level file detection

**Application Analysis:**
- [RootAppDetector] - Identifies installed root management applications
- [RootRequiredAppDetector] - Detects apps that require root access to function
- [RootCloakingAppDetector] - Finds applications designed to hide root access

**System Security Analysis:**
- [PermissionDetector] - Examines filesystem mount permissions for write access
- [SuCommandDetector] - Checks for superuser command availability in PATH
- [TestKeysDetector] - Looks for test-keys in system partitions

### Best Practices
- Combine multiple detectors for a more reliable root status check.
- Regularly update the detection logic to adapt to new rooting methods.
- Use root detection as part of a broader security strategy for your application.

## Custom Implementation
You can create your own root detection strategy by implementing the `TamperDetector` interface:

```kotlin
class CustomRootDetector : TamperDetector {
    override fun isTampered(): Double {
        // Custom root detection logic
        return 0.0 // Return a confidence score between 0.0 and 1.0
    }
}
```



