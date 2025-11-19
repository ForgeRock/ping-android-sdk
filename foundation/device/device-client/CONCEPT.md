<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Logo">
  </a>
  <hr/>
</p>

# Design Concept

## Overview

The Device Client module provides a unified API for managing various types of Multi-Factor Authentication (MFA) devices and user profile devices registered with Ping Identity services. It abstracts the complexities of interacting with different device types through a consistent interface, enabling developers to retrieve, update, and delete user devices across multiple authentication methods.

## Architecture

### Device Management Interface

The Device Client follows a generic interface pattern where all device types implement the `DeviceImplementation<T>` interface. This ensures a consistent API surface across different device types while maintaining type safety.

```kotlin
interface DeviceImplementation<T> {
    suspend fun get(): List<T>
    suspend fun delete(device: T)
    suspend fun update(device: T)
}
```

### Supported Device Types

The module supports the following device types, all extending from the sealed `Device` class:

| Device Type    | Description                                                                 | URL Suffix            |
|----------------|-----------------------------------------------------------------------------|-----------------------|
| OathDevice     | Time-based One-Time Password (TOTP) or HMAC-based OTP devices              | devices/2fa/oath      |
| PushDevice     | Push notification-based authentication devices                              | devices/2fa/push      |
| BoundDevice    | Cryptographically bound devices for device binding authentication           | devices/2fa/binding   |
| WebAuthnDevice | FIDO2/WebAuthn biometric or security key devices                           | devices/2fa/webauthn  |
| ProfileDevice  | User profile devices tracking device metadata and usage                     | devices/profile       |

### Class Hierarchy

```mermaid
classDiagram
    class DeviceImplementation~T~ {
        <<interface>>
        +get() List~T~
        +delete(device: T)
        +update(device: T)
    }
    
    class Device {
        <<sealed>>
        +String id
        +String deviceName
        +String urlSuffix
    }
    
    class OathDevice {
        +String uuid
        +Long createdDate
        +Long lastAccessDate
    }
    
    class PushDevice {
        +String uuid
        +Long createdDate
        +Long lastAccessDate
    }
    
    class BoundDevice {
        +String deviceId
        +String uuid
        +Long createdDate
        +Long lastAccessDate
    }
    
    class WebAuthnDevice {
        +String uuid
        +String credentialId
        +Long createdDate
        +Long lastAccessDate
    }
    
    class ProfileDevice {
        +String metadata
        +Long lastAccessDate
    }
    
    Device <|-- OathDevice
    Device <|-- PushDevice
    Device <|-- BoundDevice
    Device <|-- WebAuthnDevice
    Device <|-- ProfileDevice
```

## DeviceClient Configuration

The `DeviceClient` uses a DSL-based configuration pattern for initialization, similar to other modules in the SDK:

```kotlin
class DeviceClientConfig {
    var ssoTokenString: String? = null
    var serverUrl: String = ""
    var realm: String = ""
    var httpClient: HttpClient = HttpClient()
}
```

### Configuration Parameters

- **ssoTokenString**: The Single Sign-On token used for authentication with the server
- **serverUrl**: The base URL of the Ping Identity server
- **realm**: The authentication realm
- **httpClient**: The Ktor HTTP client used for network requests (can be customized)

## Device Retrieval Flow

The following sequence diagram illustrates how device data is retrieved from the server:

```mermaid
sequenceDiagram
    participant App
    participant DeviceClient
    participant DeviceImplementation
    participant HttpClient
    participant PingServer
    
    App->>DeviceClient: Create DeviceClient { config }
    App->>DeviceClient: oathDeviceClient.get()
    DeviceClient->>DeviceImplementation: get()
    DeviceImplementation->>HttpClient: request(url, headers)
    HttpClient->>PingServer: GET /users/{uid}/devices/2fa/oath
    PingServer-->>HttpClient: HTTP Response (JSON)
    HttpClient-->>DeviceImplementation: HttpResponse
    DeviceImplementation->>DeviceImplementation: getDevices<OathDevice>(response)
    DeviceImplementation->>DeviceImplementation: Parse JSON and deserialize
    DeviceImplementation-->>DeviceClient: List<OathDevice>
    DeviceClient-->>App: List<OathDevice>
```

