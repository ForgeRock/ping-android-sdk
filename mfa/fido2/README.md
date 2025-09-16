[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Ping FIDO2

# MFA/FIDO2 Module

This module provides functionality for integrating FIDO2 multi-factor authentication into your
Android application. It simplifies the process of user registration and authentication using modern
security keys and platform authenticators. This module is specifically designed for smooth
integration with AIC Journey and DaVinci Flow.

---

## Features

- **FIDO2 Registration:** Register security keys, fingerprint sensors, face unlock, and other
  authenticators.
- **FIDO2 Authentication:** Securely authenticate users with their registered FIDO2 authenticators.
- **Easy Integration:** Streamlined API for use with AIC Journey and DaVinci Flow.
- **Standards-Based:** Built on the WebAuthn/FIDO2 standard for strong authentication.

---

## Supported Authenticators

- Platform authenticators (fingerprint, face, PIN, etc.)
- Android devices with FIDO2 support

---

## Overview

This module offers a streamlined API for handling FIDO2 interactions. It abstracts away the
complexities of the underlying FIDO2 protocols, allowing you to easily add strong authentication to
your application with `AIC` Journey and `DaVinci` Flow.

Key features include:

* **FIDO2 Registration:** Enables users to register their FIDO2 authenticators (security keys,
  fingerprint sensors, face unlock, etc.) with your application.
* **FIDO2 Authentication:** Facilitates secure user login using their registered FIDO2
  authenticators.
* **Simplified API:** Provides clear and concise functions for performing registration and
  authentication.

---

## Integration

To integrate this module into your Android project, add the following dependency to your app-level
`build.gradle` file:

```gradle
dependencies {
    implementation("com.pingidentity.sdks:fido2:<version>")
    // Add other necessary dependencies here
}
```

### Additional Dependencies for Non-Discoverable Keys

**Important:** According to the [Android Credential Manager documentation](https://developer.android.com/identity/sign-in/credential-manager), the current Credential Manager does not support non-discoverable keys.

> **Note:** If your app requires non-discoverable FIDO credentials or uses physical security keys, you should continue to use FIDO at this time.

If your application needs to support non-discoverable FIDO credentials or physical security keys, you must also add the Play Services FIDO dependency:

```gradle
dependencies {
    implementation("com.pingidentity.sdks:fido2:<version>")
    implementation("com.google.android.gms:play-services-fido:XX.X.X")
    // Add other necessary dependencies here
}
```

This ensures compatibility with:
- Non-discoverable FIDO credentials
- Legacy FIDO implementations that require Play Services

Make sure to synchronize your Gradle project after adding the dependency.

---

## Usage

### DaVinci Integration

The module provides two main classes for FIDO2 operations: `Fido2RegistrationCollector` for user
registration and `Fido2AuthenticationCollector` for user authentication. Both operations are
implemented as suspend functions and return a `Result<JsonObject>`.

#### Registration

To register a new FIDO2 authenticator, use the `register()` function of the
`Fido2RegistrationCollector`.

```kotlin
if (node is ContinueNode) {
  node.collectors.forEach { collector ->
    if (collector is Fido2RegistrationCollector) {
      when (val result = collector.register()) {
        is Success -> {
          // Registration successful, proceed to the next step in the DaVinci flow
          node = node.next()
          // Process the next node
        }
        is Failure -> {
          // Registration failed, handle the error
          val error: Throwable = result.error
          // Log or display the error message
        }
      }
    }
  }
}
```

#### Authentication

To authenticate a user using a registered FIDO2 authenticator, use the `authenticate()` function of
the `Fido2AuthenticationCollector`.

```kotlin
if (node is ContinueNode) {
  node.collectors.forEach { collector ->
    if (collector is Fido2AuthenticationCollector) {
      when (val result = collector.authenticate()) {
        is Success -> {
          // Authentication successful, proceed to the next step in the DaVinci flow
          node = node.next()
          // Process the next node
        }
        is Failure -> {
          // Authentication failed, handle the error
          val error: Throwable = result.error
          // Log or display the error message
        }
      }
    }
  }
}
```

### Journey Integration

To use FIDO2 in a Journey flow, add the FIDO2 Callbacks to your node and call the appropriate
method:

```kotlin
if (node is ContinueNode) {
  node.callbacks.forEach { collector ->
    when (collector) {
      is Fido2RegistrationCallback -> {
        val result = collector.register()
        // Handle result as above
      }
      is Fido2AuthenticationCallback -> {
        val result = collector.authenticate()
        // Handle result as above
      }
    }
  }
}
```

---

## API Reference

- **Fido2RegistrationCollector**
  - `suspend fun register(): Result<JsonObject>` — Registers a new FIDO2 authenticator.
- **Fido2AuthenticationCollector**
  - `suspend fun authenticate(): Result<JsonObject>` — Authenticates using a registered FIDO2
    authenticator.
- **Result**
  - `Success` — Operation succeeded, contains the result.
  - `Failure` — Operation failed, contains the error.

---

## Troubleshooting

- **Registration/Authentication fails:**
  - Ensure the device supports FIDO2 and has a compatible authenticator.
  - Check that your server is correctly configured for FIDO2/WebAuthn.
  - Verify that the origin domain is set up as described below.
- **App not associated with server:**
  - Make sure your Digital Asset Links JSON file is correctly hosted and accessible.
- **Other issues:**
  - Consult
    the [Ping Identity SDK documentation](https://docs.pingidentity.com/sdks/latest/sdks/use-cases/mobile-biometrics/android/)
    for more details.

---

## Associate your App with your server

To associate your server with your Android app, you need to make public, verifiable statements by
using
a [Digital Asset Links JSON file](https://docs.pingidentity.com/sdks/latest/sdks/use-cases/mobile-biometrics/android/01-prepare-assetlinks-json-file.html)

---

## Origin Domain Configuration

For enhanced security and to ensure proper communication during the FIDO2 process, you might need to
configure the allowed origin domains on your server. This configuration specifies the domains from
which your server will accept FIDO2 requests.

Refer to your server-side configuration documentation, specifically the section
on [Android Origin Domains](https://docs.pingidentity.com/sdks/latest/sdks/use-cases/mobile-biometrics/android/02-node-configurations.html#android-origin-domains)
for detailed instructions on how to set up these allowed origin domains.

Typically, this involves specifying the fully qualified domain name (FQDN) of your application's
backend server within your server's FIDO2 configuration. This step helps prevent unauthorized
entities from initiating FIDO2 registration or authentication requests against your server on behalf
of your application.

---

## Customizing Credential Requests

Both FIDO2 registration and authentication support customization of the credential request objects. This allows you to configure specific options such as timeout values, preferred authenticator types, or immediate availability preferences.

### Registration Customization

You can customize the `CreatePublicKeyCredentialRequest` by providing a lambda function to the `register()` method:

#### DaVinci Collector or AIC Callback

```kotlin
val result = collector.register { publicKeyOptions ->
  CreatePublicKeyCredentialRequest(
    requestJson = publicKeyOptions.toString(),
    preferImmediatelyAvailableCredentials = true,
    isAutoSelectAllowed = false
  )
}
```

### Authentication Customization

You can customize the `GetPublicKeyCredentialOption` by providing a lambda function to the `authenticate()` method:

#### DaVinci Collector or AIC Callback

```kotlin
val result = collector.authenticate { publicKeyOptions ->
  GetPublicKeyCredentialOption(
    requestJson = publicKeyOptions.toString(),
    preferImmediatelyAvailableCredentials = true,
    isAutoSelectAllowed = false
  )
}
```

## ⚠️ Important Migration Notice

### Deprecated Legacy ForgeRock SDK Method

**The following method from the Legacy ForgeRock SDK is no longer supported:**

`org.forgerock.android.auth.callback.WebAuthnRegistrationCallback#setResidentKeyRequirement`

### Reasons for Removal

- **Platform Alignment**: To maintain consistency with the iOS platform implementation
- **Standards Compliance**: To avoid customization or extension of the platform FIDO implementation and adhere to standard WebAuthn/FIDO2 specifications

### Impact on Existing Customers

This change may affect existing customers who are currently using WebAuthn Authentication with **username-less flow** and rely on `setResidentKeyRequirement` set to `discouraged`.

If you are currently using this method in your Legacy ForgeRock SDK implementation, please review your authentication flow and consider alternative approaches that align with the standard FIDO2 implementation provided by this module.
