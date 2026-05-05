
[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Logger Module

The Ping Logger SDK provides a versatile logging interface and a set of common loggers for the Ping
SDKs.

## Getting Started

### Prerequisites

- Android API level 29 or higher

### Installation

To integrate this module into your Android project, include the following dependency in
your `build.gradle.kts` (or `build.gradle`) file:

```kotlin
dependencies {
    implementation("com.pingidentity.sdks:logger:<version>")
}
```

Replace `<version>` with the latest available version of the SDK from the Maven repository. Ensure your
project's `repositories` block includes Maven Central or the Ping Identity Maven repository.

## How to Use the SDK

### Logger to the Android LogCat

To log messages to the logcat, use the `STANDARD` logger:

```kotlin
import com.pingidentity.logger.Logger.Companion.logger

logger = Logger.STANDARD
logger.i("Hello World")
```

With the default the log will Tag with the SDK Version:
```
Ping SDK <Version>
```

### Disabling Logging

The Ping Logger SDK provides a `NONE` logger that does not log any messages:

```kotlin
import com.pingidentity.logger.Logger.Companion.logger

logger = Logger.NONE
logger.i("Hello World") // This message will not be logged
```

### Creating a Custom Logger

You can create a custom logger to suit your specific needs. For example, here's how to create a
logger that only logs
warning and error messages:

```kotlin
open class WarnErrorOnlyLogger : Logger {

    override fun d(message: String) {
    }

    override fun i(message: String) {
    }

    override fun w(message: String, throwable: Throwable?) {
        println("$message: $throwable")
    }

    override fun e(message: String, throwable: Throwable?) {
        println("$message: $throwable")
    }
}

val Logger.Companion.WARN_ERROR_ONLY: Logger by lazy {
    WarnErrorOnlyLogger()
}
```

To use your custom logger:

```kotlin
logger = Logger.WARN_ERROR_ONLY
logger.i("Hello World") // This message will not be logged
```

## Available Loggers

The Ping Logger SDK provides the following loggers:

| Logger   | Description                                           |
|----------|-------------------------------------------------------|
| STANDARD | Logs messages to Android's LogCat                     |
| WARN     | Logs warning and error messages with Android's LogCat |
| NONE     | Disables logging                                      |

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.