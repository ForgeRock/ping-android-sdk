[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Device Profile Module

The Device Profile module provides a structured framework for collecting device information in
Android applications. It uses a modular collector system that makes it easy to gather, extend, and
customize the device data you need.

## Getting Started

### Prerequisites

- Ping Identity Platform
    - Ping Advanced Identity Cloud
    - PingAM 6.5.2 or higher
- Android API level 29 or higher

### Installation

To integrate this module into your Android project, include the following dependency in
your `build.gradle.kts` (or `build.gradle`) file:

```kotlin
dependencies {
  implementation("com.pingidentity.sdks:device-profile:<version>")
}
```

Replace `<version>` with the latest available version of the SDK from the Maven repository. Ensure your
project's `repositories` block includes Maven Central or the Ping Identity Maven repository.

### Permissions

The module respects Android's permission model. Some collectors may require specific permissions:

```xml
<!-- Required for NetworkCollector -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Optional: For LocationCollector (will prompt user automatically) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Note: LocationCollector handles permission requests automatically -->
```

## Overview

This module helps you collect various device attributes through dedicated collectors:

- **Hardware information**: Camera capabilities, display properties, CPU cores, memory, storage
- **Platform details**: OS version, manufacturer, model, brand, locale, timezone
- **Network information**: Connection status, network capabilities
- **Telephony information**: Carrier name, network country code
- **Location data**: GPS coordinates (with automatic permission handling)
- **Custom collectors**: Extend with your own logic for any device data

### Architecture

```
┌─────────────────────────────────────────┐
│            Application Layer            │
├─────────────────────────────────────────┤
│         Device Profile Module           │
│  ┌─────────────┐  ┌─────────────────┐   │
│  │ Collectors  │  │ AIC Integration │   │
│  │             │  │                 │   │
│  │ • Platform  │  │ • DeviceProfile │   │
│  │ • Hardware  │  │   Callback      │   │
│  │ • Network   │  │ • Permission    │   │
│  │ • Telephony │  │   Management    │   │
│  │ • Location  │  │                 │   │
│  │ • Bluetooth │  │                 │   │
│  │ • Browser   │  │                 │   │
│  └─────────────┘  └─────────────────┘   │
├─────────────────────────────────────────┤
│          Android Platform API           │
│          AndroidBuildProvider           │
│          Permission Handling            │
│          Location Services              │
└─────────────────────────────────────────┘
```

## Usage

### Basic Usage

**Create collectors** and collect device information:

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
  "platform": {
    "platform": "android",
    "device": "flame",
    "deviceName": "Pixel 4",
    "model": "Pixel 4", 
    "brand": "google",
    "locale": "en_US",
    "timeZone": "America/Vancouver"
  },
  "hardware": {
    "hardware": "flame",
    "manufacturer": "Google",
    "storage": 64,
    "memory": 6144,
    "cpu": 8,
    "display": {
      "width": 1080,
      "height": 2280
    },
    "camera": {
      "noOfCameras": 2
    }
  },
  "network": {
    "connected": true
  },
  "telephony": {
    "networkCountryIso": "US",
    "carrierName": "Verizon"
  },
  "bluetooth": {
    "supported": true
  },
  "browser": {
    "userAgent": "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Mobile Safari/537.36"
  },
  "location": {
    "latitude": 37.7749,
    "longitude": -122.4194
  }
}
```

## 🔧 Built-in Collectors

The module provides several built-in collectors out of the box:

### PlatformCollector
Gathers platform and device identification information:
```json
{
   "platform": {
      "platform": "android",
      "device": "flame",
      "deviceName": "Pixel 4",
      "model": "Pixel 4",
      "brand": "google",
      "locale": "en_US",
      "timeZone": "America/Vancouver"
   }
}
```

### HardwareCollector
Collects comprehensive hardware specifications:
```json
{
   "hardware": {
      "hardware": "flame",
      "manufacturer": "Google",
      "storage": 64,
      "memory": 6144,
      "cpu": 8,
      "display": {
         "width": 1080,
         "height": 2280
      },
      "camera": {
         "noOfCameras": 2
      }
   }
}
```

### NetworkCollector
Determines current network connectivity status:
```json
{
   "network": {
      "connected": true
   }
}
```

### TelephonyCollector
Collects carrier and network information:
```json
{
   "telephony": {
      "networkCountryIso": "US", 
      "carrierName": "Verizon"
   }
}
```

### LocationCollector
Collects GPS coordinates with automatic permission handling:
```json
{
   "location": {
      "latitude": 37.7749,
      "longitude": -122.4194
   }
}
```

### BluetoothCollector
Checks if Bluetooth is supported on the device:
```json
{
    "bluetooth": {
        "supported": true
    }
}
```

### BrowserCollector
Gathers the default browser's user agent string:
```json
{
    "browser": {
        "userAgent": "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Mobile Safari/537.36"
    }
}
```

**Location Privacy Features:**
- Automatically requests permissions when needed
- Uses a transparent activity for seamless permission flow
- Gracefully handles permission denials
- Includes timeout protection (30 seconds)
- Returns null if location is unavailable or denied

## Customization

### Using Built-in Collectors

The module comes with several built-in collectors that you can use:

```kotlin
import com.pingidentity.device.profile.collector.*