### URL Composition Strategy

The module composes URLs dynamically based on the configuration and device type:

```
{serverUrl}/json/realms/{realm}/users/{ssoTokenString}/{urlSuffix}?_queryFilter=true
```

Example:
```
https://openam.example.com/json/realms/alpha/users/abc123/devices/2fa/oath?_queryFilter=true
```

### Response Parsing

The server response follows this structure:

```json
{
  "result": [
    {
      "_id": "device-123",
      "deviceName": "My Authenticator",
      "uuid": "uuid-456",
      "createdDate": 1699900800000,
      "lastAccessDate": 1700000000000
    }
  ]
}
```

The `getDevices<T>()` method:
1. Extracts the `result` array from the JSON response
2. Deserializes each object into the specified device type using Kotlin Serialization
3. Returns a type-safe list of devices

## Lazy Initialization Pattern

Each device client is lazily initialized using Kotlin's `by lazy` delegate, ensuring that the implementation is only created when first accessed:

```kotlin
val oathDeviceClient: DeviceImplementation<OathDevice> by lazy {
    object : DeviceImplementation<OathDevice> {
        // Implementation
    }
}
```

This pattern:
- Reduces initial memory footprint
- Improves performance by deferring object creation
- Ensures thread-safe initialization

## Implementation Details

### Delete Implementation

All device types follow the same pattern for deletion:

```kotlin
override suspend fun delete(device: OathDevice) {
    withContext(Dispatchers.IO) {
        httpClient.request {
            url.apply { composeUrlForDevice(config, device) }
            headers {
                append("Authorization", "Bearer $ssoTokenString")
                append("Content-Type", "application/json")
            }
            method = Delete
        }
    }
}
```

The `composeUrlForDevice()` helper method constructs the device-specific URL:

```kotlin
private fun composeUrlForDevice(
    config: DeviceClientConfig,
    device: Device,
): Uri {
    val uri = Uri.Builder()
        .encodedPath(config.serverUrl)
        .appendPath("json")
        .appendPath("realms")
        .appendPath(config.realm)
        .appendPath("users")
        .appendPath(config.ssoTokenString ?: "")
        .appendEncodedPath(device.urlSuffix)
        .appendEncodedPath(device.id)
    return uri.build()
}
```

### Update Implementation

All device types follow the same pattern for updates:

```kotlin
override suspend fun update(device: OathDevice) {
    withContext(Dispatchers.IO) {
        httpClient.request {
            url.apply { composeUrlForDevice(config, device) }
            headers {
                append("Authorization", "Bearer $ssoTokenString")
                append("Content-Type", "application/json")
            }
            method = Put
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(device))
        }
    }
}
```

The update operation:
- Uses `Json.encodeToString()` to serialize the device object
- Sets the Content-Type header to application/json
- Sends the serialized device data in the request body
- Uses the same URL pattern as delete with the device ID

## Authentication Strategy

The Device Client uses Bearer token authentication with the SSO token:

```kotlin
headers {
    append("Authorization", "Bearer $ssoTokenString")
    append("Content-Type", "application/json")
}
```

This approach:
- Maintains stateless authentication across requests
- Leverages existing session tokens from Journey or DaVinci flows
- Provides secure access to user-specific device data

## Device Update Flow

The following sequence diagram illustrates how device data is updated on the server:

