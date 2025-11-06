# Device Client Module

A Kotlin-based API client for managing device-related CRUD operations with Ping Identity's authentication system.

## Overview

The Device Client provides a unified interface to interact with multiple device types registered with Ping Identity:

- **OATH devices** - TOTP/HOTP credentials for one-time passwords
- **Push devices** - Push notification credentials for authentication challenges
- **Bound devices** - Device binding with cryptographic keys for passwordless authentication
- **Profile devices** - Device characteristics and metadata
- **WebAuthn devices** - FIDO2/Passkey credentials for biometric authentication

## Features

- ✅ **Unified API** - Single client for all device types
- ✅ **Type-Safe** - Sealed interface for device types with exhaustive when expressions
- ✅ **Local Storage** - Built-in caching for offline access
- ✅ **Server Sync** - Optional synchronization with Device Management Server
- ✅ **Kotlin Coroutines** - Async/await pattern with suspend functions
- ✅ **Result API** - Type-safe error handling with Kotlin Result
- ✅ **Extensible** - Custom storage and repository implementations

## Installation

Add the dependency to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":foundation:device:device-client"))
}
```

## Quick Start

### Basic Usage

```kotlin
// Initialize the DeviceClient
val deviceClient = DeviceClient.create(applicationContext) {
    logger = Logger.STANDARD
    autoSync = false // Set to true to auto-sync with server
}

// Create an OATH device
val oathDevice = OathDevice(
    id = UUID.randomUUID().toString(),
    issuer = "Example Corp",
    accountName = "user@example.com",
    oathType = "TOTP",
    secret = "BASE32ENCODEDSECRET",
    algorithm = "SHA1",
    digits = 6,
    period = 30
)

// Save the device
deviceClient.create(oathDevice).onSuccess { device ->
    println("Device created: ${device.id}")
}.onFailure { error ->
    println("Error: ${error.message}")
}

// Retrieve all devices
deviceClient.getAll().onSuccess { devices ->
    devices.forEach { device ->
        println("Device: ${device.id}, Type: ${device.type}")
    }
}

// Retrieve devices by type
deviceClient.getByType(DeviceType.OATH).onSuccess { oathDevices ->
    println("Found ${oathDevices.size} OATH devices")
}

// Retrieve a specific device
deviceClient.get(oathDevice.id).onSuccess { device ->
    if (device != null) {
        println("Found device: ${device.id}")
    }
}

// Delete a device
deviceClient.delete(oathDevice.id).onSuccess { deleted ->
    println("Device deleted: $deleted")
}

// Clear all devices
deviceClient.clear().onSuccess {
    println("All devices cleared")
}

// Close the client when done
deviceClient.close()
```

### Device Types

#### OATH Device (TOTP/HOTP)

```kotlin
val oathDevice = OathDevice(
    id = UUID.randomUUID().toString(),
    userId = "user123",
    issuer = "Example Corp",
    accountName = "user@example.com",
    oathType = "TOTP", // or "HOTP"
    secret = "BASE32SECRET",
    algorithm = "SHA1", // SHA1, SHA256, or SHA512
    digits = 6,
    period = 30, // For TOTP
    counter = 0L // For HOTP
)
```

#### Push Device

```kotlin
val pushDevice = PushDevice(
    id = UUID.randomUUID().toString(),
    userId = "user123",
    issuer = "Example Corp",
    accountName = "user@example.com",
    serverEndpoint = "https://auth.example.com/push",
    sharedSecret = "shared-secret-key",
    platform = "PING_AM" // or "PING_ONE"
)
```

#### Bound Device

```kotlin
val boundDevice = BoundDevice(
    id = UUID.randomUUID().toString(),
    userId = "user123",
    resourceId = "key-id-123",
    userName = "user@example.com",
    kid = "key-id-123",
    authType = "BIOMETRIC" // Authentication type
)
```

#### Profile Device

```kotlin
val profileDevice = ProfileDevice(
    id = UUID.randomUUID().toString(),
    userId = "user123",
    deviceName = "Pixel 8 Pro",
    deviceManufacturer = "Google",
    platform = "Android",
    platformVersion = "14"
)
```

#### WebAuthn Device

```kotlin
val webAuthnDevice = WebAuthnDevice(
    id = UUID.randomUUID().toString(),
    userId = "user123",
    rpId = "example.com",
    userHandle = "user-handle-base64",
    userName = "user@example.com",
    credentialId = "credential-id-base64"
)
```

## Advanced Usage

### Custom Storage Implementation

Implement the `DeviceStorage` interface for custom storage backends:

```kotlin
class CustomDeviceStorage : DeviceStorage {
    override suspend fun initialize() {
        // Initialize storage (e.g., open database)
    }
    
    override suspend fun save(device: Device) {
        // Save device to storage
    }
    
    override suspend fun get(id: String): Device? {
        // Retrieve device from storage
    }
    
    // ... implement other methods
}

// Use custom storage
val deviceClient = DeviceClient.create(applicationContext) {
    storage = CustomDeviceStorage()
}
```

### Server Synchronization

Implement the `DeviceRepository` interface for server communication:

```kotlin
class MyDeviceRepository(private val apiClient: HttpClient) : DeviceRepository {
    override suspend fun fetch(id: String): Device? {
        // Fetch device from server
    }
    
