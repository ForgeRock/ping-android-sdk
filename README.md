[![Build Status](https://github.com/ForgeRock/ping-android-sdk/actions/workflows/ci.yaml/badge.svg)](https://github.com/ForgeRock/ping-android-sdk/actions/workflows/ci.yaml)
[![Coverage](https://codecov.io/gh/ForgeRock/unified-sdk-android/graph/badge.svg?token=1UYU8JMS8C)](https://codecov.io/gh/ForgeRock/unified-sdk-android)

[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

The Ping Orchestration SDK for Android is designed for creating mobile native apps that seamlessly integrate with the PingAM, Ping AIC, and PingOne platforms.
It offers a range of APIs for user authentication, user device management, and accessing resources
secured by PingOne.

# Documentation

- **Quick Starts** - Find specific setup instructions in the quick start guide for each SDK module.
  Begin with
  the [DaVinci](./davinci/README.md)
  or [Journey](./journey/README.md)
  guide, based on your chosen orchestration platform.
- **Sample Apps** - Visit our [sample apps](https://github.com/ForgeRock/sdk-sample-apps) repository
  on GitHub for examples showcasing various use cases.  
- **Official Docs** - Refer to our
  main [documentation site](https://developer.pingidentity.com/orchsdks/index.html) for
  comprehensive information on the SDKs.

> [!NOTE]
> If you are migrating from the  ForgeRock Android SDK, refer to [MIGRATION.md](./MIGRATION.md).

# Modules

    ping
    ├── foundation                            # Foundation module
    │   ├── android                           # Android common utilities
    │   ├── browser                           # Browser launcher / Custom Tabs support
    │   ├── davinci-plugin                    # Plugin contract for DaVinci integration
    │   ├── device                            # Device capabilities
    │   │   ├── device-client                 # Device client information
    │   │   ├── device-id                     # Unique device identifier
    │   │   ├── device-profile                # Device profile collection
    │   │   └── device-root                   # Device root / integrity detection
    │   ├── journey-plugin                    # Plugin contract for Journey integration
    │   ├── logger                            # Logging interface and built-in loggers
    │   ├── migration                         # Legacy-to-modern data migration helpers
    │   ├── network                           # Ktor-based HTTP network layer
    │   ├── oidc                              # OIDC / OAuth 2.0 interface
    │   ├── orchestrate                       # Authentication flow orchestration framework
    │   ├── storage                           # Encrypted and plain storage abstractions
    │   ├── testrail                          # TestRail integration for test reporting
    │   └── utils                             # Shared utility functions
    ├── davinci                               # Orchestrate authentication with PingOne DaVinci
    ├── external-idp                          # Native social login (Google, Facebook, Apple)
    ├── journey                               # Orchestrate authentication with PingAM Journey
    ├── mfa                                   # MFA capabilities
    │   ├── auth-migration                    # Authentication data migration helpers
    │   ├── binding                           # Device binding (biometric / PIN / none)
    │   ├── binding-migration                 # Legacy device binding data migration
    │   ├── binding-ui                        # Jetpack Compose UI for device binding
    │   ├── commons                           # Shared MFA types and utilities
    │   ├── fido                              # FIDO2 / WebAuthn
    │   ├── oath                              # TOTP / HOTP one-time passwords
    │   └── push                              # Push notification authentication
    ├── protect                               # PingOne Protect fraud signals
    ├── recaptcha-enterprise                  # reCAPTCHA Enterprise integration
    └── samples                               # Sample applications
        └── pingsampleapp                     # Combined Ping sample app

# Support

If you encounter any issues, be sure to check our *
*[Troubleshooting](https://docs.pingidentity.com/sdks/latest/sdks/troubleshooting.html)** pages.

Support tickets can be raised whenever you need our assistance; here are some examples of when it is
appropriate to open a ticket (but not limited to):

* Suspected bugs or problems with Ping Identity software.
* Requests for assistance - please look at the **[Documentation](https://docs.pingidentity.com/sdks)
  ** and **[Knowledge Base](https://support.pingidentity.com/s/knowledge-base)** first.

You can raise a ticket using the *
*[Ping Identity Support Portal](https://support.pingidentity.com/s/)** that provides one stop access
to support services.

The support portal shows all currently open support tickets and allows you to raise a new one by
clicking **New Ticket**.

# Contributing

If you would like to contribute to this project, please see
the [contributions guide](./CONTRIBUTING.md).

# Disclaimer

> **This code is provided by Ping Identity Corporation ("Ping") on an "as is" basis, without
warranty of any kind, to the fullest extent permitted by law.
> Ping Identity Corporation does not represent or warrant or make any guarantee regarding the use of
this code or the accuracy, timeliness or completeness of any data or information relating to this
code, and Ping Identity Corporation hereby disclaims all warranties whether express, or implied or
statutory, including without limitation the implied warranties of merchantability, fitness for a
particular purpose, and any warranty of non-infringement.
> Ping Identity Corporation shall not have any liability arising out of or related to any use,
implementation or configuration of this code, including but not limited to use for any commercial
purpose.
> Any action or suit relating to the use of the code may be brought only in the courts of a
jurisdiction wherein Ping Identity Corporation resides or in which Ping Identity Corporation
conducts its primary business, and under the laws of that jurisdiction excluding its conflict-of-law
provisions.**

# License

This software may be modified and distributed under the terms of the MIT license. See the [LICENSE](./LICENSE) file for details

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.