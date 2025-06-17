<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Ping Identity Logo" width="200">
  </a>
  <hr/>
</p>

# Journey Plugin

The `Journey-Plugin` module provides a mechanism to extend the functionality of the Journey Module by allowing you to
implement custom callbacks and register them for use within the Journey workflow. This enables you to inject specific
logic and data handling at various stages of the user journey.

## Key Features

1. **Callback Implementation:** You can implement your own callback logic by creating classes that adhere to
   the `Callback` interface. This allows for highly customized behavior tailored to your application's needs.

2. **Callback Registration:** The module provides a centralized registry (`CallbackRegistry`) where you can register
   your implemented callbacks. This registration associates a Callback type (a string) with your callback
   implementation, making it discoverable and usable by the Journey Module.

3. **Loose Coupling:** By using the plugin mechanism, your custom logic remains separate from the core Journey Module,
   promoting modularity and maintainability.

## Getting Started

### 1. Add Dependency

To use the `Journey-Plugin` module in your project, you need to add it as a dependency, add the following line to your
module's `build.gradle` file (usually the `app/build.gradle` or a feature module's `build.gradle`):

```gradle
dependencies {
    ...
    implementation("com.pingidentity.sdks:android:<latest_version>") 
    implementation("com.pingidentity.sdks:journey-plugin:<latest_version>") 
}
```

### 2. Implement the `Callback` Interface

Create a class that implements the `Callback` interface. This interface defines the contract for your custom callbacks.

```kotlin
import com.google.gson.JsonObject
import com.example.foundation.android.journey.Action // Assuming Action interface is in this package

interface Callback : Action {
    /**
     * Initializes the callback with the provided JSON object.
     * @param jsonObject The JSON object containing the callback configuration.
     * @return The initialized Callback instance.
     */
    fun init(jsonObject: JsonObject): Callback

    /**
     * Returns the payload of the callback.
     */
    fun payload(): JsonObject
}
```

**Explanation of the `Callback` Interface:**

* **`Callback : Action`**: Your callback interface extends an `Action` interface (the definition of `Action` is not
  provided in the original text, but it likely represents a base interface for actions within the Journey Module).
* **`fun init(jsonObject: JsonObject): Callback`**: This function is responsible for initializing your callback instance
  using a JSON object provided by the Journey Module. This JSON object will likely contain configuration parameters
  specific to this callback instance. The function should return the initialized `Callback` object.
* **`fun payload(): JsonObject`**: This function should return a `JsonObject` representing the output or data payload of
  your callback after it has been executed. This payload will likely be used by subsequent steps in the Journey
  workflow.
* **`AbstractCallback : Callback`**: Provides a base implementation of the `Callback` interface. This class can
  contain common logic or properties that all callbacks might share, reducing code duplication.
*

**Example Implementation:**

Let's say you want to create a callback that collects the username.

```kotlin

class NameCallback : AbstractCallback() {
    var prompt: String = ""
        private set

    //Input
    var name: String = ""

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content
        }
    }

    override fun payload() = input(name)

}
```

### 3. Register Your Callback

To make your custom callback available to the Journey Module, you need to register it with the `CallbackRegistry`. This
is typically done during your module's startup process. The Journey Module likely provides a mechanism to initialize
modules.

**Example using `ModuleInitializer`:**

```kotlin
import com.example.foundation.android.journey.CallbackRegistry
import com.example.foundation.android.journey.ModuleInitializer

class CollectorRegistry : ModuleInitializer() {

    /**
     * Initializes the module by registering custom callbacks.
     */
    override fun initialize() {
        CallbackRegistry.register("ChoiceCallback", ::ChoiceCallback)
        CallbackRegistry.register("NameCallback", ::NameCallback) // Example from the original text
        // Register other custom callbacks here
    }
}
```

**Explanation:**

* **`CollectorRegistry : ModuleInitializer()`**: This class implements the `ModuleInitializer` interface (assuming this
  interface exists in the Journey Module).
* **`override fun initialize()`**: This function is called during the module's initialization.
* **`CallbackRegistry.register("CHOICE_CALLBACK", ::ChoiceCallback)`**: This line registers your `ChoiceCallback`
  implementation.
    * `"ChoiceCallback"`: This is a unique identifier (a string) that the Journey Module will use to invoke this
      specific callback.
    * `::ChoiceCallback`: This is a class reference to your `ChoiceCallback` implementation. The `CallbackRegistry`
      likely uses this reference to create instances of your callback when needed.

**Important Considerations:**

* **Callback Name:** Ensure that the identifier you use when registering your callback matches the one Callback name
  from server.
* **Module Initialization:** Ensure you register the ModuleInitializer in the AndroidManifest.xml file.
```xml
<provider
        android:name=".journey.CollectorRegistry"
        android:authorities="${applicationId}.collectorRegistry"
        android:enabled="true"
        android:exported="false"/>
```
* **Testing:** Thoroughly test your custom callbacks to ensure they function as expected within the Journey workflow.

By following these steps, you can effectively use the `Journey-Plugin` module to extend the Journey Module with your own
custom callback logic, making your application more flexible and adaptable to specific user journey requirements.