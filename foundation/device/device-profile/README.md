[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Device Profile Module

> **A flexible, extensible, and privacy-conscious framework for collecting device information in Android applications.**

The Device Profile module provides a structured framework for collecting device information in
Android applications. It uses a modular collector system that makes it easy to gather, extend, and
customize the device data you need.

---

## ✨ Features

- **🔧 Modular Architecture**: Plug-and-play collector system for maximum flexibility
- **⚡ Asynchronous Collection**: All operations are suspend functions for smooth UI performance
- **🔗 AIC Journey Integration**: Built-in support for PingOne AIC Device Profile workflows
- **📦 Serializable Output**: JSON-ready data structures for easy network transmission
- **🔌 Extensible Framework**: Create custom collectors for any device signals you need
- **🔒 Permission-Aware**: Handles Android permissions gracefully

---

## 📋 Table of Contents

- [Overview](#overview)
- [Getting Started](#getting-started)
- [Built-in Collectors](#-built-in-collectors)
- [Customization](#customization)
- [AIC Journey Integration](#-aic-journey-integration)
- [API Reference](#-api-reference)

---

## Overview

This module helps you collect various device attributes through dedicated collectors:

- **Hardware information**: Camera capabilities, display properties, sensors, memory, storage
- **Platform details**: OS version, manufacturer, model, SDK version, build information
- **Network information**: Connection type, carrier details, network capabilities
- **Custom collectors**: Extend with your own logic for any device data

### Architecture

```
┌─────────────────────────────────────────┐
│            Application Layer            │
├─────────────────────────────────────────┤
│         Device Profile Module           │
│  ┌─────────────┐  ┌─────────────────┐   │
│  │ Collectors  │  │ AIC Integration │   │
│  └─────────────┘  └─────────────────┘   │
├─────────────────────────────────────────┤
│          Android Platform API           │
└─────────────────────────────────────────┘
```
---

## Getting Started

### Permissions

The module respects Android's permission model. Some collectors may require specific permissions:

```xml
<!-- Optional: For enhanced hardware detection -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />

<!-- Note: Permissions are only used if the corresponding collectors are enabled -->
```

### Basic Usage

1. **Add the module dependency** to your project:

```kotlin
dependencies {
    implementation("com.pingidentity.device:device-profile:x.y.z")
}
```

2. **Create collectors** and collect device information:

```kotlin
import com.pingidentity.device.profile.collector.DefaultDeviceCollector
import com.pingidentity.device.profile.collector.collect

suspend fun collectDeviceProfile() {
    // Initialize collectors with default set
    val collectors = mutableListOf<DeviceCollector<*>>().apply(DefaultDeviceCollector())

    // Collect device information
    val deviceProfile = collectors.collect()

    // Use the collected profile (JsonObject)
    println(deviceProfile.toString())
}
```

### Example Output

```json
{
  "camera": {
    "noOfCameras": 2
  },
  "platform": {
    "osVersion": "13",
    "manufacturer": "Google",
    "model": "Pixel 7",
    "sdkVersion": 33
  }
}
```

---

## 🔧 Built-in Collectors

The module provides several built-in collectors out of the box:

### CameraCollector
Collects camera-related information:
```json
{
  "camera": {
    "noOfCameras": 2
  }
}
```

### PlatformCollector
Gathers platform and OS information:
```json
{
   "platform": {
      "platform": "Android",
      "version": "13",
      "deviceName": "Google",
      "brand": "Google",
      "model": "Pixel 7",
      "sdkVersion": 33,
      "timeZone": "America/Vancouver",
      "locale": "en_US",
      "jailBreakScore": 1
   }
}
```

### HardwareCollector
Collects hardware specifications:
```json
{
   "hardware": {
      "hardware": "ranchu",
      "manufacturer": "Google",
      "storage": 5951,
      "memory": 1968,
      "cpu": 4,
      "display": {
         "width": 1080,
         "height": 2148,
         "orientation": 1
      },
      "camera": {
         "numberOfCameras": 2
      }
   }
}
```

### NetworkCollector (Custom Example)
```json
{
   "network": {
      "connected": true
   }
}
```

---

## Customization

### Using Built-in Collectors

The module comes with several built-in collectors that you can use:

```kotlin
import com.pingidentity.device.profile.collector.*

val collectors = mutableListOf<DeviceCollector<*>>().apply {
    add(CameraCollector)
    add(PlatformCollector())
    add(HardwareCollector)
}
```

### Creating Custom Collectors

You can create your own collectors to gather specific device information:

1. **Simple collector** using the factory function:

```kotlin
val BatteryCollector = DeviceCollector<Map<String, Any>>("battery") {
    mapOf(
        "level" to getBatteryPercentage(),
        "isCharging" to isDeviceCharging()
    )
}
```

2. **Complex collector** by implementing the interface:

```kotlin

class NetworkCollector : DeviceCollector<NetworkInfo> {
    override val key = "network"
    override val serializer = serializer<NetworkInfo>()

    override suspend fun collect(): NetworkInfo {
        // Collect network information
        return NetworkInfo(
            isConnected = true,
            type = "WIFI",
            strength = 85
        )
    }
}

@Serializable
data class NetworkInfo(
    val isConnected: Boolean,
    val type: String,
    val strength: Int
)
```

### Advanced Custom Collector Example

Here's a more comprehensive example of a custom collector:

```kotlin
class SecurityCollector : DeviceCollector<SecurityInfo> {
    override val key = "security"
    override val serializer = serializer<SecurityInfo>()

    override suspend fun collect(): SecurityInfo? {
        return try {
            SecurityInfo(
                hasBiometrics = checkBiometricAvailability(),
                isDeviceSecure = isDeviceSecure(),
                hasScreenLock = hasScreenLock(),
                isRooted = checkRootStatus()
            )
        } catch (e: Exception) {
            // Handle collection errors gracefully
            null
        }
    }

    private fun checkBiometricAvailability(): Boolean {
        // Implementation details...
        return BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_WEAK) == BIOMETRIC_SUCCESS
    }

    private fun isDeviceSecure(): Boolean {
        // Implementation details...
        return keyguardManager.isDeviceSecure
    }

    private fun hasScreenLock(): Boolean {
        // Implementation details...
        return keyguardManager.isKeyguardSecure
    }

    private fun checkRootStatus(): Boolean {
        // Implementation details...
        return RootBeer(context).isRooted
    }
}

@Serializable
data class SecurityInfo(
    val hasBiometrics: Boolean,
    val isDeviceSecure: Boolean,
    val hasScreenLock: Boolean,
    val isRooted: Boolean
)
```

### Conditional Collector Loading

```kotlin
fun createCollectors(context: Context): List<DeviceCollector<*>> {
    return mutableListOf<DeviceCollector<*>>().apply {
        // Always include basic collectors
        add(PlatformCollector())
        
        // Conditionally add collectors based on permissions
        if (ContextCompat.checkSelfPermission(context, CAMERA) == PERMISSION_GRANTED) {
            add(CameraCollector)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(BiometricCollector())
        }
        
        // Add based on device capabilities
        if (packageManager.hasSystemFeature(FEATURE_TELEPHONY)) {
            add(TelephonyCollector())
        }
    }
}
```

---

## 🔗 AIC Journey Integration

The `DeviceProfileCallback` class provides a specialized way to collect device information
specifically for integration with AIC Journeys. It creates device profiles in the format expected by
AIC Journeys.

### Basic Usage with DeviceProfileCallback

```kotlin
import com.pingidentity.device.profile.DeviceProfileCallback
import kotlinx.coroutines.runBlocking

deviceProfileCallback.collect()

```

By default, `DeviceProfileCallback.collect()` will use all the default collectors provided by
`DefaultDeviceCollector()`, organized in a format compatible with AIC.

### Customizing AIC Journey Device Profile Collection

You can customize which collectors are used:

```kotlin

val profile = deviceProfileCallback.collect {
   // Set a custom device identifier if needed for AIC Journey tracking
   deviceIdentifier = object : DeviceIdentifier {
      override val id = "your-custom-device-id"
   }

   // Add collectors in a metadata block
   metadata {
      // Add specific collectors relevant to your AIC risk assessment needs
      add(CameraCollector)
      add(PlatformCollector())
   }

   // Add more collectors in another metadata block if needed
   metadata {
      // Add custom collectors for enhanced AIC risk signals
      add(DeviceCollector("battery") {
         mapOf("level" to 95, "isCharging" to true)
      })
   }
}

// The profile is now ready for submission to AIC services
```

The data collected through this mechanism will be automatically formatted for proper integration
with PingOne AIC Journey risk assessment services.

### AIC Profile Structure

When using `DeviceProfileCallback`, the output is structured specifically for AIC consumption:

```json
{
  "identifier": "unique-device-id",
  "metadata": {
    "camera": { "noOfCameras": 2 },
    "platform": { "osVersion": "13" },
    "custom": { "batteryLevel": 85 }
  }
}
```

### Error Handling in AIC Integration

```kotlin
val result = deviceProfileCallback.collect {
    metadata {
        add(CameraCollector)
        add(PlatformCollector())
    }
}
result.onSuccess { profile ->
    // Submit to AIC service
   
}.onFailure { e ->
    // Handle collection errors
    Log.e("DeviceProfile", "Failed to collect profile", e)
}
```

---

## 📚 API Reference

### Core Interfaces

#### DeviceCollector<T>
```kotlin
interface DeviceCollector<T> {
    val key: String
    val serializer: KSerializer<T>
    suspend fun collect(): T?
}
```

#### DeviceProfileCallback
```kotlin
class DeviceProfileCallback {
    suspend fun collect(
        block: DeviceProfileConfig.() -> Unit = {}
    ):  Result<JsonObject>
}
```

### Extension Functions

#### collect()
```kotlin
suspend fun List<DeviceCollector<*>>.collect(): JsonObject
```

Collects data from all collectors and returns a JSON object.

#### DefaultDeviceCollector()
```kotlin
fun MutableList<DeviceCollector<*>>.DefaultDeviceCollector()
```

Adds the default set of collectors to the list.

---

