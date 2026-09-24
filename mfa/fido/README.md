[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# FIDO Module

This module provides functionality for integrating FIDO multi-factor authentication into your
Android application. It simplifies the process of user registration and authentication using modern
security keys and platform authenticators. This module is specifically designed for smooth
integration with AIC Journey and DaVinci Flow.

## Getting Started

### Prerequisites

- PingOne DaVinci or Ping Advanced Identity Cloud / PingAM [Supported Versions](https://support.pingidentity.com/s/article/Ping-Identity-EOL-Tracker)
- Android API level 29 or higher

### Installation

To integrate this module into your Android project, include the following dependency in
your `build.gradle.kts` (or `build.gradle`) file:

```kotlin
dependencies {
    implementation("com.pingidentity.sdks:fido:<version>")
    // Add other necessary dependencies here
}
```

Replace `<version>` with the latest available version of the SDK from the Maven repository. Ensure your
project's `repositories` block includes Maven Central or the Ping Identity Maven repository.

### Additional Dependencies for API 33 and Older

**Important:** For devices running Android API 33 (Android 13) and older, you must add the Credentials Play Services Auth dependency to ensure proper FIDO functionality:

```gradle
dependencies {
    implementation("com.pingidentity.sdks:fido:<version>")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    // Add other necessary dependencies here
}
```

This dependency is required because:
- Android Credential Manager requires additional support libraries for API 33 and below
- It provides backward compatibility for FIDO authentication on older Android versions

### Additional Dependencies for Non-Discoverable Keys

**Important:** According to the [Android Credential Manager documentation](https://developer.android.com/identity/sign-in/credential-manager), the current Credential Manager does not support non-discoverable keys.

> **Note:** If your app requires non-discoverable FIDO credentials or uses physical security keys, you should continue to use FIDO at this time.

If your application needs to support non-discoverable FIDO credentials or physical security keys, you must also add the Play Services FIDO dependency:

```gradle
dependencies {
    implementation("com.pingidentity.sdks:fido:<version>")
    implementation("com.google.android.gms:play-services-fido:XX.X.X")
    // Add other necessary dependencies here
}
```

This ensures compatibility with:
- Non-discoverable FIDO credentials
- Legacy FIDO implementations that require Play Services

Make sure to synchronize your Gradle project after adding the dependency.


## Supported Authenticators

- Platform authenticators (fingerprint, face, PIN, etc.)
- Android devices with FIDO support


## Overview

This module offers a streamlined API for handling FIDO interactions. It abstracts away the
complexities of the underlying FIDO protocols, allowing you to easily add strong authentication to
your application with `AIC` Journey and `DaVinci` Flow.

Key features include:

* **FIDO Registration:** Enables users to register their FIDO authenticators (security keys,
  fingerprint sensors, face unlock, etc.) with your application.
* **FIDO Authentication:** Facilitates secure user login using their registered FIDO
  authenticators.
* **Simplified API:** Provides clear and concise functions for performing registration and
  authentication.

## Usage

### DaVinci Integration

The module provides two main classes for FIDO operations: `FidoRegistrationCollector` for user
registration and `FidoAuthenticationCollector` for user authentication. Both operations are
implemented as suspend functions and return a `Result<JsonObject>`.

#### Registration

To register a new FIDO authenticator, use the `register()` function of the
`FidoRegistrationCollector`.

```kotlin
if (node is ContinueNode) {
  node.collectors.forEach { collector ->
    if (collector is FidoRegistrationCollector) {
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

To authenticate a user using a registered FIDO authenticator, use the `authenticate()` function of
the `FidoAuthenticationCollector`.

```kotlin
if (node is ContinueNode) {
  node.collectors.forEach { collector ->
    if (collector is FidoAuthenticationCollector) {
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

To use FIDO in a Journey flow, add the FIDO Callbacks to your node and call the appropriate
method:

```kotlin
if (node is ContinueNode) {
  node.callbacks.forEach { collector ->
    when (collector) {
      is FidoRegistrationCallback -> {
        val result = collector.register()
        // Handle result as above
      }
      is FidoAuthenticationCallback -> {
        val result = collector.authenticate()
        // Handle result as above
      }
    }
  }
}
```

---

## API Reference

- **FidoRegistrationCollector**
  - `suspend fun register(): Result<JsonObject>` — Registers a new FIDO authenticator.
- **FidoAuthenticationCollector**
  - `suspend fun authenticate(): Result<JsonObject>` — Authenticates using a registered FIDO
    authenticator.
- **FidoAuthenticationCallback** (Journey)
  - `suspend fun authenticate(): Result<JsonObject>` — Authenticates using a registered FIDO
    authenticator.
  - `suspend fun pendingAuthenticate(block: FidoAuthenticateCustomizer.() -> Unit = {}):
    Result<FidoPendingAuthentication>` — Builds a pending
    request for conditional mediation (autofill with passkeys) instead of launching the modal
    ceremony. See "Conditional mediation (autofill with passkeys)" below.
- **FidoPendingAuthentication**
  - `val request: PendingGetCredentialRequest` — The request to attach to the username field:
    `view.pendingGetCredentialRequest = pending.request`. **View-only** — the androidx extension
    has no Compose equivalent; wrap an `EditText` with `AndroidView` in Compose apps.
  - `suspend fun await(): Result<JsonObject>` — The assertion in the same shape `authenticate()`
    returns. Stays suspended if the user dismisses the suggestions (the pending path propagates
    no errors) — keep the modal fallback visible and treat non-completion as the fallback
    trigger.
  - `fun cancel()` — Releases a suspended `await()` with a `CancellationException`-bearing
    failure (e.g. when the screen goes away).
- **isConditionalMediationSupported** — `true` only where the OS can deliver pending credential
  requests: Android 15 (API 35) or above, or an API 34 preview build. Gate the conditional
  affordance on this value; `pendingAuthenticate` fails fast with
  `GetCredentialUnsupportedException` below it.
- **Result**
  - `Success` — Operation succeeded, contains the result.
  - `Failure` — Operation failed, contains the error.

---

## Troubleshooting

- **Registration/Authentication fails:**
  - Ensure the device supports FIDO and has a compatible authenticator.
  - Check that your server is correctly configured for FIDO/WebAuthn.
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

For enhanced security and to ensure proper communication during the FIDO process, you might need to
configure the allowed origin domains on your server. This configuration specifies the domains from
which your server will accept FIDO requests.

Refer to your server-side configuration documentation, specifically the section
on [Android Origin Domains](https://docs.pingidentity.com/sdks/latest/sdks/use-cases/mobile-biometrics/android/02-node-configurations.html#android-origin-domains)
for detailed instructions on how to set up these allowed origin domains.

Typically, this involves specifying the fully qualified domain name (FQDN) of your application's
backend server within your server's FIDO configuration. This step helps prevent unauthorized
entities from initiating FIDO registration or authentication requests against your server on behalf
of your application.

---

## Customizing Credential Requests

Both FIDO registration and authentication support customization of the credential request objects. This allows you to configure specific options such as timeout values, preferred authenticator types, or immediate availability preferences using a DSL-style customizer.

### Registration Customization

You can customize the `CreatePublicKeyCredentialRequest` by providing a lambda to the `register()` method. This is useful for setting preferences like `preferImmediatelyAvailableCredentials`.

#### DaVinci Collector or Journey Callback

```kotlin
val result = collector.register {
    onCreatePublicKeyCredentialRequest { originalRequest ->
        // Customize and return a new request
        CreatePublicKeyCredentialRequest(
            requestJson = originalRequest.requestJson,
            preferImmediatelyAvailableCredentials = true
        )
    }
}
```

### Authentication Customization

You can customize the authentication request for both **Credential Manager** and **Google Play Services FIDO API** by providing a lambda to the `authenticate()` method. The SDK automatically uses the appropriate customizer based on the API being used.

#### DaVinci Collector or Journey Callback

```kotlin
val result = collector.authenticate {
    // Customizer for Android Credential Manager API
    onGetPublicKeyCredentialOption { originalOption ->
        GetPublicKeyCredentialOption(requestJson = originalOption.requestJson)
    }

    // Customizer for Google Play Services FIDO API
    onPublicKeyCredentialRequestOptions { originalOptions ->
      PublicKeyCredentialRequestOptions.Builder(originalOptions)
            .setRpId(originalOptions.rpId)
            .setChallenge(originalOptions.challenge)
            .setAllowList(originalOptions.allowList)
            .setTimeoutSeconds(30.0) // Example: change timeout
            .build()
    }
}
```

#### Customizing the Credential Manager request

`onGetPublicKeyCredentialOption` customizes the credential option, but some preferences live on
the `GetCredentialRequest` that wraps it. The most notable is
`preferImmediatelyAvailableCredentials`, which moved from `GetPublicKeyCredentialOption` to
`GetCredentialRequest` in `androidx.credentials` 1.5.0 — it makes the ceremony return
immediately when no locally available credential exists instead of falling back to discovering
remote (e.g. cross-device) options. Use the `onGetCredentialRequest` hook to set it:

```kotlin
val result = collector.authenticate {
    useFido2ApiClient = false // Credential Manager path
    onGetCredentialRequest { originalRequest ->
        GetCredentialRequest(
            originalRequest.credentialOptions,
            preferImmediatelyAvailableCredentials = true
        )
    }
}
```

#### Selecting the FIDO2 API per request

The SDK automatically selects the FIDO2 API at runtime: **Google Play Services FIDO** when GMS
is present on the device, otherwise the **Android Credential Manager API**. This default
preserves the routing behaviour existing apps see today.

- Credential Manager is the modern passkey experience and the only path that supports
  conditional mediation (autofill with passkeys).
- Google Play Services is required for non-discoverable, device-bound credentials.

You can override the selection per call with `useFido2ApiClient` in the call block:

```kotlin
val result = collector.authenticate {
    // Route this call through Credential Manager (e.g. for autofill with passkeys)
  useFido2ApiClient = false
}
```

`FidoAuthenticateCustomizer.useFido2ApiClient` is the single source of truth for API selection —
there is no client-level configuration for it. The Credential Manager path requires the app to
declare `androidx.credentials:credentials-play-services-auth` on API ≤ 33 (see the sample app).

### Conditional mediation (autofill with passkeys)

On Android 15 (API 35) the SDK can attach a *pending* credential request to a View (typically
the username field): when the user focuses the field, passkey suggestions appear in the
keyboard without launching the modal ceremony. Use `FidoClient.pendingAuthenticate` (or the
Journey callback's `pendingAuthenticate` variant) to obtain the request, attach it to a View,
and await the result:

```kotlin
if (isConditionalMediationSupported) {
    client.pendingAuthenticate(publicKeyCredentialRequestOptions).onSuccess { pending ->
        // Attach to a View — there is no Compose equivalent; wrap with AndroidView if needed
        usernameField.pendingGetCredentialRequest = pending.request
        launch {
            pending.await().onSuccess { assertion -> /* same JsonObject shape as authenticate() */ }
        }
    }
}
```

Behavior notes:

- `pendingAuthenticate` always routes through the Credential Manager — conditional mediation
  has no Google Play Services surface. When the routing (`useFido2ApiClient`, auto-detected
  GMS by default) would have selected GMS, the SDK logs a warning and forces Credential
  Manager anyway.
- The androidx pending-request callback delivers only final responses — no errors. If the user
  dismisses the suggestions, `pending.await()` stays suspended: keep the modal fallback
  ("Use a passkey" button) visible and treat non-completion as the fallback trigger, not an
  error. `pending.cancel()` releases a suspended `await()` with a
  `CancellationException`-bearing failure (e.g. when closing the screen).
- `pendingAuthenticate` fails fast with a `GetCredentialUnsupportedException` on devices below
  the OS gate (API 35, or API 34 with a preview SDK); check `isConditionalMediationSupported`
  to hide the affordance up front.
- On API ≤ 33 the Credential Manager path requires the `credentials-play-services-auth`
  bridge (see the prerequisite above); when it is missing the SDK logs a warning and the
  request never delivers suggestions.

## ⚠️ Important Migration Notice

### Deprecated Legacy ForgeRock SDK Method

**The following method from the Legacy ForgeRock SDK is no longer supported:**

`org.forgerock.android.auth.callback.WebAuthnRegistrationCallback#setResidentKeyRequirement`

### Reasons for Removal

- **Platform Alignment**: To maintain consistency with the iOS platform implementation
- **Standards Compliance**: To avoid customization or extension of the platform FIDO implementation and adhere to standard WebAuthn/FIDO specifications

### Impact on Existing Customers

This change may affect existing customers who are currently using WebAuthn Authentication with **username-less flow** and rely on `setResidentKeyRequirement` set to `discouraged`.

If you are currently using this method in your Legacy ForgeRock SDK implementation, please review your authentication flow and consider alternative approaches that align with the standard FIDO implementation provided by this module.

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.