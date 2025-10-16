# ReCaptchaEnterpriseCallback Class Diagram

This class diagram shows the structure and relationships of the `ReCaptchaEnterpriseCallback` class based on the Journey Module concept and the plugin architecture.

```mermaid
classDiagram
    %% Core Interfaces and Abstract Classes
    class AbstractCallback {
        <<abstract>>
        #init(name: String, value: JsonElement)*
        +input(vararg value: Any): JsonObject
    }
    
    %% ReCaptcha Enterprise Classes
    class ReCaptchaEnterpriseCallback {
        +reCaptchaSiteKey: String
        +init(name: String, value: JsonElement)
        +verify(block: ReCaptchaEnterpriseConfig.() -> Unit): Result~String~
    }
    
    class ReCaptchaEnterpriseConfig {
        +recaptchaAction: RecaptchaAction
        +timeoutInMills: Long
    }
    
    %% Google ReCaptcha Classes (External Dependencies)
    class RecaptchaClient {
        <<external>>
        +execute(action: RecaptchaAction, timeoutInMillis: Long): Result~String~
    }
    
    class RecaptchaAction {
        <<external>>
        <<enumeration>>
        LOGIN
        SIGNUP
        %% ... other actions
    }
    
    class Recaptcha {
        <<external>>
        +fetchClient(application: Application, siteKey: String): RecaptchaClient$
    }
    
    %% Android Framework Classes
    class Application {
        <<external>>
    }
    
    class ContextProvider {
        <<external>>
        +context: Context$
    }
    
    %% Kotlin Serialization Classes
    class JsonObject {
        <<external>>
    }
    
    class JsonElement {
        <<external>>
    }
    
    class Result~T~ {
        <<external>>
    }
    
    %% DSL Annotation
    class PingDsl {
        <<annotation>>
    }
    
    %% Relationships - Inheritance
    AbstractCallback <|-- ReCaptchaEnterpriseCallback
    
    %% Relationships - Composition/Aggregation
    ReCaptchaEnterpriseCallback ..> ReCaptchaEnterpriseConfig : "creates & uses"
    ReCaptchaEnterpriseConfig o-- RecaptchaAction : "contains"
    
    %% Relationships - Dependencies
    ReCaptchaEnterpriseCallback ..> JsonElement : "uses"
    ReCaptchaEnterpriseCallback ..> Result : "returns"
    ReCaptchaEnterpriseCallback ..> ContextProvider : "uses"
    ReCaptchaEnterpriseCallback ..> Recaptcha : "uses"
    ReCaptchaEnterpriseConfig --> PingDsl : "annotated with"
    
    %% Notes
    note for ReCaptchaEnterpriseCallback "Main callback class that handles\nGoogle ReCaptcha Enterprise verification\nwithin the Journey framework"
    note for ReCaptchaEnterpriseConfig "Configuration class using DSL pattern\nfor setting up ReCaptcha parameters"
    note for AbstractCallback "Base class providing common callback\nfunctionality including JSON handling\nand input management"
```

## Architecture Overview

Based on the code structure, the `ReCaptchaEnterpriseCallback` follows these architectural patterns:

### 1. Plugin Architecture
- **AbstractCallback**: Provides the foundation for all callbacks in the Journey module. `ReCaptchaEnterpriseCallback` extends this class to integrate into the authentication flow.

### 2. DSL Configuration Pattern
- **ReCaptchaEnterpriseConfig**: Uses the `@PingDsl` annotation to provide a domain-specific language for configuration, allowing for fluent and type-safe setup of verification parameters using lambda blocks.

### 3. External Integration
- Integrates directly with Google's ReCaptcha Enterprise SDK (`com.google.android.recaptcha.Recaptcha`).
- Uses Android's Application context via `com.pingidentity.android.ContextProvider`.
- Leverages Kotlin serialization for handling JSON data from the server.

### Key Design Features:
1. **Simplicity**: Direct integration with the ReCaptcha SDK without extra abstraction layers.
2. **Configuration**: DSL-based configuration for ease of use.
3. **Integration**: Seamless integration with the Journey workflow via the `AbstractCallback`.
4. **Error Handling**: Uses Kotlin's `Result` type for robust error handling.
5. **Async Support**: The `verify` method is a `suspend` function, enabling non-blocking asynchronous operations.
