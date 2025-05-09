<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Logo">
  </a>
  <hr/>
</p>

# Ping FIDO2

# MFA/FIDO2 Module

This module provides functionality for integrating FIDO2 (Fast Identity Online, also known as WebAuthn) multi-factor
authentication into your Android application. It simplifies the process of user registration and authentication using
modern security keys and platform authenticators. This module is specifically designed for smooth integration with AIC
Journey and DaVinci Flow,

## Overview

This module offers a streamlined API for handling FIDO2 interactions. It abstracts away the complexities of the
underlying FIDO2 protocols, allowing you to easily add strong authentication to your application with `AIC` Journey
and `DaVinci` Flow.

Key features include:

* **FIDO2 Registration:** Enables users to register their FIDO2 authenticators (security keys, fingerprint sensors, face
  unlock, etc.) with your application.
* **FIDO2 Authentication:** Facilitates secure user login using their registered FIDO2 authenticators.
* **Simplified API:** Provides clear and concise functions for performing registration and authentication.

## Integration

To integrate this module into your Android project, add the following dependency to your app-level `build.gradle` file:

```gradle
dependencies {
    implementation("com.pingidentity.sdks:fido2:<version>")
    // Add other necessary dependencies here
}
```

Make sure to synchronize your Gradle project after adding the dependency.

## Usage

### DaVinci Integration

The module provides two main classes for FIDO2 operations: `Fido2RegistrationCollector` for user registration
and `Fido2AuthenticationCollector` for user authentication. Both operations are implemented as suspend functions and
return
a `Result<JsonObject>`.

#### Registration

To register a new FIDO2 authenticator, use the `register()` function of the `Fido2RegistrationCollector`.

```kotlin
if (node is ContinueNode) {
    node.collectors.forEach { collector ->
        if (collector is Fido2RegistrationCollector) {
            when (val result = collector.register()) {
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

#### Authentication

To authenticate a user using a registered FIDO2 authenticator, use the `authenticate()` function of
the `Fido2AuthenticationCollector`.

```kotlin
if (node is ContinueNode) {
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
```

### Journey Integration

TBD

## Associate your App with your server

To associate your server with your Android app, you need to make public, verifiable statements by using a
[Digital Asset Links JSON file](https://docs.pingidentity.com/sdks/latest/sdks/use-cases/mobile-biometrics/android/01-prepare-assetlinks-json-file.html)

## Origin Domain Configuration

For enhanced security and to ensure proper communication during the FIDO2 process, you might need to configure the
allowed origin domains on your server. This configuration specifies the domains from which your server will accept FIDO2
requests.

Refer to your server-side configuration documentation, specifically the section on "[Android Origin
Domains](https://docs.pingidentity.com/sdks/latest/sdks/use-cases/mobile-biometrics/android/02-node-configurations.html#android-origin-domains)"
for detailed instructions on how to set up these allowed origin domains.

Typically, this involves specifying the fully qualified domain name (FQDN) of your application's backend server within
your server's FIDO2 configuration. This step helps prevent unauthorized entities from initiating FIDO2 registration or
authentication requests against your server on behalf of your application.
