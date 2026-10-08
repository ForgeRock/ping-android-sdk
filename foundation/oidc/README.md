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

### Rich Authorization Requests (RFC 9396)

Use `authorizationDetails` to request granular authorization instead of hand-serializing RAR JSON
into `additionalParameters`.

Configure it on the OIDC module:

```kotlin
module(com.pingidentity.oidc.module.Oidc) {
    discoveryEndpoint = "https://example.com/envId/as/.well-known/openid-configuration"
    clientId = "client-id"
    redirectUri = "org.pingidentity.demo://callback"
    authorizationDetails = listOf(
        AuthorizationDetail(
            type = "payment_initiation",
            actions = listOf("initiate", "status"),
            locations = listOf("https://example.com/payments"),
        )
    )
}
```

Or provide it per call, which replaces the configured value for that request:

```kotlin
web.authorize {
    authorizationDetails(
        AuthorizationDetail(type = "account_information", actions = listOf("balance"))
    )
}
```

The `authorization_details` parameter is emitted exactly once, chosen as: per-call
`authorizationDetails` > a hand-serialized `authorization_details` entry in `additionalParameters` >
the configured list. When `par = true`, the parameter is pushed in the PAR form body. Type-specific
members beyond the common fields (`type`, `locations`, `actions`, `datatypes`, `privileges`) are
carried through `AuthorizationDetail.additionalFields`. With JSON configuration, use the
`authorizationDetails` key (an array of detail objects).

A Journey-configured client applies the module-level `authorizationDetails` to its session agent's
authorize request. `authorization_details` scoped to an individual Journey run is not currently
exposed.

### Using RAR alongside an existing login (multiple tokens)

A RAR login typically happens *after* the user has already signed in, and the app then holds two
tokens: the token from the original login (**Token A**) and the token issued for the RAR transaction
(**Token B**). The app can use either one, revoke only B, or revoke both on logout.

Every workflow (`Journey`, `DaVinci`, `OidcWebClient`, `OidcDeviceClient`) keeps **one token in its
own `storage`**. The browser-based, DaVinci, and device workflows **revoke the token currently in
their storage and replace it** when a new authorization starts — so running the RAR login on the same
workflow (or on a client sharing its storage) revokes Token A. (Journey does not revoke or replace its
stored token on a new login: the previous token remains stored and `token()` keeps serving it until it
expires.) To keep both tokens, give the RAR login **its own workflow with its own storage**:

```kotlin
// Token A: the original login. Scenario 1 uses a native Journey login,
// scenario 2 a browser login with an OidcWebClient, each with its own storage account.
val journey = Journey {
    serverUrl = "https://example.com/am"
    realm = "alpha"
    module(Oidc) {
        clientId = "ClientID"
        discoveryEndpoint = "https://example.com/am/oauth2/alpha/.well-known/openid-configuration"
        scopes = mutableSetOf("openid", "profile")
        redirectUri = "org.forgerock.demo://oauth2redirect"
        storage {
            fileName = "ACCESS_TOKEN_STORAGE_JOURNEY"
        }
    }
}

// Token B: the browser-based RAR login, a separate workflow with a separate storage account.
// It can use the same OAuth client as the original login, or a different one.
val rarLogin = OidcWebClient {
    module(Oidc) {
        clientId = "ClientID"
        discoveryEndpoint = "https://example.com/am/oauth2/alpha/.well-known/openid-configuration"
        scopes = mutableSetOf("openid", "profile")
        redirectUri = "org.forgerock.demo://oauth2redirect"
        storage {
            fileName = "ACCESS_TOKEN_STORAGE_RAR"
        }
    }
}

// Sign in once (Token A), then run the RAR login (Token B). Token A stays valid.
val journeyUser = journey.user()
val rarResult = rarLogin.authorize {
    authorizationDetails(
        AuthorizationDetail(type = "account_information", actions = listOf("list_accounts"))
    )
}

// Use either token, whenever you need it
val tokenA = journeyUser?.token()
val tokenB = rarLogin.user()?.token()

// Revoke only Token B. Token A is untouched.
rarLogin.user()?.revoke()

// Log out of everything: each workflow only knows its own storage, so this is one call per workflow.
journeyUser?.logout()
rarLogin.user()?.logout()
```

With JSON configuration, use the `storage.fileName` key instead of the DSL:

```json
{
  "oidc": {
    "clientId": "ClientID",
    "discoveryEndpoint": "https://example.com/am/oauth2/alpha/.well-known/openid-configuration",
    "scopes": ["openid", "profile"],
    "redirectUri": "org.forgerock.demo://oauth2redirect",
    "storage": { "fileName": "ACCESS_TOKEN_STORAGE_RAR" }
  }
}
```

| Scenario | Token A | Token B (RAR) |
|---|---|---|
| **1. Native login, then browser RAR** | `Journey` workflow, storage `fileName` `A` | `OidcWebClient`, storage `fileName` `B` |
| **2. Browser login, then browser RAR** | `OidcWebClient`, storage `fileName` `A` | A second `OidcWebClient`, storage `fileName` `B` |

> **Important: the storage file is what separates the tokens.** Encrypted DataStore files are
> identified by their `fileName`, so two configurations with the same name share one slot. The
> default file name is `com.pingidentity.sdk.v1.tokens`, so clients that do not set `storage` all
> share it. Clients created from JSON configuration use the default file unless the config sets
> `storage.fileName`. Sharing a slot has these effects:
> - **Same OAuth client:** on the browser-based, DaVinci, and device workflows, the RAR login revokes
>   Token A and replaces it with Token B.
> - **Different OAuth clients:** the RAR login deletes Token A from the device, but its revocation
>   request is sent with the RAR client's `client_id`. The authorization server is expected to refuse
>   it ([RFC 7009 §2.1](https://datatracker.ietf.org/doc/html/rfc7009#section-2.1)), so Token A stays
>   valid at the server yet can no longer be used or revoked by the app. Token B is then returned by
>   the original workflow's `token()` too, although it was issued to a different client.

**Things to be aware of:**
- **One token per workflow.** On the browser-based, DaVinci, and device workflows, signing in again —
  including running a second RAR login on `rarLogin` — revokes and replaces that workflow's previous
  token. Journey does not revoke on re-login: the previously stored token remains until it expires.
  To hold several RAR tokens at once, use one workflow and storage file for each.
- **Logging out of both takes two calls.** `logout()` revokes the token of the workflow it is called
  on and signs that workflow out. Nothing links the two workflows, so the app calls `logout()` on each.
- **The browser session is shared separately from the tokens.** All workflows on the same tenant share
  the Custom Tab cookie jar, so a second authorization for the same user may complete silently through
  the existing browser session (SSO) without a login prompt. Storage separation isolates the *tokens*,
  not the browser session.

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.
