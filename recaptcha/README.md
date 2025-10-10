[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# ReCaptcha Enterprise Module

> **Seamless integration of Google ReCaptcha Enterprise into Ping Identity's Journey workflows for Android.**

The ReCaptcha Enterprise module provides a flexible, extensible, and developer-friendly way to add Google ReCaptcha Enterprise verification to your authentication flows using the Journey plugin architecture.

---

## ✨ Features

- **🔌 Plugin Architecture**: Integrates with the Journey framework for authentication orchestration
- **🛡️ Security**: Adds bot and abuse protection to your flows using Google ReCaptcha Enterprise
- **⚡ Asynchronous API**: Suspend functions for smooth, non-blocking UI
- **🧩 Extensible Provider**: Strategy pattern for custom Recaptcha client providers
- **📝 DSL Configuration**: Fluent, type-safe configuration using Kotlin DSL
- **📦 JSON Serialization**: Uses Kotlin serialization for easy data handling

---

## 📋 Table of Contents

- [Overview](#overview)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Usage Example](#usage-example)
- [Sequence Diagram](#sequence-diagram)
- [Extensibility](#extensibility)
- [Architecture](#architecture)
- [License](#license)

---

## Overview

This module enables you to add Google ReCaptcha Enterprise verification to your authentication journeys. It is designed to be used as a callback within the Journey plugin system, providing a secure and customizable way to verify user actions.

---

## Getting Started

Add the module to your project dependencies (see the main SDK documentation for details).

---

## Configuration

Configure the callback using the provided DSL:

```kotlin
callback.verify {
    reCaptchaSiteKey = "YOUR_SITE_KEY"
    recaptchaAction = RecaptchaAction.LOGIN
    timeoutInMills = 10000L
    setProvider(DefaultRecaptchaClientProvider(this))
}
```

---

## Usage Example

```kotlin
val result = callback.verify {
    reCaptchaSiteKey = "YOUR_SITE_KEY"
    // Optionally customize action, timeout, or provider
}
result.onSuccess { json ->
    // Handle successful verification
}.onFailure { error ->
    // Handle error
}
```

---

## Sequence Diagram

The following sequence diagram shows the complete flow of ReCaptcha Enterprise verification within the Journey framework:

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant Journey as Journey
    participant Callback as ReCaptchaEnterpriseCallback
    participant Config as ReCaptchaEnterpriseConfig
    participant Provider as RecaptchaClientProvider
    participant Context as ContextProvider
    participant Google as Google ReCaptcha
    participant Client as RecaptchaClient

    %% Journey Flow Initialization
    Dev->>Journey: start(journeyName)
    Journey->>Journey: Process journey flow
    Journey->>Callback: init(jsonObject)
    Note over Callback: Initialize with server config<br/>(site key, etc.)
    Callback->>Callback: Extract reCaptchaSiteKey
    Journey->>Dev: Return callback node

    %% Developer Verification Process
    Dev->>Callback: verify { DSL block }
    activate Callback
    
    %% Configuration Setup
    Callback->>Config: Create ReCaptchaEnterpriseConfig()
    Callback->>Config: Set reCaptchaSiteKey
    Callback->>Config: Apply DSL block
    Note over Config: Configure action, timeout,<br/>provider, etc.
    
    %% Provider Validation
    Callback->>Config: Get recaptchaClientProvider
    alt Provider not set
        Callback->>Dev: IllegalStateException("Provider not set")
    else Provider available
        Config->>Provider: Provide configured provider
    end
    
    %% Client Creation
    Callback->>Context: Get Application context
    Context->>Callback: Return Application
    Callback->>Provider: fetchClient(application, siteKey)
    activate Provider
    Provider->>Google: Recaptcha.fetchClient(app, siteKey)
    activate Google
    Google->>Client: Create RecaptchaClient
    Google->>Provider: Return RecaptchaClient
    deactivate Google
    Provider->>Callback: Return RecaptchaClient
    deactivate Provider
    
    %% Action Execution
    Callback->>Provider: execute(client, action, timeout)
    activate Provider
    Provider->>Client: execute(action, timeoutInMillis)
    activate Client
    Client->>Client: Perform ReCaptcha verification
    Client->>Provider: Result<String> (token or error)
    deactivate Client
    Provider->>Provider: Extract token from Result
    
    alt Verification successful
        Provider->>Callback: Return verification token
        Callback->>Callback: Json.encodeToJsonElement(token)
        Callback->>Callback: super.input(tokenJson)
        Callback->>Dev: Result.success(JsonObject)
    else Verification failed
        Provider->>Callback: Return null
        Callback->>Dev: IllegalStateException("Invalid captcha token")
    end
    deactivate Provider
    deactivate Callback
    
    %% Continue Journey Flow
    Dev->>Journey: next() with updated callback
    Journey->>Journey: Process with verification token
    Journey->>Dev: Next node or completion
```

---

## Extensibility

You can provide your own implementation of `RecaptchaClientProvider` to customize how the Recaptcha client is fetched or executed.

---

## Architecture

- **Plugin/Callback**: Integrates with the Journey plugin system
- **Strategy Pattern**: Customizable Recaptcha client provider
- **DSL**: Type-safe configuration
- **Kotlin Serialization**: For JSON handling

For a detailed class diagram and architecture, see the [CONCEPT.md](CONCEPT.md) file.

---

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.
