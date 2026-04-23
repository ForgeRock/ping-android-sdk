[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Android Module

> **Internal module.** This module is not intended for direct use by application developers. It provides Android
> platform bootstrapping utilities consumed by other modules within the Ping Android SDK.

## Getting Started

### Prerequisites

- Android API level 29 or higher

### Installation

To integrate this module into your Android project, include the following dependency in your `build.gradle.kts` (or `build.gradle`) file:

```kotlin
    implementation("com.pingidentity.sdks:android:<version>")
```

## Overview

The `android` module handles application-level initialization and provides shared Android infrastructure:

- **`ContextProvider`** — A singleton that holds a reference to the application `Context` and the current `Activity`,
  making them accessible across the SDK without requiring explicit passing.
- **`ContextInitializer`** — An [AndroidX App Startup](https://developer.android.com/topic/libraries/app-startup)
  `Initializer` that automatically populates `ContextProvider` at app launch and tracks the current activity via
  `ActivityLifecycleCallbacks`. No manual setup is required.
- **`ModuleInitializer`** — An abstract `ContentProvider`-based class that other SDK modules extend to register their
  components (e.g., collectors, callbacks) automatically at startup via `AndroidManifest.xml` declarations.

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.
