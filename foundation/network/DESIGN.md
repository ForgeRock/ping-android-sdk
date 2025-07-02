# Network Module Design Document

## Overview
The Network Module will provide a centralized networking infrastructure for all modules in the Ping Identity Android SDK. It will replace the individual HTTP client implementations in modules such as `oidc`, `orchestrate`, and `mfa`, consolidating networking functionality into a single module that enforces standards and provides extensibility.

## Problem Statement
Currently, each module (`oidc`, `orchestrate`, `mfa`) implements its own HTTP client with duplicated code for configuration, which leads to:

1. Code duplication across modules
2. Inconsistent behavior between modules
3. Difficulty in implementing global policies (e.g., standard headers)
4. No standard mechanism for developers to intercept or customize network requests
5. No ability for developers to provide their own HTTP client implementation

## Goals
1. Create a centralized network module in `foundation/network` that provides HTTP client functionality
2. Ensure all SDK network calls include standard headers such as `x-requested-with` and `x-requested-platform`
3. Provide an interface for developers to intercept and modify network requests and responses
4. Allow developers to provide their own HTTP client implementation
5. Ensure a seamless migration path for existing modules

## Non-Goals
1. Supporting non-HTTP protocols
2. Implementing custom caching strategies (will rely on Ktor's capabilities)
3. Building a complete networking library to replace Ktor

## Design

### Module Structure
```
foundation/network/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   └── kotlin/
│   │       └── com/
│   │           └── pingidentity/
│   │               └── network/
│   │                   ├── NetworkClient.kt
│   │                   ├── NetworkClientConfig.kt 
│   │                   ├── NetworkInterceptor.kt
│   │                   ├── RequestInterceptor.kt
│   │                   ├── ResponseInterceptor.kt
│   │                   ├── PingNetworkClient.kt
│   │                   ├── HttpClientProvider.kt
│   │                   ├── DefaultHttpClientProvider.kt
│   │                   ├── Headers.kt
│   │                   └── extensions/
│   │                       ├── HttpClientExtensions.kt
│   │                       └── LoggingExtensions.kt
│   ├── test/
│   │   └── kotlin/
│   │       └── com/
│   │           └── pingidentity/
│   │               └── network/
│   │                   ├── NetworkClientTest.kt
│   │                   ├── RequestInterceptorTest.kt
│   │                   └── ResponseInterceptorTest.kt
```

### Core Interfaces

#### NetworkClient
The main interface that modules will use to perform network operations.

```kotlin
interface NetworkClient {
    /**
     * Executes a network request and returns the response.
     *
     * @param block The request builder block.
     * @return The response from the request.
     */
    suspend fun <T> request(block: HttpRequestBuilder.() -> Unit): HttpResponse

    /**
     * Executes a network request and returns the response body.
     *
     * @param block The request builder block.
     * @return The response body from the request.
     */
    suspend inline fun <reified T> requestBody(block: HttpRequestBuilder.() -> Unit): T
    
    /**
     * Closes the client and releases resources.
     */
    fun close()
}
```

#### HttpClientProvider
Interface that allows developers to provide their own HTTP client implementation.

```kotlin
interface HttpClientProvider {
    /**
     * Creates and returns an HTTP client with the given configuration.
     *
     * @param config The configuration for the HTTP client.
     * @return The configured HTTP client.
     */
    fun createClient(config: NetworkClientConfig): HttpClient
}
```

#### NetworkInterceptor
Base interface for all interceptors.

```kotlin
interface NetworkInterceptor {
    /**
     * Gets the priority of this interceptor.
     * Interceptors with lower priority values will be executed first.
     */
    val priority: Int
}
```

#### RequestInterceptor
Interface for intercepting and modifying requests before they are sent.

```kotlin
interface RequestInterceptor : NetworkInterceptor {
    /**
     * Intercepts a request before it is sent.
     *
     * @param request The request to intercept.
     * @return The modified request.
     */
    fun intercept(request: HttpRequestBuilder): HttpRequestBuilder
}
```

#### ResponseInterceptor
Interface for intercepting and modifying responses after they are received.

```kotlin
interface ResponseInterceptor : NetworkInterceptor {
    /**
     * Intercepts a response after it is received.
     *
     * @param response The response to intercept.
     * @return The modified response.
     */
    suspend fun intercept(response: HttpResponse): HttpResponse
}
```

### Implementation Classes

#### NetworkClientConfig
Configuration class for the network client.

```kotlin
class NetworkClientConfig {
    /**
     * The HTTP client provider to use.
     * If not provided, a default provider will be used.
     */
    var httpClientProvider: HttpClientProvider? = null

    /**
     * The request interceptors to apply to all requests.
     */
    val requestInterceptors = mutableListOf<RequestInterceptor>()

    /**
     * The response interceptors to apply to all responses.
     */
    val responseInterceptors = mutableListOf<ResponseInterceptor>()

    /**
     * The timeout for HTTP requests in milliseconds.
     * Default is 15 seconds.
     */
    var timeout: Long = 15000

    /**
     * Whether to follow redirects.
     * Default is false.
     */
    var followRedirects: Boolean = false

    /**
     * The logger to use for logging.
     * Default is the SDK's default logger.
     */
    var logger: Logger = Logger.logger

    /**
     * Adds a request interceptor.
     *
     * @param interceptor The interceptor to add.
     */
    fun addRequestInterceptor(interceptor: RequestInterceptor) {
        requestInterceptors.add(interceptor)
        requestInterceptors.sortBy { it.priority }
    }

    /**
     * Adds a response interceptor.
     *
     * @param interceptor The interceptor to add.
     */
    fun addResponseInterceptor(interceptor: ResponseInterceptor) {
        responseInterceptors.add(interceptor)
        responseInterceptors.sortBy { it.priority }
    }
}
```

#### PingNetworkClient
Default implementation of the NetworkClient interface.

```kotlin
class PingNetworkClient(
    private val config: NetworkClientConfig
) : NetworkClient {
    private val httpClient: HttpClient = createHttpClient()

    private fun createHttpClient(): HttpClient {
        // Use the provided client provider or the default
        val provider = config.httpClientProvider ?: DefaultHttpClientProvider()
        return provider.createClient(config)
    }

    override suspend fun <T> request(block: HttpRequestBuilder.() -> Unit): HttpResponse {
        val requestBuilder = HttpRequestBuilder().apply(block)
        
        // Apply request interceptors
        val modifiedRequest = config.requestInterceptors.fold(requestBuilder) { req, interceptor ->
            interceptor.intercept(req)
        }
        
        // Execute the request
        var response = httpClient.request(modifiedRequest)
        
        // Apply response interceptors
        for (interceptor in config.responseInterceptors) {
            response = interceptor.intercept(response)
        }
        
        return response
    }

    override suspend inline fun <reified T> requestBody(block: HttpRequestBuilder.() -> Unit): T {
        return request(block).body()
    }

    override fun close() {
        httpClient.close()
    }
}
```

#### DefaultHttpClientProvider
Default implementation of HttpClientProvider that creates a Ktor HTTP client.

```kotlin
class DefaultHttpClientProvider : HttpClientProvider {
    override fun createClient(config: NetworkClientConfig): HttpClient {
        return HttpClient(CIO) {
            // Configure the client based on the provided configuration
            followRedirects = config.followRedirects
            
            // Add logging if a logger is provided
            if (config.logger !is None) {
                install(Logging) {
                    logger = createKtorLogger(config.logger)
                    level = LogLevel.ALL
                }
            }
            
            // Add timeout
            install(HttpTimeout) {
                requestTimeoutMillis = config.timeout
            }
            
            // Add default headers
            install(DefaultRequest) {
                header(Headers.X_REQUESTED_WITH, Headers.ANDROID_VALUE)
                header(Headers.X_REQUESTED_PLATFORM, Headers.ANDROID_PLATFORM)
            }
        }
    }
    
    private fun createKtorLogger(logger: Logger): io.ktor.client.plugins.logging.Logger {
        return object : io.ktor.client.plugins.logging.Logger {
            override fun log(message: String) {
                logger.d(message)
            }
        }
    }
}
```

#### Headers
Constants for standard headers.

```kotlin
object Headers {
    const val X_REQUESTED_WITH = "x-requested-with"
    const val X_REQUESTED_PLATFORM = "x-requested-platform"
    const val ANDROID_VALUE = "android"
    const val ANDROID_PLATFORM = "android"
}
```

### Extension Functions

These extension functions will help with common operations and integrations with Ktor.

```kotlin
// HttpClientExtensions.kt
fun HttpClient.toPingNetworkClient(): NetworkClient {
    // Create a NetworkClient that delegates to this HttpClient
    return object : NetworkClient {
        override suspend fun <T> request(block: HttpRequestBuilder.() -> Unit): HttpResponse {
            return this@toPingNetworkClient.request(block)
        }
        
        override suspend inline fun <reified T> requestBody(block: HttpRequestBuilder.() -> Unit): T {
            return this@toPingNetworkClient.request(block).body()
        }
        
        override fun close() {
            this@toPingNetworkClient.close()
        }
    }
}
```

## Migration Strategy

1. **Phase 1: Implementation**
   - Create the network module with all the required interfaces and implementations
   - Add unit tests to ensure functionality
   - Document the new APIs

2. **Phase 2: Integration**
   - Update one module (e.g., `oidc`) to use the new network module
   - Ensure backward compatibility
   - Run all integration tests to verify functionality

3. **Phase 3: Full Migration**
   - Update all remaining modules to use the new network module
   - Remove duplicate HTTP client code
   - Update documentation to reflect the changes

4. **Phase 4: Release**
   - Release a new version of the SDK with the network module
   - Update sample applications to demonstrate the new functionality

## Usage Examples

### Basic Usage

```kotlin
// Create a network client with default configuration
val networkClient = NetworkClientFactory.create {
    timeout = 30000
    followRedirects = true
}

// Use the client to make a request
val response = networkClient.requestBody<String> {
    url("https://api.example.com/endpoint")
    method = HttpMethod.Get
}
```

### Adding Custom Interceptors

```kotlin
// Create a custom request interceptor
class AuthInterceptor(private val token: String) : RequestInterceptor {
    override val priority: Int = 0
    
    override fun intercept(request: HttpRequestBuilder): HttpRequestBuilder {
        request.header("Authorization", "Bearer $token")
        return request
    }
}

// Create a custom response interceptor
class LoggingInterceptor : ResponseInterceptor {
    override val priority: Int = 0
    
    override suspend fun intercept(response: HttpResponse): HttpResponse {
        println("Response status: ${response.status}")
        return response
    }
}

// Create a network client with the interceptors
val networkClient = NetworkClientFactory.create {
    addRequestInterceptor(AuthInterceptor("my-token"))
    addResponseInterceptor(LoggingInterceptor())
}
```

### Using a Custom HTTP Client Provider

```kotlin
// Create a custom HTTP client provider
class MyHttpClientProvider : HttpClientProvider {
    override fun createClient(config: NetworkClientConfig): HttpClient {
        return HttpClient(OkHttp) {
            // Custom OkHttp configuration
        }
    }
}

// Create a network client with the custom provider
val networkClient = NetworkClientFactory.create {
    httpClientProvider = MyHttpClientProvider()
}
```

## Testing Strategy

1. **Unit Tests**
   - Test each component in isolation
   - Use mock HTTP clients for testing
   - Ensure all interceptors work as expected

2. **Integration Tests**
   - Test the network module with other modules
   - Ensure backward compatibility
   - Test with real network calls

3. **Performance Tests**
   - Measure the overhead of interceptors
   - Compare performance with the current implementation

## Open Questions

1. Should we support other HTTP client engines besides CIO?
2. Should we provide more advanced features such as caching, retry logic, etc.?
3. How should we handle authentication across different modules?
4. Should we support other protocols besides HTTP?

## Conclusion

The network module will provide a centralized, consistent, and extensible networking infrastructure for the Ping Identity Android SDK. It will improve code quality, reduce duplication, and provide developers with more flexibility and control over network operations.

## References

1. Ktor Client Documentation: https://ktor.io/docs/client.html
2. Android Network Security Configuration: https://developer.android.com/training/articles/security-config
3. Modern HTTP Networking on Android: https://developer.android.com/training/volley
