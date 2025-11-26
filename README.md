[![Build Status](https://github.com/ForgeRock/ping-android-sdk/actions/workflows/ci.yaml/badge.svg)](https://github.com/ForgeRock/ping-android-sdk/actions/workflows/ci.yaml)
[![Coverage](https://codecov.io/gh/ForgeRock/unified-sdk-android/graph/badge.svg?token=1UYU8JMS8C)](https://codecov.io/gh/ForgeRock/unified-sdk-android)

[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

The Ping SDK for Android is designed for creating mobile native Apps that seamlessly integrate with the PingOne platform.
It offers a range of APIs for user authentication, user device management, and accessing resources secured by PingOne.
This SDK is support Browser, iOS and Android platforms.

# Modules

    ping 
    ├── foundation                            # Foundation module
    │   ├── android                           # Android Common
    │   ├── davinci-plugin                    # For module that integrated with davinci
    │   ├── journey-plugin*                   # For module that integrated with journey
    │   ├── logger                            # Provide Logging interface and common loggers
    │   ├── oidc*                             # Provide OIDC interface
    │   ├── orchestrate                       # Orchestrating authentication flow framework
    │   ├── storage                           # Provide Storage interface
    │   └── utils                             # Provide common utilities function
    ├── davinci                               # Orchestrate authentication with PingOne Davinci
    ├── journey*                              # Orchestrate authentication with Journey
    ├── mfa*                                  # Povide MFA capabilities such as OTP, PUsh, WebAuthn
    ├── external-idp                          # Provide Native Google, Facebook, Apple SocialLogin
    ├── ...
    └── ...

***Note***: * Module under development and experimental