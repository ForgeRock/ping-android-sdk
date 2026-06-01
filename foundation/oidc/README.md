[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Oidc Module

`Oidc module` provides OIDC client for PingOne and Ping Identity platform.

The `oidc` module follows the [OIDC](https://openid.net/specs/openid-connect-core-1_0.html)
specification and
provides a simple and easy-to-use API to interact with the OIDC server. It allows you to
authenticate, retrieve the
access token, revoke the token, and sign out from the OIDC server.

## Getting Started

### Prerequisites

- Android API level 29 or higher

### Installation

To integrate this module into your Android project, include the following dependency in
your `build.gradle.kts` (or `build.gradle`) file:

```kotlin
dependencies {
    implementation("com.pingidentity.sdks:oidc:<version>")
    //Use the browser agent to launch the browser for the authorization request
    implementation("com.pingidentity.sdks:browser:<version>")
}
```

Replace `<version>` with the latest available version of the SDK from the Maven repository. Ensure your
project's `repositories` block includes Maven Central or the Ping Identity Maven repository.

### Set scheme in `AndroidManifest.xml`

With the `browser` module dependency, the Ping SDK's OIDC module uses a Browser agent to launch the
authorization request in a browser. By default, it uses Custom Tabs or Auth Tabs.

To handle the redirect after authentication, you must define a scheme. This scheme must match the
scheme used in your redirect URI.

### Custom Scheme Configuration

For example, if your redirect URI is `com.pingidentity.demo://callback`, then
`com.pingidentity.demo`
should be defined as your scheme. You can also define multiple schemes if needed to support various
redirect URIs.

In the App `gradle.build.kts` file, add the following `manifestPlaceholders` to the
`android.defaultConfig`:

```kotlin
android {
    defaultConfig {
        manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.demo"
    }
}
```

### HTTPS Scheme Configuration

If you're using HTTPS redirect URIs (e.g., `https://yourdomain.com/oauth2redirect`), you need to configure App Links to handle the redirect after authentication. This requires two steps:

#### 1. Setup assetlinks.json file

Create an `assetlinks.json` file on your web server as described in the [Android App Links documentation](https://developer.android.com/studio/write/app-link-indexing#associatesite). This file should be accessible at `https://yourdomain.com/.well-known/assetlinks.json`.

Example `assetlinks.json`:
```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.pingidentity.demo",
      "sha256_cert_fingerprints": ["your_app_sha256_fingerprint"]
    }
  }
]
```

#### 2. Update AndroidManifest.xml

Add an intent filter to your app's `AndroidManifest.xml` file to handle the HTTPS redirect:

```xml
<activity
    android:name="com.pingidentity.browser.CustomTabActivity"
    android:exported="true"
    android:launchMode="singleTop">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="https" />
        <data android:host="yourdomain.com" />
        <data android:path="/oauth2redirect" />
    </intent-filter>
</activity>
```

**Important**: Make sure the `scheme`, `host`, and `path` in the intent filter exactly match your redirect URI. For example, if your redirect URI is `https://youtdomain.com/oauth2redirect`, then:
- `android:scheme="https"`
- `android:host="yourdomain.com"`
- `android:path="/oauth2redirect"`

## Oidc Client Configuration

Basic Configuration, use `discoveryEndpoint` to lookup OIDC endpoints

```kotlin

// Create an OIDC client with the discovery endpoint, and other configurations
val web = OidcWebClient {
    logger = Logger.STANDARD
    module(com.pingidentity.oidc.module.Oidc) {
        discoveryEndpoint =
            "https://example.com/envId/as/.well-known/openid-configuration"
        clientId = "client-id"
        redirectUri = "org.pingidentity.demo://callback"
        scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
    }
}

//Start the OIDC authentication flow
web.authorize()
    .onSuccess { user ->
        ...
    }.onFailure { throwable ->
        ...
    }


// To retieve the existing user
val user = web.user()


//To retrieve the access token
when (val result = user.token()) { // Retrieve the access token
    is Result.Failure -> {
        when (result.value) {
            is OidcError.ApiError -> TODO()
            OidcError.AuthenticationRequired -> TODO()
            is OidcError.AuthorizeError -> TODO()
            is OidcError.NetworkError -> TODO()
            is OidcError.Unknown -> TODO()
        }
    }
    is Result.Success -> {
        val accessToken = result.value
    }
}

user.revoke() // Revoke the access token
user.logout() // Logout
```

By default, the SDK use `EncryptedDataStoreStorage` to stores the token and `None` Logger is set,
however developers can override the storage and logger settings.

Basic Configuration with custom `storage` and `logger`

```kotlin
val web = OidcWebClient {
    logger = Logger.STANDARD
    module(com.pingidentity.oidc.module.Oidc) {
        discoveryEndpoint =
            "https://example.com/envId/as/.well-known/openid-configuration"
        clientId = "client-id"
        redirectUri = "org.pingidentity.demo://callback"
        scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
    }
    storage = { MemoryStorage() }
}
```

More OidcClient configuration, configurable attribute can be found under
[OIDC Spec](https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest)

```kotlin
val web = OidcWebClient {
    module(com.pingidentity.oidc.module.Oidc) {
        discoveryEndpoint =
            "https://example.com/envId/as/.well-known/openid-configuration"
        clientId = "client-id"
        redirectUri = "org.pingidentity.demo://callback"
        acrValues = "urn:acr:form"
        loginHint = "test"
        display = "test"
        ...
    }
}
```

Provide parameters to the `authorize` method to override the default configuration

```kotlin
web.authorize(
    "acrValues" to "urn:acr:form",
    "loginHint" to "test",
    "custom" to "custom_value"
).onSuccess { user ->
    ...
  }.onFailure { throwable ->
    ...
  }
```

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.