val collectors = mutableListOf<DeviceCollector<*>>().apply {
    clear()
    add(PlatformCollector())
    add(HardwareCollector())
    add(NetworkCollector())
    add(TelephonyCollector)
    add(LocationCollector()) // Will handle permissions automatically
}

// Or use the default configuration
val collectors = mutableListOf<DeviceCollector<*>>().apply(DefaultDeviceCollector())
```

### Creating Custom Collectors

You can create your own collectors to gather specific device information:

1. **Simple collector** using the factory function:

```kotlin
val BatteryCollector = DeviceCollector<Map<String, String>>("battery") {
    mapOf(
        "level" to "100",
        "isCharging" to "true",
    )
}

// or using a data class for structured data
@Serializable // Ensure the data class is serializable
data class BatteryData(val level: Int, val isCharging: Boolean, val capacity: Int)

val BatteryCollector = DeviceCollector<BatteryData>("battery") {
    BatteryData(
        level = 100,
        isCharging = true,
        capacity = 4000,
    )
}
```

2. **Complex collector** by implementing the interface:

```kotlin
class SecurityCollector : DeviceCollector<SecurityInfo> {
    override val key = "security"
    override val serializer = SecurityInfo.serializer()

    override suspend fun collect(): SecurityInfo {
        return SecurityInfo(
            isDeviceSecure = checkDeviceSecuritySettings(),
            hasScreenLock = checkScreenLockStatus(),
            biometricsAvailable = checkBiometricCapabilities()
        )
    }
}

@Serializable
data class SecurityInfo(
    val isDeviceSecure: Boolean,
    val hasScreenLock: Boolean,
    val biometricsAvailable: Boolean
)
```

### Testable Architecture

The module is designed with testing in mind:

```kotlin
// Example: Testing HardwareCollector with mocked Build properties
class HardwareCollectorTest {
    @Test
    fun `test hardware collection with custom build provider`() = runTest {
        val mockBuildProvider = mockk<AndroidBuildProvider> {
            every { getHardware() } returns "test-hardware"
            every { getManufacturer() } returns "test-manufacturer"
        }
        
        val collector = HardwareCollector(mockBuildProvider)
        val result = collector.collect()
        
        assertEquals("test-hardware", result.hardware)
        assertEquals("test-manufacturer", result.manufacturer)
    }
}
```

### Conditional Collector Loading

```kotlin
fun createCollectors(context: Context): List<DeviceCollector<*>> {
    return mutableListOf<DeviceCollector<*>>().apply {
        // Always include basic collectors
        add(PlatformCollector())
        add(HardwareCollector())
        
        // Conditionally add collectors based on permissions
        if (ContextCompat.checkSelfPermission(context, ACCESS_NETWORK_STATE) == PERMISSION_GRANTED) {
            add(NetworkCollector())
        }
        
        // LocationCollector handles its own permissions
        add(LocationCollector())
        
        // Add based on device capabilities
        if (packageManager.hasSystemFeature(FEATURE_TELEPHONY)) {
            add(TelephonyCollector)
        }
    }
}
```

---

## AIC Journey Integration

The `DeviceProfileCallback` class provides a specialized way to collect device information
specifically for integration with AIC Journeys. It creates device profiles in the format expected by
AIC Journeys.

### Basic Usage with DeviceProfileCallback

```kotlin
import com.pingidentity.device.profile.DeviceProfileCallback
import kotlinx.coroutines.runBlocking

