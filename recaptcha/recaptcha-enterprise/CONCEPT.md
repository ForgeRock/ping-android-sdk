# ReCaptchaEnterpriseCallback Class Diagram

This class diagram shows the structure and relationships of the `ReCaptchaEnterpriseCallback` class based on the Journey Module concept and the plugin architecture.

```mermaid
classDiagram
    %% Core Interfaces and Abstract Classes
    class Action {
        <<interface>>
    }
    
    class Callback {
        <<interface>>
        +init(jsonObject: JsonObject): Callback
        +payload(): JsonObject
    }
    
    class AbstractCallback {
        <<abstract>>
        -json: JsonObject
        #init(name: String, value: JsonElement)*
        +init(jsonObject: JsonObject): Callback
        +input(vararg value: Any): JsonObject
        +payload(): JsonObject
        -update(input: JsonArray): JsonObject
    }
    
    class JourneyAware {
        <<interface>>
        +journey: Journey
    }
    
    class Journey {
        <<type alias>>
        %% Journey is a type alias for Workflow
    }
    
    %% ReCaptcha Enterprise Classes
    class ReCaptchaEnterpriseCallback {
        +reCaptchaSiteKey: String
        +journey: Journey
        +init(name: String, value: JsonElement)
        +verify(block: ReCaptchaEnterpriseConfig.() -> Unit): Result~JsonObject~
    }
    
    class ReCaptchaEnterpriseConfig {
        +reCaptchaSiteKey: String
        +recaptchaAction: RecaptchaAction
        +timeoutInMills: Long
        +recaptchaClientProvider: RecaptchaClientProvider?
        +setProvider(provider: RecaptchaClientProvider)
    }
    
    class RecaptchaClientProvider {
        <<interface>>
        +fetchClient(application: Application, siteKey: String): RecaptchaClient
        +execute(client: RecaptchaClient, action: RecaptchaAction, timeoutInMillis: Long): String?
    }
    
    class DefaultRecaptchaClientProvider {
        -config: ReCaptchaEnterpriseConfig
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
    Action <|-- Callback
    Callback <|-- AbstractCallback
    AbstractCallback <|-- ReCaptchaEnterpriseCallback
    JourneyAware <|.. ReCaptchaEnterpriseCallback
    RecaptchaClientProvider <|.. DefaultRecaptchaClientProvider
    
    %% Relationships - Composition/Aggregation
    ReCaptchaEnterpriseCallback o-- Journey : "has"
    ReCaptchaEnterpriseCallback ..> ReCaptchaEnterpriseConfig : "creates & uses"
    ReCaptchaEnterpriseConfig o-- RecaptchaClientProvider : "contains"
    ReCaptchaEnterpriseConfig o-- RecaptchaAction : "contains"
    DefaultRecaptchaClientProvider o-- ReCaptchaEnterpriseConfig : "contains"
    
    %% Relationships - Dependencies
    ReCaptchaEnterpriseCallback ..> JsonObject : "uses"
    ReCaptchaEnterpriseCallback ..> JsonElement : "uses"
    ReCaptchaEnterpriseCallback ..> Result : "returns"
    ReCaptchaEnterpriseCallback ..> ContextProvider : "uses"
    RecaptchaClientProvider ..> Application : "uses"
    RecaptchaClientProvider ..> RecaptchaClient : "returns"
    RecaptchaClientProvider ..> Recaptcha : "uses"
    ReCaptchaEnterpriseConfig --> PingDsl : "annotated with"
    
    %% Notes
    note for ReCaptchaEnterpriseCallback "Main callback class that handles\nGoogle ReCaptcha Enterprise verification\nwithin the Journey framework"
    note for ReCaptchaEnterpriseConfig "Configuration class using DSL pattern\nfor setting up ReCaptcha parameters"
    note for RecaptchaClientProvider "Strategy pattern interface for\ncustomizing ReCaptcha client behavior"
    note for AbstractCallback "Base class providing common callback\nfunctionality including JSON handling\nand input management"
```

## Architecture Overview

Based on the CONCEPT.md files and the code structure, the `ReCaptchaEnterpriseCallback` follows these architectural patterns:

### 1. Plugin Architecture (from Journey CONCEPT.md)
- **AbstractCallback**: Provides the foundation for all callbacks in the Journey module
- **JourneyAware**: Interface that allows callbacks to be aware of the Journey workflow
- **Journey**: Type alias for Workflow, representing the orchestration flow

### 2. Strategy Pattern
- **RecaptchaClientProvider**: Interface that allows different implementations for ReCaptcha client management
- **DefaultRecaptchaClientProvider**: Default implementation of the provider

### 3. DSL Configuration Pattern
- **ReCaptchaEnterpriseConfig**: Uses `@PingDsl` annotation to provide a domain-specific language for configuration
- Allows fluent configuration using lambda blocks

### 4. External Integration
- Integrates with Google's ReCaptcha Enterprise SDK
- Uses Android's Application context through ContextProvider
- Leverages Kotlin serialization for JSON handling

### Key Design Features:
1. **Extensibility**: Through the RecaptchaClientProvider interface
2. **Configuration**: DSL-based configuration for ease of use
3. **Integration**: Seamless integration with the Journey workflow
4. **Error Handling**: Uses Kotlin's Result type for robust error handling
5. **Async Support**: Suspend functions for non-blocking operations
