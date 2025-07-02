# Network Module

The Network Module provides a centralized networking infrastructure for all modules in the Ping Identity Android SDK.

## Features

- Centralized HTTP client for all SDK modules
- Standard headers for all SDK network requests
- Request and response interceptors for customizing network behavior
- Support for custom HTTP client implementations

## Getting Started

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

// Create a network client with the interceptor
val networkClient = NetworkClientFactory.create {
    addRequestInterceptor(AuthInterceptor("my-token"))
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

## Architecture

The Network Module is designed with the following components:

- **NetworkClient**: The main interface for making network requests
- **NetworkClientConfig**: Configuration for the network client
- **RequestInterceptor**: Interface for intercepting and modifying requests
- **ResponseInterceptor**: Interface for intercepting and modifying responses
- **HttpClientProvider**: Interface for providing custom HTTP client implementations

For more detailed information, see the [Design Document](DESIGN.md).

## Contributing

See the [Contributing Guide](../CONTRIBUTING.md) for more information.

## License

This project is licensed under the MIT License - see the [LICENSE](../../LICENSE) file for details.