// Collect with default settings - use DefaultDeviceCollector
val result = deviceProfileCallback.collect {
    collectors.apply(DefaultDeviceCollector())
}
```

By default, `DeviceProfileCallback.collect()` will not use the default collectors provided by
`DefaultDeviceCollector()` unless being passed in the `collect()` block. This allows you to
customize which collectors are used for AIC Journey profiles.

### Customizing AIC Journey Device Profile Collection

You can customize which collectors are used:

```kotlin
val profile = deviceProfileCallback.collect {
   // Set a custom device identifier if needed for AIC Journey tracking
   deviceIdentifier = object : DeviceIdentifier {
       override val id: suspend () -> String = { "your-custom-device-id" }
   }

   // Add collectors in a metadata block
   collectors {
      // Clear default collectors if needed
      clear()
      // Add specific collectors relevant to your AIC risk assessment needs
      add(PlatformCollector())
      add(HardwareCollector())
      add(NetworkCollector())
      add(LocationCollector()) // Will automatically handle permissions
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
  "platform": {
    "platform": "android",
    "device": "flame",
    "deviceName": "Pixel 4"
  },
  "hardware": {
    "manufacturer": "Google",
    "storage": 64,
    "memory": 6144
  },
  "network": {
    "connected": true
  },
  "location": {
    "latitude": 37.7749,
    "longitude": -122.4194
  }
}
```

### Error Handling in AIC Integration

```kotlin
val result = deviceProfileCallback.collect {
    collectors {
        clear() // Clear default collectors
        add(PlatformCollector())
        add(HardwareCollector())
        add(LocationCollector()) // May return null if permission denied
    }
}

result.onSuccess { profile ->
    // Submit to AIC service
    // Note: Some collectors may return null values if data unavailable
}.onFailure { e ->
    // Handle collection errors
    Log.e("DeviceProfile", "Failed to collect profile", e)
}
```

## API Reference

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
        block: DeviceProfileConfig.() -> Unit = DefaultProfile()
    ): Result<JsonObject>
}
```

### Built-in Collectors

#### PlatformCollector
```kotlin
val PlatformCollector: DeviceCollector<PlatformInfo>
```

#### HardwareCollector
```kotlin
class HardwareCollector(
    androidBuildProvider: AndroidBuildProvider = DefaultAndroidBuildProvider()
) : DeviceCollector<HardwareInfo>
```

#### NetworkCollector  
```kotlin
class NetworkCollector(
    sdkVersionProvider: SdkVersionProvider = DefaultSdkVersionProvider()
) : DeviceCollector<NetworkInfo>
```

#### TelephonyCollector
```kotlin
val TelephonyCollector: DeviceCollector<TelephonyInfo>
```

#### LocationCollector
```kotlin
class LocationCollector : DeviceCollector<LocationInfo>
```

### Extension Functions

#### collect()
```kotlin
suspend fun List<DeviceCollector<*>>.collect(): JsonObject
```

Collects data from all collectors and returns a JSON object.

#### DefaultDeviceCollector()
```kotlin
fun DefaultDeviceCollector(): MutableList<DeviceCollector<*>>.() -> Unit
```

Adds the default set of collectors to the list (Platform, Hardware, Network, Telephony, Location).

### Utility Classes

#### AndroidBuildProvider
```kotlin
interface AndroidBuildProvider {
    fun getHardware(): String?
    fun getManufacturer(): String?
    fun getModel(): String?
    fun getBrand(): String?
    fun getDevice(): String?
}
```

Provides testable access to Android Build properties.

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.
