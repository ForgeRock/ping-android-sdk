[![Build Status](https://github.com/ForgeRock/ping-android-sdk/actions/workflows/ci.yaml/badge.svg)](https://github.com/ForgeRock/ping-android-sdk/actions/workflows/ci.yaml)
[![Coverage](https://codecov.io/gh/ForgeRock/unified-sdk-android/graph/badge.svg?token=1UYU8JMS8C)](https://codecov.io/gh/ForgeRock/unified-sdk-android)

<div>
  <picture>
     <img src="https://www.pingidentity.com/content/dam/ping-6-2-assets/topnav-json-configs/Ping-Logo.svg" width="80" height="80"  alt=""/>
  </picture>
</div>

Unified SDK is an SDK designed for creating Mobile Native Apps that seamlessly integrate with the PingOne platform.
It offers a range of APIs for user authentication, user device management, and accessing resources secured by PingOne.
This SDK is support Browser, iOS and Android platforms.

# Module

    ping 
    ├── foundation                            # Foundation module
    │   ├── android                           # Android Common
    │   ├── davinci-plugin                    # For module that integrated with davinci
    │   ├── device                            # Device module
    │   │   ├── device-id                     # Generate and manage Device Id
    │   │   └── device-integrity              # Device and App Integriry
    │   ├── fido                              # WebAuthn integration
    │   ├── journey-plugin                    # For module that integrated with journey
    │   ├── logger                            # Provide Logging interface and common loggers
    │   ├── network                           # Provide Networking interface
    │   ├── oidc                              # Provide OIDC interface
    │   └── storage                           # Provide Storage interface
    ├── davinci                               # Orchestrate authentication with PingOne Davinci
    ├── journey                               # Orchestrate authentication with Journey
    ├── mfa                                   # Provide interface to build Authenticator App
    ├── protect                               # Provide PingOne Protect integration
    ├── external-idp                          # Provide Native Google, Facebook, Apple SocialLogin
    ├── verify                                # Provide PingOne Verify integration
    ├── wallet                                # Provide PingOne Neo integration
    ├── ...
    └── ...

## Add dependency to your project

The PingIdentity unified SDK project emphasizes modularity, allowing you to select and include only the necessary SDKs for your app instead of including the entire SDK.
For example:

### Scenario 1:

An application with Centralize Login

```kotlin
dependencies {
    implementation("com.pingidentity.sdks:oidc:$ping_version")
}
```

### Scenario 2:

An Orchestrate authentication with PingOne Davinci, and in the flow, I would like to enable PingOne Protect

```kotlin
dependencies {
    implementation("com.pingidentity.sdks:davinci:$ping_version")
    implementation("com.pingidentity.sdks:protect:$ping_version")
}
```

### Scenario 2:

An Orchestrate authentication with Journey, and in the authentication journey, I would like to have Google Social Login,
enable PingOne Protect and PingOne Verify

```kotlin
dependencies {
    implementation("com.pingidentity.sdks:journey:$ping_version")
    implementation("com.pingidentity.sdks:protect:$ping_version")
    implementation("com.pingidentity.sdks:verify:$ping_version")
    implementation("com.pingidentity.sdks:social-login:$ping_version")
}
```

As you can see, the `protect` module is included in both scenarios. 
This is because the `protect` module is a dependency of the `davinci` and `journey` modules.
Similarly, the `verify` and other modules can be shared across the `journey` and `davinci` modules.