```mermaid
sequenceDiagram
    participant App
    participant DeviceClient
    participant DeviceImplementation
    participant HttpClient
    participant PingServer
    
    App->>DeviceClient: oathDeviceClient.update(device)
    DeviceClient->>DeviceImplementation: update(device)
    DeviceImplementation->>DeviceImplementation: Serialize device to JSON
    DeviceImplementation->>HttpClient: request(url, headers, body)
    HttpClient->>PingServer: PUT /users/{uid}/devices/2fa/oath/{deviceId}
    PingServer-->>HttpClient: HTTP Response (200 OK)
    HttpClient-->>DeviceImplementation: HttpResponse
    DeviceImplementation-->>DeviceClient: Success
    DeviceClient-->>App: Success
```

### Update Operation Details

The `update(device: T)` method:
1. Serializes the device object to JSON using Kotlin Serialization
2. Sends a PUT request to the device-specific URL
3. Includes the device ID in the URL path: `{urlSuffix}/{deviceId}`
4. Sets the request body with the serialized device data
5. Uses Bearer token authentication

Example URL for update:
```
https://openam.example.com/json/realms/alpha/users/abc123/devices/2fa/oath/device-123
```

## Device Deletion Flow

The following sequence diagram illustrates how a device is deleted from the server:

```mermaid
sequenceDiagram
    participant App
    participant DeviceClient
    participant DeviceImplementation
    participant HttpClient
    participant PingServer
    
    App->>DeviceClient: oathDeviceClient.delete(device)
    DeviceClient->>DeviceImplementation: delete(device)
    DeviceImplementation->>HttpClient: request(url, headers)
    HttpClient->>PingServer: DELETE /users/{uid}/devices/2fa/oath/{deviceId}
    PingServer-->>HttpClient: HTTP Response (200 OK)
    HttpClient-->>DeviceImplementation: HttpResponse
    DeviceImplementation-->>DeviceClient: Success
    DeviceClient-->>App: Success
```

### Delete Operation Details

The `delete(device: T)` method:
1. Sends a DELETE request to the device-specific URL
2. Includes the device ID in the URL path: `{urlSuffix}/{deviceId}`
3. Uses Bearer token authentication
4. No request body is required

Example URL for delete:
```
https://openam.example.com/json/realms/alpha/users/abc123/devices/2fa/oath/device-123
```

## Error Handling Considerations

The implementation handles various error scenarios that may occur during device operations:

1. **Network Errors**: Connection timeouts, DNS resolution failures
2. **Authentication Errors**: Invalid or expired tokens (401 Unauthorized)
3. **Authorization Errors**: Insufficient permissions (403 Forbidden)
4. **Parsing Errors**: Malformed JSON responses (for GET operations)
5. **Server Errors**: 5xx status codes indicating server-side issues
6. **Not Found Errors**: Device ID doesn't exist (404 Not Found)

Applications should wrap device operations in try-catch blocks to handle these errors gracefully.

## Coroutines and Threading

All device operations are suspend functions that execute on the `Dispatchers.IO` context:

```kotlin
override suspend fun get(): List<OathDevice> {
    return withContext(Dispatchers.IO) {
        // Network operation
    }
}
```

This ensures:
- Network operations don't block the main thread
- Proper resource management for I/O-bound operations
- Integration with Kotlin coroutines ecosystem

## Future Enhancements

The Device Client architecture is designed to support future enhancements:

1. **Enhanced Error Handling**: Wrap operations in `Result<T>` or custom sealed classes for better error handling
2. **Pagination Support**: Handling large device lists with pagination parameters
3. **Filtering and Sorting**: Advanced query capabilities using `_queryFilter` parameter
4. **Real-time Updates**: WebSocket or polling mechanisms for device status changes
5. **Caching Strategy**: Local caching of device data with TTL and invalidation policies
6. **Offline Support**: Queue operations for execution when connectivity is restored
7. **Batch Operations**: Support for deleting or updating multiple devices in a single request
8. **Device Registration**: APIs for registering new devices (currently handled by MFA modules)
9. **Device Validation**: Pre-request validation of device data before sending to server

