<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Logo">
  </a>
  <hr/>
</p>

# Ping Logger SDK

The Ping Logger SDK provides a versatile logging interface and a set of common loggers for the Ping
SDKs.

## Integrating the SDK into your project

To add the Ping Logger SDK as a dependency to your project, include the following in
your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation(project(":foundation:logging"))
}
```

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
