[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Device Root Detection Module

The Device Root Detection Module provides comprehensive utilities for analyzing Android device tampering, including root detection, system integrity verification, and security compromise identification. It employs multiple detection strategies to provide reliable assessment of device security status.

## Features

- **Comprehensive Tamper Detection**: Multi-layered approach using various detection mechanisms
- **Scoring System**: Returns confidence scores (0.0 to 1.0) instead of binary results
- **Multiple Detection Categories**:
  - **Build & System Property Analysis**
  - **File System Analysis** 
  - **Application Analysis**
  - **System Security Analysis**
- **Asynchronous API**: Coroutine-based analysis for non-blocking operations
- **Extensible Architecture**: Easy to add custom detectors
- **DSL Configuration**: Fluent API for configuring detection strategies
- **Production Ready**: Thread-safe with comprehensive error handling

## Installation

Add the module to your project using Gradle:

```gradle
implementation("com.pingidentity.sdks:device-root:<latest_version>")
```

## Quick Start

### Basic Usage

Use the `analyze()` function with default detectors:

```kotlin
import com.pingidentity.device.analyze

// Check device tampering with default detectors
val tamperScore = analyze()

// Interpret results
when {
    tamperScore >= 0.8 -> println("High confidence: Device is tampered")
    tamperScore >= 0.4 -> println("Medium confidence: Potential tampering")
    else -> println("Low confidence: Device appears secure")
}
```

### Custom Detector Configuration

Configure specific detectors using the DSL:

```kotlin
val tamperScore = analyze {
    detector {
        // Add specific detectors
        add(RootAppDetector)
        add(BuildTagsDetector())
        add(PermissionDetector())
        
        // Add custom detector
        add(TamperDetector { 
            // Custom detection logic
            if (customSecurityCheck()) 1.0 else 0.0
        })
    }
}
```

## Available Detectors

### Build & System Property Analysis
- **`BuildTagsDetector`** - Checks for test-keys in build tags indicating unofficial firmware
- **`DangerousPropertyDetector`** - Examines system properties for insecure configurations (ro.debuggable=1, ro.secure=0)

### File System Analysis
- **`BusyBoxProgramFileDetector`** - Detects BusyBox Unix utilities package
- **`RootProgramFileDetector`** - Searches for core root binaries (su, magisk)
- **`RootApkDetector`** - Looks for root management APK files in system directories
- **`NativeDetector`** - Uses JNI-based low-level file detection

### Application Analysis
- **`RootAppDetector`** - Identifies installed root management applications
- **`RootRequiredAppDetector`** - Detects apps that require root access to function
- **`RootCloakingAppDetector`** - Finds applications designed to hide root access

### System Security Analysis
- **`PermissionDetector`** - Examines filesystem mount permissions for write access
- **`SuCommandDetector`** - Checks for superuser command availability in PATH

## Advanced Usage

### Selective Detection

Choose only specific detection categories:

```kotlin
// Only check for root applications
val appBasedScore = analyze {
    detector {
        add(RootAppDetector)
        add(RootRequiredAppDetector)
        add(RootCloakingAppDetector)
    }
}

// Only check file system indicators
val fileBasedScore = analyze {
    detector {
        add(RootProgramFileDetector)
        add(BusyBoxProgramFileDetector)
        add(RootApkDetector)
    }
}
```

### Custom Logging

Configure custom logging behavior:

```kotlin
val tamperScore = analyze {
    logger = MyCustomLogger()
    detector {
        add(BuildTagsDetector())
        // ... other detectors
    }
}
```

## Understanding Scores

The detection system returns confidence scores as Double values:

| Score Range | Interpretation | Recommended Action |
|-------------|----------------|-------------------|
| `1.0` | High confidence tampering detected | Block/restrict functionality |
| `0.8 - 0.99` | Very likely tampered | Strong security measures |
| `0.6 - 0.79` | Probable tampering | Enhanced monitoring |
| `0.4 - 0.59` | Possible tampering | Caution advised |
| `0.1 - 0.39` | Low suspicion | Normal operation |
| `0.0` | No tampering detected | Device appears secure |

## Custom Detector Implementation

Create custom detectors by implementing the `TamperDetector` interface:

```kotlin
class CustomEmulatorDetector : TamperDetector {
    override suspend fun analyze(context: Context): Double {
        // Check for emulator indicators
        val isEmulator = Build.FINGERPRINT.contains("generic") ||
                        Build.MODEL.contains("Emulator")
        
        return if (isEmulator) 0.7 else 0.0
    }
}

// Use custom detector
val score = analyze {
    detector {
        add(CustomEmulatorDetector())
    }
}
```

### Extending Existing Detectors

Extend abstract base classes for specific detection types:

```kotlin
class CustomPackageDetector : PackageDetector() {
    override fun getPackages(): List<String> {
        return listOf(
            "com.example.suspicious.app",
            "com.malware.package"
        )
    }
}

class CustomFileDetector : FileDetector() {
    override fun getFilenames(): List<String> {
        return listOf("suspicious_binary", "rootkit_file")
    }
}
```

## Best Practices

### Security Considerations
- **Multi-layered Detection**: Use multiple detector types for comprehensive coverage
- **Score Thresholds**: Set appropriate thresholds based on your security requirements
- **Regular Updates**: Keep detection logic updated to counter new tampering methods
- **Graceful Degradation**: Handle detection failures gracefully without breaking app functionality

### Performance Optimization
- **Selective Detection**: Only use detectors relevant to your threat model
- **Caching**: Cache results for repeated checks within the same session
- **Background Analysis**: Perform detection on background threads

### Error Handling
```kotlin
try {
    val tamperScore = analyze()
    // Handle score
} catch (e: SecurityException) {
    // Handle security-related errors
    logger.w("Security check failed", e)
} catch (e: Exception) {
    // Handle other errors gracefully
    logger.e("Tamper detection failed", e)
}
```

## Integration Examples

### Conditional Feature Access
```kotlin
suspend fun checkSecurityAccess(): Boolean {
    val tamperScore = analyze()
    return when {
        tamperScore >= 0.8 -> {
            // High risk - block access
            false
        }
        tamperScore >= 0.4 -> {
            // Medium risk - require additional authentication
            requireAdditionalAuth()
        }
        else -> {
            // Low risk - allow access
            true
        }
    }
}
```

### Telemetry and Monitoring
```kotlin
suspend fun reportDeviceStatus() {
    val tamperScore = analyze()
    
    analytics.track("device_security_check") {
        put("tamper_score", tamperScore)
        put("device_model", Build.MODEL)
        put("timestamp", System.currentTimeMillis())
    }
}
```


© Copyright 2025-2026 Ping Identity Corporation. All Rights Reserved
