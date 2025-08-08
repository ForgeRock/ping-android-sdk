<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Logo">
  </a>
  <hr/>
</p>

# Device Profile Module

The Device Profile module provides a structured framework for collecting device information in
Android applications. It uses a modular collector system that makes it easy to gather, extend, and
customize the device data you need.

## Overview

This module helps you collect various device attributes through dedicated collectors:

- Hardware information (camera, display)
- Platform details
- And more through custom collectors you can add

## Getting Started

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

    override suspend fun collect(): NetworkInfo? {
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

### Customizing Default Collectors

You can modify the default collector set:

```kotlin
val collectors = mutableListOf<DeviceCollector<*>>().apply(DefaultDeviceCollector())

// Remove a specific collector
collectors.removeIf { it.key == "camera" }

// Add your own collectors
collectors.add(BatteryCollector)
collectors.add(NetworkCollector())
println(collectors.collect())
```

## Using DeviceProfileCallback for AIC Journey Integration

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

## Advanced Usage

### Filtering Collectors

You can filter which collectors run based on runtime conditions:

```kotlin
val collectors = mutableListOf<DeviceCollector<*>>().apply(DefaultDeviceCollector())

// Only collect data from specific collectors
val filteredCollectors = collectors.filter { it.key in setOf("platform", "hardware") }
val profile = runBlocking { filteredCollectors.collect() }
```

## Best Practices

1. **Collect asynchronously** - The collect method is a suspend function, so use it within a
   coroutine context
2. **Be selective** - Only collect the data you need for your use case
3. **Respect privacy** - Inform users about what device data you collect and why
4. **Handle errors** - Some collectors may fail on certain devices
