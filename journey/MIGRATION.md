# Migration Guide: ForgeRock Android SDK to Ping Identity Android SDK

This guide provides comprehensive instructions for migrating from the legacy ForgeRock Android SDK
to the new Ping Identity Android SDK.

## Table of Contents

- [Overview](#overview)
- [Migration Approaches](#migration-approaches)
    - [Manual Migration](#manual-migration)
    - [AI-Assisted Migration](#ai-assisted-migration)
    - [MCP Server Integration](#mcp-server-integration)
- [Migration JSON Structure](#migration-json-structure)
- [API Changes Summary](#api-changes-summary)
- [Module Organization](#module-organization)

---

## Overview

The new Ping Identity Android SDK represents a complete modernization of the ForgeRock Android SDK,
featuring:

- **Kotlin-first design** with idiomatic property access instead of getter/setter methods
- **Coroutine support** with suspend functions for asynchronous operations
- **Sealed classes** for type-safe state handling (ContinueNode, SuccessNode, FailureNode,
  ErrorNode)
- **Modular architecture** with separate modules for OIDC, external IdP, MFA, and Protect
- **Simplified APIs** that reduce boilerplate and improve developer experience

---

## Migration Approaches

### Manual Migration

For developers who prefer hands-on migration or need to understand the API changes in detail, use
the following reference tables.

#### SDK Configuration

| Legacy SDK                   | New SDK                        | Notes                                   |
|------------------------------|--------------------------------|-----------------------------------------|
| `FROptionsBuilder.build { }` | `Journey { }`                  | Simplified builder pattern              |
| `server.url`                 | `serverUrl`                    | Top-level property                      |
| `server.realm`               | `realm`                        | Top-level property                      |
| `server.cookieName`          | `cookie`                       | Renamed to `cookie`                     |
| `oauth.oauthClientId`        | `module(Oidc) { clientId }`    | Moved to Oidc module                    |
| `oauth.oauthRedirectUri`     | `module(Oidc) { redirectUri }` | Moved to Oidc module                    |
| `oauth.oauthScope`           | `module(Oidc) { scopes }`      | Changed from String to MutableSet       |
| `service.authServiceName`    | `serviceName`                  | Top-level property or used as parameter |

#### Authentication Flow

| Legacy SDK                                                   | New SDK                      | Notes                      |
|--------------------------------------------------------------|------------------------------|----------------------------|
| `FRSession.authenticate(context, journeyName, nodeListener)` | `journey.start(journeyName)` | Returns Node directly      |
| `NodeListener.onCallbackReceived(node)`                      | `is ContinueNode`            | Sealed class pattern       |
| `NodeListener.onSuccess(result)`                             | `is SuccessNode`             | Sealed class pattern       |
| `NodeListener.onException(e)`                                | `is ErrorNode`               | Sealed class pattern       |
| N/A                                                          | `is FailureNode`             | New explicit failure state |

#### Callbacks - Core (journey module)

| Callback Type                  | Legacy SDK                                                                                                  | New SDK                                                                                            | Key Changes          |
|--------------------------------|-------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|----------------------|
| **TextInputCallback**          | `node.getCallback(TextInputCallback::class.java)`<br>`callback.getPrompt()`<br>`callback.setText("value")`  | `node.callback as TextInputCallback`<br>`callback.prompt`<br>`callback.text = "value"`             | Property access      |
| **PasswordCallback**           | `node.getCallback(PasswordCallback::class.java)`<br>`callback.getPrompt()`<br>`callback.setPassword(chars)` | `node.callback as PasswordCallback`<br>`callback.prompt`<br>`callback.password = chars`            | Property access      |
| **NameCallback**               | `node.getCallback(NameCallback::class.java)`<br>`callback.getPrompt()`<br>`callback.setName("name")`        | `node.callback as NameCallback`<br>`callback.prompt`<br>`callback.name = "name"`                   | Property access      |
| **ChoiceCallback**             | `callback.getChoices()`<br>`callback.getDefaultChoice()`<br>`callback.setSelectedIndex(0)`                  | `callback.choices`<br>`callback.defaultChoice`<br>`callback.selectedIndex = 0`                     | Property access      |
| **ConfirmationCallback**       | `callback.getOptions()`<br>`callback.setSelectedIndex(0)`                                                   | `callback.options`<br>`callback.selectedIndex = 0`                                                 | Property access      |
| **TextOutputCallback**         | `callback.getMessage()`<br>`callback.getMessageType()`                                                      | `callback.message`<br>`callback.messageType`                                                       | Read-only properties |
| **PollingWaitCallback**        | `callback.getWaitTime()`<br>`callback.getMessage()`                                                         | `callback.waitTime`<br>`callback.message`                                                          | Property access      |
| **KbaCreateCallback**          | `callback.getPredefinedQuestions()`<br>`callback.setSelectedQuestion(q)`<br>`callback.setSelectedAnswer(a)` | `callback.predefinedQuestions`<br>`callback.selectedQuestion = q`<br>`callback.selectedAnswer = a` | Property access      |
| **TermsAndConditionsCallback** | `callback.getTerms()`<br>`callback.getVersion()`<br>`callback.setAccept(true)`                              | `callback.terms`<br>`callback.version`<br>`callback.accept = true`                                 | Property access      |

#### Callbacks - Attribute Inputs

| Callback Type                     | Legacy SDK                                                                     | New SDK                                                            | Key Changes     |
|-----------------------------------|--------------------------------------------------------------------------------|--------------------------------------------------------------------|-----------------|
| **BooleanAttributeInputCallback** | `callback.getName()`<br>`callback.getValue()`<br>`callback.setValue(true)`     | `callback.name`<br>`callback.value`<br>`callback.value = true`     | Property access |
| **StringAttributeInputCallback**  | `callback.getName()`<br>`callback.getPrompt()`<br>`callback.setValue("value")` | `callback.name`<br>`callback.prompt`<br>`callback.value = "value"` | Property access |
| **NumberAttributeInputCallback**  | `callback.getName()`<br>`callback.getPrompt()`<br>`callback.setValue(42.0)`    | `callback.name`<br>`callback.prompt`<br>`callback.value = 42.0`    | Property access |

#### Callbacks - External IdP (external-idp module)

| Callback Type         | Legacy SDK                                                                                                                                   | New SDK                                                                                                     | Key Changes                                      |
|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| **SelectIdPCallback** | `org.forgerock.android.auth.callback.SelectIdPCallback`<br>`callback.getProviders()`<br>`callback.setValue(provider.provider)`               | `com.pingidentity.idp.journey.SelectIdpCallback`<br>`callback.providers`<br>`callback.value = provider`     | Moved to external-idp module<br>Property access  |
| **IdPCallback**       | `org.forgerock.android.auth.callback.IdPCallback`<br>`callback.getProvider()`<br>`callback.setToken(token)`<br>`callback.setTokenType(type)` | `com.pingidentity.idp.journey.IdpCallback`<br>`callback.provider`<br>`callback.signIn(context, idpHandler)` | Moved to external-idp module<br>Suspend function |

#### Callbacks - Device Binding (mfa/binding module)

| Callback Type                     | Legacy SDK                                                                                                                                                       | New SDK                                                                                                                                   | Key Changes                                                          |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| **DeviceBindingCallback**         | `org.forgerock.android.auth.callback.DeviceBindingCallback`<br>`callback.getUserId()`<br>`callback.getChallenge()`<br>`callback.bind(context, activity)`         | `com.pingidentity.device.binding.journey.DeviceBindingCallback`<br>`callback.userId`<br>`callback.challenge`<br>`callback.bind()`         | Moved to mfa/binding module<br>Suspend function<br>No context needed |
| **DeviceSigningVerifierCallback** | `org.forgerock.android.auth.callback.DeviceSigningVerifierCallback`<br>`callback.getUserId()`<br>`callback.getChallenge()`<br>`callback.sign(context, activity)` | `com.pingidentity.device.binding.journey.DeviceSigningVerifierCallback`<br>`callback.userId`<br>`callback.challenge`<br>`callback.sign()` | Moved to mfa/binding module<br>Suspend function<br>No context needed |

#### Callbacks - FIDO/WebAuthn (mfa/fido module)

| Callback Type                      | Legacy SDK                                                                                                                                                      | New SDK                                                                                                                                          | Key Changes                                                                                                   |
|------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| **WebAuthnRegistrationCallback**   | `org.forgerock.android.auth.callback.WebAuthnRegistrationCallback`<br>`callback.register(activity, node) { result ->`<br>`  when (result) { ... }`<br>`}`       | `com.pingidentity.fido.journey.FidoRegistrationCallback`<br>`try {`<br>`  callback.register(activity)`<br>`} catch (e: Exception) { ... }`       | Renamed to FidoRegistrationCallback<br>Moved to mfa/fido module<br>Suspend function with exception handling   |
| **WebAuthnAuthenticationCallback** | `org.forgerock.android.auth.callback.WebAuthnAuthenticationCallback`<br>`callback.authenticate(activity, node) { result ->`<br>`  when (result) { ... }`<br>`}` | `com.pingidentity.fido.journey.FidoAuthenticationCallback`<br>`try {`<br>`  callback.authenticate(activity)`<br>`} catch (e: Exception) { ... }` | Renamed to FidoAuthenticationCallback<br>Moved to mfa/fido module<br>Suspend function with exception handling |

#### Callbacks - PingOne Protect (protect module)

| Callback Type                        | Legacy SDK                                                                                                                                          | New SDK                                                                                                                                                | Key Changes                                                                    |
|--------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| **PingOneProtectInitializeCallback** | `org.forgerock.android.auth.callback.PingOneProtectInitializeCallback`<br>`callback.start(context) { result ->`<br>`  when (result) { ... }`<br>`}` | `com.pingidentity.protect.journey.PingOneProtectInitializeCallback`<br>`try {`<br>`  callback.initialize(context)`<br>`} catch (e: Exception) { ... }` | Moved to protect module<br>Renamed start() to initialize()<br>Suspend function |
| **PingOneProtectEvaluationCallback** | `org.forgerock.android.auth.callback.PingOneProtectEvaluationCallback`<br>`callback.getData() { result ->`<br>`  when (result) { ... }`<br>`}`      | `com.pingidentity.protect.journey.PingOneProtectEvaluationCallback`<br>`try {`<br>`  callback.evaluate()`<br>`} catch (e: Exception) { ... }`          | Moved to protect module<br>Renamed getData() to evaluate()<br>Suspend function |

---

### AI-Assisted Migration

For faster migration with AI assistance, you can leverage the structured migration data provided in
the `migration.json` file.

#### Step 1: Download the Migration JSON

Download the migration reference file from:

```
https://github.com/ForgeRock/ping-android-sdk/blob/develop/journey/migration.json
```

Or access it locally at:

```
journey/migration.json
```

#### Step 2: Prepare Your AI Context

Feed the `migration.json` file to your AI assistant (e.g., ChatGPT, Claude, GitHub Copilot, etc.)
along with your legacy code.

#### Step 3: Use Migration Prompts

Here are example prompts you can use with AI assistants:

##### General Migration

```
I have ForgeRock Android SDK code that needs to be migrated to the new Ping Identity Android SDK.
Please use the attached migration.json file as reference to transform my code.

[Paste your legacy code here]
```

##### Configuration Migration

```
Migrate this FROptionsBuilder configuration to the new Journey builder syntax using the 
migration.json reference. Ensure OAuth properties are moved to the Oidc module.

[Paste your FROptionsBuilder code here]
```

##### Authentication Flow Migration

```
Convert this FRSession.authenticate implementation with NodeListener to the new Journey.start() 
pattern with sealed classes. Use migration.json as reference.

[Paste your authentication code here]
```

##### Callback Migration

```
Migrate these callback handlers from ForgeRock SDK to Ping SDK. Replace getter/setter methods 
with property access and update callback-based patterns to suspend functions where applicable. 
Use migration.json as reference.

[Paste your callback handling code here]
```

##### Module-Specific Migration

```
This code uses [IdPCallback/DeviceBindingCallback/WebAuthnRegistrationCallback]. Migrate it to 
the new SDK, ensuring the correct module import and API updates according to migration.json.

[Paste your code here]
```

##### Complete File Migration

```
Perform a complete migration of this file from ForgeRock Android SDK to Ping Identity Android SDK.
Update imports, configuration, authentication flow, and all callbacks. Use migration.json as reference.

[Paste your entire file here]
```

##### Error Handling Migration

```
Update this legacy callback-based error handling to use the new suspend function pattern with 
try-catch blocks. Reference migration.json for the correct approach.

[Paste your error handling code here]
```

##### Incremental Migration

```
I'm migrating incrementally. Please migrate only the [specific component/callback/feature] 
in this code to the new Ping SDK API, using migration.json as reference.

[Paste your code here]
```

#### Step 4: Review and Test

Always review AI-generated code carefully:

- Verify imports point to correct modules
- Ensure property access replaces getter/setter methods
- Confirm suspend functions are used correctly
- Test authentication flows thoroughly
- Validate callback handling logic

---

## MCP Server Integration (TBD)

For the most seamless migration experience, configure your AI agent with the Model Context
Protocol (MCP) Server. This allows your AI assistant to directly access migration resources without
manual file uploads.

---

## Migration JSON Structure

The `migration.json` file contains structured migration examples to facilitate both manual and
AI-assisted migrations.

### File Structure

```json
{
  "migration_examples": [
    {
      "id": "unique_identifier",
      "description": "Brief description of what is being migrated",
      "notes": "Additional context, warnings, or important changes",
      "legacy_interface": {
        "language": "Kotlin",
        "configuration_name": "Legacy component name",
        "snippet": "Code example from legacy SDK"
      },
      "new_interface": {
        "language": "Kotlin",
        "configuration_name": "New component name",
        "snippet": "Equivalent code in new SDK"
      },
      "key_mapping_summary": [
        {
          "legacy_key": "Old API method/property",
          "new_key": "New API method/property",
          "notes": "Explanation of the change"
        }
      ]
    }
  ]
}
```

### Field Descriptions

| Field                                   | Description                                                                               |
|-----------------------------------------|-------------------------------------------------------------------------------------------|
| **id**                                  | Unique identifier for the migration example (e.g., `forgerock_callback_textinput_003`)    |
| **description**                         | Human-readable description of what aspect is being migrated                               |
| **notes**                               | Important context about behavioral changes, new requirements, or migration considerations |
| **legacy_interface**                    | Contains the old SDK implementation details                                               |
| **legacy_interface.language**           | Programming language (typically "Kotlin")                                                 |
| **legacy_interface.configuration_name** | Name of the legacy component or pattern                                                   |
| **legacy_interface.snippet**            | Actual code example from the legacy SDK                                                   |
| **new_interface**                       | Contains the new SDK implementation details                                               |
| **new_interface.language**              | Programming language (typically "Kotlin")                                                 |
| **new_interface.configuration_name**    | Name of the new component or pattern                                                      |
| **new_interface.snippet**               | Equivalent code in the new SDK                                                            |
| **key_mapping_summary**                 | Array of specific API mappings                                                            |
| **key_mapping_summary[].legacy_key**    | Old API method, property, or pattern                                                      |
| **key_mapping_summary[].new_key**       | New API method, property, or pattern                                                      |
| **key_mapping_summary[].notes**         | Explanation of what changed and why                                                       |

### Example Migration Entry

Here's a complete example showing the TextInputCallback migration:

```json
{
  "id": "forgerock_callback_textinput_003",
  "description": "Migration of TextInputCallback from legacy ForgeRock SDK to the new Ping Identity SDK",
  "notes": "Legacy SDK used explicit getCallback() with class parameter. New SDK uses direct type casting and property access.",
  "legacy_interface": {
    "language": "Kotlin",
    "configuration_name": "TextInputCallback (Legacy)",
    "snippet": "val callback = node.getCallback(TextInputCallback::class.java)\nval prompt = callback.getPrompt()\ncallback.setText(\"user input\")"
  },
  "new_interface": {
    "language": "Kotlin",
    "configuration_name": "TextInputCallback (New)",
    "snippet": "val callback = node.callback as TextInputCallback\nval prompt = callback.prompt\ncallback.text = \"user input\""
  },
  "key_mapping_summary": [
    {
      "legacy_key": "getCallback(TextInputCallback::class.java)",
      "new_key": "callback as TextInputCallback",
      "notes": "Direct type casting instead of getCallback method"
    },
    {
      "legacy_key": "getPrompt()",
      "new_key": "prompt",
      "notes": "Property access instead of getter method"
    },
    {
      "legacy_key": "setText(\"user input\")",
      "new_key": "text = \"user input\"",
      "notes": "Property assignment instead of setter method"
    }
  ]
}
```

### How to Use the Migration JSON

1. **For Manual Migration**: Search for your current API usage in the `legacy_interface.snippet` or
   `legacy_key` fields, then apply the corresponding changes from `new_interface.snippet` or
   `new_key`.

2. **For AI-Assisted Migration**: Provide the entire `migration.json` file as context to your AI
   assistant. The structured format allows AI to understand the transformation patterns and apply
   them to your code.

3. **For Pattern Recognition**: The `key_mapping_summary` provides granular API mappings that can be
   used to create automated migration scripts or IDE plugins.

4. **For Documentation**: Use the `description` and `notes` fields to understand why changes were
   made and what behavioral differences to expect.

---

## API Changes Summary

### Key Architectural Changes

1. **Property Access**: Getter/setter methods (`getName()`, `setName()`) replaced with Kotlin
   properties (`name`, `name = value`)
2. **Suspend Functions**: Callback-based async operations replaced with suspend functions and
   coroutines
3. **Sealed Classes**: `NodeListener` callbacks replaced with sealed class hierarchy (
   `ContinueNode`, `SuccessNode`, `FailureNode`, `ErrorNode`)
4. **Modular Structure**: Features split into focused modules (journey, external-idp, mfa/binding,
   mfa/fido, protect)
5. **Context Reduction**: Many APIs no longer require Context parameter
6. **Exception Handling**: Try-catch pattern replaces callback-based error handling for async
   operations

### Breaking Changes

- **Package names**: All packages changed from `org.forgerock.android.auth.*` to
  `com.pingidentity.*`
- **Class names**: Some callbacks renamed (e.g., `WebAuthnRegistrationCallback` →
  `FidoRegistrationCallback`)
- **Module dependencies**: Need to add specific module dependencies for IdP, MFA, and Protect
  features
- **OAuth configuration**: Moved from top-level to `module(Oidc)` block
- **Scope format**: Changed from space-separated string to `mutableSetOf<String>`

---

## Module Organization

The new SDK is organized into focused modules:

| Module                   | Purpose                           | Key Callbacks                                                      |
|--------------------------|-----------------------------------|--------------------------------------------------------------------|
| **journey**              | Core authentication and callbacks | TextInput, Password, Name, Choice, Confirmation, etc.              |
| **foundation/oidc**      | OAuth/OIDC functionality          | Oidc configuration module                                          |
| **external-idp**         | Social login providers            | SelectIdpCallback, IdpCallback                                     |
| **mfa/binding**          | Device binding for MFA            | DeviceBindingCallback, DeviceSigningVerifierCallback               |
| **mfa/fido**             | FIDO2/WebAuthn authentication     | FidoRegistrationCallback, FidoAuthenticationCallback               |
| **protect**              | PingOne Protect risk signals      | PingOneProtectInitializeCallback, PingOneProtectEvaluationCallback |
| **recaptcha-enterprise** | ReCaptcha Enterprise support      | ReCaptcha callbacks                                                |

### Gradle Dependencies

Update your `build.gradle.kts` to include required modules:

```kotlin
dependencies {
    // Core journey
    implementation("com.pingidentity.sdks:journey:1.0.0")

    // OIDC support
    implementation("com.pingidentity.sdks:oidc:1.0.0")

    // External IdP (social login)
    implementation("com.pingidentity.sdks:external-idp:1.0.0")

    // Device binding MFA
    implementation("com.pingidentity.sdks:mfa-binding:1.0.0")

    // FIDO2/WebAuthn
    implementation("com.pingidentity.sdks:mfa-fido:1.0.0")

    // PingOne Protect
    implementation("com.pingidentity.sdks:protect:1.0.0")
}
```

---

## Additional Resources

- [Ping Identity Android SDK Documentation](https://docs.pingidentity.com/sdks/android/)
- [Migration JSON Reference](migration.json)
- [Sample Applications](samples/)
- [GitHub Repository](https://github.com/ForgeRock/ping-android-sdk)

---

## Support

If you encounter issues during migration:

1. Review the migration.json file for specific API mappings
2. Check the sample applications in the `samples/` directory
3. Consult the API documentation
4. Open an issue on GitHub with your migration question

---

**Last Updated**: November 27, 2025

