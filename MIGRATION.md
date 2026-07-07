[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Migrating from the ForgeRock SDK to the Ping Orchestration SDK for Android

This guide helps you transition from the legacy ForgeRock Android SDK to the modern Ping Orchestration
SDK for
Android. It covers authentication flow migration as well as MFA data migration tools.

For the full official migration documentation, visit:
**[Ping Orchestration SDK Migration Guide](https://developer.pingidentity.com/orchsdks/journey/migration.html)**.


## Journey Migration

The Journey module includes a comprehensive migration reference that maps every legacy ForgeRock API
to its Ping SDK equivalent, including:

- **SDK initialization** — `FROptions` → `JourneyConfig`
- **Authentication flow** — callback-based `NodeListener` → coroutine-based sealed `Node` types
- **Session & token management** — `FRSession` / `FRUser` → `Journey` / `User`
- **Data model translation** — complete class-by-class mapping table

See the full reference with side-by-side code examples:\
**[journey/migration.md](./journey/migration.md)**


## MFA Data Migration

If your app uses the legacy ForgeRock MFA capabilities (device binding, TOTP/HOTP, or push
authentication), the Ping Orchestration SDK provides dedicated migration modules that automatically transfer
existing credentials to the new storage format.

Both modules use the
[foundation migration framework](./foundation/migration/README.md) and support automatic
startup via AndroidX App Startup as well as manual invocation.

### Device Binding Migration

The `binding-migration` module migrates legacy device binding data such as user keys and App PIN
cryptographic material to the new `UserKeysStorage` and `AppPinConfig` storage systems.

For setup instructions, manifest configuration, and troubleshooting:\
**[mfa/binding-migration/README.md](./mfa/binding-migration/README.md)**

### Authenticator (OATH & Push) Migration

The `auth-migration` module migrates legacy ForgeRock Authenticator TOTP, HOTP, and Push
credentials to the new `SQLOathStorage` and `SQLPushStorage` databases.

For setup instructions, configuration options, and troubleshooting:\
**[mfa/auth-migration/README.md](./mfa/auth-migration/README.md)**


## Migration Framework

Both MFA migration modules are built on top of the **Ping Migration SDK**
(`foundation:migration`), a step-based migration framework with progress tracking and shared state
management. If you need to build custom migration logic (e.g., for custom storage implementations),
refer to the framework documentation:\
**[foundation/migration/README.md](./foundation/migration/README.md)**


## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.
