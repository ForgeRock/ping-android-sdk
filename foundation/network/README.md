[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Network Module

The Network module provides a flexible and powerful HTTP client abstraction for making network
requests. It defines a clean, platform-agnostic interface (`HttpClient`, `HttpRequest`,
`HttpResponse`) that can be implemented using different underlying HTTP client libraries.

## Getting Started

### Prerequisites

- Android API level 29 or higher

### Installation

To integrate this module into your Android project, include the following dependency in
your `build.gradle.kts` (or `build.gradle`) file:

```kotlin
dependencies {
  implementation("com.pingidentity.sdks:network:<version>")
}
```

Replace `<version>` with the latest available version of the SDK from the Maven repository. Ensure your
project's `repositories` block includes Maven Central or the Ping Identity Maven repository.

## Overview

The module uses **interface-based design** to decouple the HTTP client abstraction from its
implementation:

- **`HttpClient`** - Interface defining the contract for HTTP operations
- **`HttpRequest`** - Interface for building HTTP requests
- **`HttpResponse`** - Interface for accessing HTTP responses

This design allows you to:

- Swap implementations without changing application code
- Test your code with mock implementations
- Choose the best HTTP client for your platform

## Default Implementation: Ktor with CIO Engine

**Ktor is used as the default `HttpClient` implementation**, not as the foundation of the module
itself. The `KtorHttpClient` class implements the `HttpClient` interface using Ktor's powerful HTTP
client library.

By default, we use Ktor's **CIO (Coroutine-based I/O) engine**, which provides:

- Pure Kotlin implementation
- Lightweight and performant
- Built on Kotlin Coroutines
- No external dependencies beyond Kotlin stdlib

## Usage

### Making Requests

#### Simple GET Request

```kotlin
val response = httpClient.request {
    url = "https://api.example.com/data"
}

if (response.status.isSuccess()) {
    val data = response.body()
    println("Response: $data")
} else {
    println("Error: ${response.status}")
}
```

#### POST Request with JSON Body

```kotlin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

val response = httpClient.request {
    url = "https://api.example.com/users"
    post(buildJsonObject {
        put("name", "John Doe")
        put("email", "john@example.com")
    })
}
```

#### PUT Request with JSON Body

```kotlin
val response = httpClient.request {
    url = "https://api.example.com/users/123"
    put(buildJsonObject {
        put("name", "Updated Name")
        put("email", "updated@example.com")
    })
}
```

#### DELETE Request

```kotlin
// DELETE without body
val response = httpClient.request {
    url = "https://api.example.com/users/123"
    delete()
}

// DELETE with JSON body (optional)
val response = httpClient.request {
    url = "https://api.example.com/users/123"
    delete(buildJsonObject {
        put("reason", "user requested deletion")
    })
}
```

#### Form Data Submission

```kotlin
val response = httpClient.request {
    url = "https://api.example.com/login"
    form {
        put("username", "john.doe")
        put("password", "secret123")
    }
}
```

#### Adding Headers

```kotlin
val response = httpClient.request {
    url = "https://api.example.com/protected"
    header("Authorization", "Bearer token123")
    header("Accept", "application/json")
}
```

#### Query Parameters

```kotlin
val response = httpClient.request {
    url = "https://api.example.com/search"
    parameter("q", "kotlin")
    parameter("limit", "10")
    parameter("sort", "relevance")
}
```

#### Handling Cookies

```kotlin
val response = httpClient.request {
    url = "https://api.example.com/session"
    cookie("sessionId=abc123; Path=/; HttpOnly")
}

// Access cookies from response
val cookies = response.cookies()
cookies.forEach { cookie ->
    println("Cookie: $cookie")
}
```

#### Accumulating Form Parameters

The `form()` method accumulates parameters across multiple calls:

```kotlin
val request = KtorHttpRequest()
request.url = "https://api.example.com/submit"

// Add base fields
request.form {
    put("action", "create")
}

// Add user data conditionally
if (includeUser) {
    request.form {
        put("username", "john")
        put("email", "john@example.com")
    }
}

// Add CSRF token
request.form {
    put("csrf_token", generateToken())
}

// All parameters are accumulated in the final request
val response = httpClient.request(request)
```

### Response Handling

#### Accessing Response Data

```kotlin
val response = httpClient.request { url = "..." }

// Status code (property, not function)
val statusCode = response.status
println("Status: $statusCode")

// Check if successful (2xx)
if (response.status.isSuccess()) {
    println("Success!")
}

// Response body (suspend function)
val body = response.body()

// Headers
val contentType = response.header("Content-Type")
val allHeaders = response.headers()

// Cookies
val cookies = response.cookies()
```

#### HTTP Status Helpers

```kotlin
val response = httpClient.request { url = "..." }

// Extension function on Int
if (response.status.isSuccess()) {
    // Handle 200-299 status codes
    processSuccess(response.body())
} else {
    when (response.status) {
        400 -> handleBadRequest()
        401 -> handleUnauthorized()
        404 -> handleNotFound()
        500 -> handleServerError()
        else -> handleOtherError(response.status)
    }
}
```

### Resource Management

Always close the HTTP client when done:

```kotlin
try {
    val response = httpClient.request { url = "..." }
    // Use response
} finally {
    httpClient.close()
}
```

### HttpClient Factory Function

The module provides a convenient factory function that creates a configured Ktor-based HTTP client
with sensible defaults:

```kotlin
import com.pingidentity.network.ktor.HttpClient
import com.pingidentity.logger.Logger
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// Create a basic HTTP client with defaults
val httpClient = HttpClient {
    // Optional: Configure timeout (default: 15 seconds)
    timeout = 30.toDuration(DurationUnit.SECONDS)
    // Optional: Configure logging (default: Logger.WARN)
    logger = Logger.logger  // Use default application logger
}
```

### HttpClientConfig

The `HttpClientConfig` class provides a type-safe DSL for configuring the HTTP client with three
main configuration options:

#### 1. Timeout Configuration

Control how long requests can take before timing out:

```kotlin
val httpClient = HttpClient {
    // Set timeout for all requests
    timeout = 20.toDuration(DurationUnit.SECONDS)
}
```

#### 2. Logging Configuration

Configure HTTP request/response logging using the SDK's logger system:

```kotlin
// Warning level only (default)
val httpClient = HttpClient {
    logger = Logger.WARN
}

// Disable logging
val httpClient = HttpClient {
    logger = Logger.None
}
```

#### 3. Request and Response Interceptors

Add custom interceptors to modify requests before they're sent or inspect responses after they're
received:

**Request Interceptors** - Execute before the request is sent:

```kotlin
val httpClient = HttpClient {
    // Add correlation ID
    onRequest {
        header("X-Correlation-ID", UUID.randomUUID().toString())
    }

    // Add custom headers conditionally
    onRequest {
        if (isDebugMode) {
            header("X-Debug", "true")
        }
    }
}
```

**Response Interceptors** - Execute after the response is received:

```kotlin
val httpClient = HttpClient {
    // Log response status
    onResponse {
        println("Response status: $status")
    }

    // Track response times
    onResponse {
        val duration = header("X-Response-Time")
        analytics.trackResponseTime(duration)
    }

    // Monitor error rates
    onResponse {
        if (status >= 500) {
            errorMonitor.recordServerError(status)
        }
    }
}
```

### Using HttpClient with SDK Modules

Once configured, pass the `HttpClient` to any SDK module:

```kotlin
import com.pingidentity.network.ktor.HttpClient

// Create and configure HTTP client
val client = HttpClient {
    timeout = 30.toDuration(DurationUnit.SECONDS)
    logger = Logger.logger

    onRequest {
        header("X-App-Version", BuildConfig.VERSION_NAME)
    }
}

// Use with Journey Module
val journey = Journey {
    httpClient = client
    // ... other configuration
}

// Use with DaVinci Module
val daVinci = DaVinci {
    httpClient = client
    // ... other configuration
}

// Use with MFA Module
val mfa = MFA {
    httpClient = client
    // ... other configuration
}
```
## Custom HttpClient

In some scenarios, the provided implementations (CIO, Android, OkHttp) may not fit your company's
security policies, compliance requirements, or specific networking needs. In such cases, you can
implement your own custom HTTP client that aligns with the `HttpClient`, `HttpRequest`, and
`HttpResponse` interfaces.

### When to Create a Custom Implementation

Consider implementing a custom HttpClient when:

- **Security Requirements**: Your company requires a specific HTTP client library for security
  compliance
- **Custom Networking Stack**: You have proprietary networking infrastructure
- **Legacy Systems**: You need to integrate with existing custom HTTP implementations
- **Regulatory Compliance**: Industry regulations mandate specific networking implementations
- **Performance Optimization**: You need highly specialized performance characteristics

### Custom HttpClient Implementation

Here's a prototype showing how to implement `HttpClient` interfaces:

```kotlin

/**
 * Custom HttpClient implementation.
 * Replace the internal implementation with your company's HTTP library.
 */
class CustomHttpClient : HttpClient {

    override fun request(): HttpRequest {
        return CustomHttpRequest()
    }

    override suspend fun request(request: HttpRequest): HttpResponse {
        require(request is CustomHttpRequest) {
            "Request must be CustomHttpRequest"
        }

        // TODO: Use your company's HTTP client to execute the request
        // Example pseudocode:
        // val response = yourCompanyHttpClient.execute(
        //     method = request.method,
        //     url = request.url,
        //     headers = request.headers,
        //     body = request.body
        // )

        return CustomHttpResponse(request, /* your response object */)
    }

    override suspend fun request(requestBuilder: HttpRequest.() -> Unit): HttpResponse {
        val request = CustomHttpRequest().apply(requestBuilder)
        return request(request)
    }

    override fun close() {
        // Clean up resources
    }
}

```

### Usage of Custom HttpClient

Once implemented, use it like any other HttpClient:

```kotlin
// Create your custom client
val customHttpClient: HttpClient = CustomHttpClient()

// Use it with Journey
val journey = Journey {
    httpClient = customHttpClient
    // ... other configuration
}

// Use it with DaVinci
val daVinci = DaVinci {
    httpClient = customHttpClient
    // ... other configuration
}

// Use it directly
val response = customHttpClient.request {
    url = "https://api.example.com/users"
    header("Authorization", "Bearer token123")
}

if (response.status.isSuccess()) {
    println("Success: ${response.body()}")
}

// Clean up
customHttpClient.close()
```

---

## Dependencies

This module relies on the following key dependencies:

* [Ktor](https://ktor.io/): For the underlying HTTP client implementation.
* [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization): For JSON serialization
  and deserialization.
* [Kotlinx Coroutines](https://github.com/Kotlin/kotlinx.coroutines): For managing asynchronous
  operations.

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.

