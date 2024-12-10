<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Logo">
  </a>
  <hr/>
</p>

`oidc` module provides OIDC client for PingOne and ForgeRock platform.

The `oidc` module follows the [OIDC](https://openid.net/specs/openid-connect-core-1_0.html) specification and
provides a simple and easy-to-use API to interact with the OIDC server. It allows you to authenticate, retrieve the
access token, revoke the token, and sign out from the OIDC server.

## Add dependency to your project

```kotlin
dependencies {
    implementation(project(":foundation:oidc"))
}
```

## Set scheme in AndroidManifest.xml

By default, the Ping SDK uses the `Browser` agent to launch the browser for the authorization request.
The SDK uses the Custom Tabs to launch the browser, you can customize the browser agent by providing the `customTab`
properties. To receive the redirect from the browser, you need to define the `scheme` in the `AndroidManifest.xml`.

Define `scheme` to receive redirect from browser, the `scheme` should match the `redirect uri` scheme.

In the following example, the `scheme` is `com.pingidentity.demo` and `org.forgerock.demo`. You can define multiple schemes
to receive the redirect from the browser.

```xml

<activity
        android:name="net.openid.appauth.RedirectUriReceiverActivity"
        android:exported="true"
        tools:node="replace">
    <intent-filter>
        <action android:name="android.intent.action.VIEW"/>

        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>

        <data android:scheme="com.pingidentity.demo"/>
        <data android:scheme="org.forgerock.demo"/>
    </intent-filter>
</activity>
```

## Oidc Client Configuration

Basic Configuration, use `discoveryEndpoint` to lookup OIDC endpoints

```kotlin
val ping = OidcClient {
    discoveryEndpoint =
            "https://auth.pingone.ca/02fb4743-189a-4bc7-9d6c-a919edfe6447/as/.well-known/openid-configuration"
    clientId = "c12743f9-08e8-4420-a624-71bbb08e9fe1"
    redirectUri = "org.forgerock.demo://oauth2redirect"
    scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
}

when (val result = ping.accessToken()) { // Retrieve the access token
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

ping.revoke() // Revoke the access token
ping.endSession() // End the session
```

By default, the SDK use `DataStoreStorage` (With `EncryptedSerializer` ) to stores the token and `None` Logger is set,
however developers can override the storage and logger settings.

Basic Configuration with custom `storage` and `logger`

```kotlin
val ping = OidcClient {
    storage = encryptedStorage(clientId) // Store the token in the encrypted storage
    logger = Logger.Standard // Log to Logcat
    //...
}
```

More OidcClient configuration, configurable attribute can be found under
[OIDC Spec](https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest)

```kotlin

val ping = OidcClient {
    acrValues = "urn:acr:form"
    loginHint = "test"
    display = "test"
    //...
}
```

## Customize the browser agent

The SDK provides [custom tab](https://developer.chrome.com/docs/android/custom-tabs/guide-get-started) as agent to
launch the browser for the authorization request. You can customize the browser agent by providing
the [customTab property](https://developer.android.com/reference/androidx/browser/customtabs/CustomTabsIntent.Builder).

```kotlin
val ping = OidcClient {

    agent(browser) {
        
        // Customize the CustomTab
        customTab = {
            setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            setShowTitle(false)
            setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            setUrlBarHidingEnabled(true)
        }
    }
    //...

}
```

## Custom Agent

Other than using the Custom Tabs, you can also provide a custom agent to launch the authorization request.
You can implement the `Agent` interface to create a custom agent.

```kotlin
interface Agent<T> {
    fun config(): () -> T
    suspend fun endSession(oidcConfig: OidcConfig<T>, idToken: String): Boolean
    suspend fun authenticate(oidcConfig: OidcConfig<T>): AuthCode
}
```

Here is an example of creating a custom agent.

```kotlin
// Create a custom agent configuration
@OidcDsl
class CustomAgentConfig {
    var config1 = "config1Value"
    var config2 = "config2Value"
}

var customAgent = object : Agent<CustomAgentConfig> {
    override fun config() = ::CustomAgentConfig
    override suspend fun authenticate(oidcConfig: OidcConfig<CustomAgentConfig>): AuthCode {
        oidcConfig.config.config2 //Access the agent configuration
        oidcConfig.oidcClientConfig.openId.endSessionEndpoint //Access the oidcClientConfig
        return AuthCode("TestAgent", "", "", "", "")
    }

    override suspend fun endSession(oidcConfig: OidcConfig<CustomAgentConfig>, idToken: String):
            Boolean {
        //Logout session with idToken
        oidcConfig.config.config1 // Access the agent configuration
        oidcConfig.oidcClientConfig.openId.endSessionEndpoint // Access the oidcClientConfig
        return true
    }
}

val ping = OidcClient {
    agent(customAgent) {
        config1 = "customConfig1"
        config2 = "customConfig2"
    }
    //...
}
```