    override suspend fun create(device: Device): Device {
        // Create device on server
    }
    
    override suspend fun update(device: Device): Device {
        // Update device on server
    }
    
    override suspend fun delete(id: String): Boolean {
        // Delete device from server
    }
    
    override suspend fun sync(): List<Device> {
        // Fetch all devices from server
    }
    
    // ... implement other methods
}

// Use with auto-sync
val deviceClient = DeviceClient.create(applicationContext) {
    repository = MyDeviceRepository(httpClient)
    autoSync = true // Automatically sync with server
}

// Manual sync
deviceClient.sync().onSuccess { devices ->
    println("Synced ${devices.size} devices from server")
}
```

### Query Operations

```kotlin
// Get all devices for a specific user
deviceClient.getByUserId("user123").onSuccess { devices ->
    println("User has ${devices.size} devices")
}

// Get all OATH devices
deviceClient.getByType(DeviceType.OATH).onSuccess { oathDevices ->
    oathDevices.forEach { device ->
        val oath = device as OathDevice
        println("OATH: ${oath.issuer} - ${oath.accountName}")
    }
}

// Delete all devices for a user (e.g., on logout)
deviceClient.deleteByUserId("user123").onSuccess { count ->
    println("Deleted $count devices")
}

// Delete all devices of a specific type
deviceClient.deleteByType(DeviceType.PUSH).onSuccess { count ->
    println("Deleted $count push devices")
}
```

### Pattern Matching with Device Types

```kotlin
deviceClient.getAll().onSuccess { devices ->
    devices.forEach { device ->
        when (device) {
            is OathDevice -> {
                println("OATH: ${device.issuer} - ${device.oathType}")
            }
            is PushDevice -> {
                println("Push: ${device.issuer} - ${device.platform}")
            }
            is BoundDevice -> {
                println("Bound: ${device.userName} - ${device.authType}")
            }
            is ProfileDevice -> {
                println("Profile: ${device.deviceName}")
            }
            is WebAuthnDevice -> {
                println("WebAuthn: ${device.rpId}")
            }
        }
    }
}
```

## Architecture

```
DeviceClient
    ├── Device (sealed interface)
    │   ├── OathDevice
    │   ├── PushDevice
    │   ├── BoundDevice
    │   ├── ProfileDevice
    │   └── WebAuthnDevice
    ├── DeviceStorage (interface)
    │   └── InMemoryDeviceStorage (default)
    └── DeviceRepository (interface)
```

### Components

- **DeviceClient** - Main API client with CRUD operations
- **Device** - Sealed interface for type-safe device representations
- **DeviceStorage** - Interface for local persistence (implement for SharedPreferences, Room, etc.)
- **DeviceRepository** - Interface for server communication (implement for REST, GraphQL, etc.)
- **InMemoryDeviceStorage** - Default in-memory storage (data not persisted)

## Error Handling

The DeviceClient uses Kotlin's `Result` type for error handling:

```kotlin
deviceClient.create(device)
    .onSuccess { createdDevice ->
        // Handle success
    }
    .onFailure { error ->
        when (error) {
            is DeviceNotFoundException -> {
                // Device not found
            }
            is DeviceStorageException -> {
                // Storage error
            }
            is DeviceNetworkException -> {
                // Network error
            }
            is DeviceClientNotInitializedException -> {
                // Client not initialized
            }
            else -> {
                // Other errors
            }
        }
    }

// Or use getOrNull() for nullable results
val device = deviceClient.get(id).getOrNull()
if (device != null) {
    // Use device
}

// Or throw on error
try {
    val device = deviceClient.create(device).getOrThrow()
} catch (e: DeviceException) {
    // Handle exception
}
```

## Thread Safety

All DeviceClient operations are thread-safe and use Kotlin coroutines with `Dispatchers.IO` for I/O operations.

## Testing

The module includes `InMemoryDeviceStorage` for testing:

```kotlin
@Test
fun testDeviceOperations() = runTest {
    val client = DeviceClient.create(context) {
        storage = InMemoryDeviceStorage() // Use in-memory for tests
    }
    
    val device = OathDevice(
        id = "test-id",
        issuer = "Test",
        accountName = "test@example.com",
        oathType = "TOTP",
        secret = "SECRET"
    )
    
    client.create(device).onSuccess { created ->
        assertEquals("test-id", created.id)
    }
}
```

## Best Practices

1. **Initialize once** - Create a single DeviceClient instance per application
2. **Use dependency injection** - Inject DeviceClient where needed
3. **Cleanup on logout** - Call `deleteByUserId()` when user logs out
4. **Handle errors** - Always handle Result failures appropriately
5. **Close when done** - Call `close()` to release resources (e.g., in Activity/Fragment onDestroy)
6. **Use custom storage** - Implement persistent storage for production apps
7. **Enable auto-sync** - Set `autoSync = true` if server sync is critical

## License

Copyright (c) 2025 Ping Identity Corporation. All rights reserved.

This software may be modified and distributed under the terms of the MIT license.

