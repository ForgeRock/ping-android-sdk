[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# DaVinci Plugin Module

The `DaVinci-Plugin` module provides a mechanism to extend the functionality of the DaVinci Module by allowing you to
implement custom collectors and register them for use within DaVinci flows. This enables you to inject specific
logic and data handling for form fields and interactions at various stages of the authentication flow.

## Key Features

1. **Collector Implementation:** You can implement your own collector logic by creating classes that adhere to
   the `Collector` interface. This allows for highly customized behavior tailored to your application's needs.

2. **Collector Registration:** The module provides a centralized registry (`CollectorFactory`) where you can register
   your implemented collectors. This registration associates a collector type (a string matching the server's field
   `inputType` or `type`) with your collector implementation, making it discoverable and usable by the DaVinci Module.

3. **DaVinci Awareness:** Collectors that need access to the DaVinci workflow instance can implement the `DaVinciAware`
   interface. The `DaVinci` instance will be automatically injected before `init` is called.

4. **Self-Submittable Collectors:** Collectors that can trigger a form submission on their own (e.g., a button) can
   implement the `Submittable` interface to declare their event type.

5. **Loose Coupling:** By using the plugin mechanism, your custom logic remains separate from the core DaVinci Module,
   promoting modularity and maintainability.

## Getting Started

### Prerequisites

- PingOne DaVinci
- Android API level 29 or higher

### 1. Add Dependency

To integrate this module into your Android project, include the following dependency in
your `build.gradle.kts` (or `build.gradle`) file:

```kotlin
dependencies {
    ...
    implementation("com.pingidentity.sdks:davinci:<version>")
    implementation("com.pingidentity.sdks:davinci-plugin:<version>")
}
```

Replace `<version>` with the latest available version of the SDK from the Maven repository. Ensure your
project's `repositories` block includes Maven Central or the Ping Identity Maven repository.

### 2. Implement the `Collector` Interface

Create a class that implements the `Collector` interface. This interface defines the contract for your custom
collectors.

```kotlin
interface Collector<T> : Action {
    /**
     * Returns a unique identifier for this collector instance.
     */
    fun id(): String

    /**
     * Initializes the Collector with the given JsonObject from the server response.
     * @param input The JsonObject used to initialize the Collector.
     * @return The initialized Collector instance.
     */
    fun init(input: JsonObject): Collector<T>

    /**
     * Returns the payload to post to the server.
     * When null, the field will not be included in the submission.
     */
    fun payload(): T?
}
```

**Explanation of the `Collector` Interface:**

* **`Collector<T> : Action`**: Your collector interface extends the `Action` interface, which is the base interface
  for actions within the DaVinci Module.
* **`fun id(): String`**: Returns a unique identifier for the collector. Defaults to a random UUID, but field-based
  collectors typically return their server-defined `key`.
* **`fun init(input: JsonObject): Collector<T>`**: Called with the JSON object from the server to initialize the
  collector's properties (e.g., label, required flag, default value). Returns the initialized collector instance.
* **`fun payload(): T?`**: Returns the value to be posted back to the server. Returning `null` excludes the field
  from the submission payload.

**Example Implementation:**

Let's say you want to create a collector that collects a custom token value.

```kotlin
import com.pingidentity.davinci.plugin.Collector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class TokenCollector : Collector<String> {
    var label: String = ""
        private set

    // Input value set by the developer or user
    var value: String = ""

    override fun init(input: JsonObject): Collector<String> {
        label = input["label"]?.jsonPrimitive?.content ?: ""
        return this
    }

    override fun payload(): String? = value.takeIf { it.isNotEmpty() }
}
```

### 3. Register Your Collector

To make your custom collector available to the DaVinci Module, register it with the `CollectorFactory`. This is
typically done during your module's startup process using `ModuleInitializer`.

**Example using `ModuleInitializer`:**

```kotlin
import com.pingidentity.android.ModuleInitializer
import com.pingidentity.davinci.plugin.CollectorFactory

class CollectorRegistry : ModuleInitializer() {

    /**
     * Initializes the module by registering custom collectors.
     */
    override fun initialize() {
        CollectorFactory.register("TOKEN", ::TokenCollector)
        // Register other custom collectors here
    }
}
```

**Explanation:**

* **`CollectorRegistry : ModuleInitializer()`**: This class extends `ModuleInitializer` so it is invoked automatically
  during application startup.
* **`override fun initialize()`**: This function is called during the module's initialization phase.
* **`CollectorFactory.register("TOKEN", ::TokenCollector)`**: Registers your `TokenCollector` implementation.
    * `"TOKEN"`: The string identifier that matches the server-side field `inputType` or `type` value.
    * `::TokenCollector`: A class reference used by `CollectorFactory` to create new instances on demand.

**Important Considerations:**

* **Collector Type:** Ensure the type string you use when registering matches the `inputType` (or `type`) field
  value returned by the DaVinci server for the corresponding form field.
* **Module Initialization:** Register the `ModuleInitializer` in the `AndroidManifest.xml` file.

```xml
<provider
        android:name=".davinci.CollectorRegistry"
        android:authorities="${applicationId}.collectorRegistry"
        android:enabled="true"
        android:exported="false"/>
```

* **DaVinci Awareness:** If your collector needs access to the DaVinci workflow (e.g., to trigger sub-flows),
  implement the `DaVinciAware` interface. The `DaVinci` instance will be injected automatically before `init` is called.

```kotlin
import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.davinci.plugin.DaVinciAware
import kotlinx.serialization.json.JsonObject

class SubFlowCollector : Collector<Nothing>, DaVinciAware {
    override lateinit var davinci: DaVinci

    override fun init(input: JsonObject): Collector<Nothing> {
        // davinci is available here
        return this
    }
}
```

* **Self-Submittable Collectors:** If your collector should trigger a form submission on its own (e.g., a button
  click), implement the `Submittable` interface and return the appropriate event type.

```kotlin
import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.davinci.plugin.Submittable
import kotlinx.serialization.json.JsonObject

class ActionCollector : Collector<Nothing>, Submittable {
    override fun init(input: JsonObject): Collector<Nothing> = this

    override fun eventType(): String = "action"
}
```

* **Testing:** Thoroughly test your custom collectors to ensure they function as expected within DaVinci flows.

By following these steps, you can effectively use the `DaVinci-Plugin` module to extend the DaVinci Module with your
own custom collector logic, making your application more flexible and adaptable to specific authentication flow
requirements.

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.
