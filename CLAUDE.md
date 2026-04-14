# Ping Android SDK – Claude Instructions

## Project Overview

This is the **Ping SDK for Android** — a modular, Kotlin-first SDK for integrating PingOne and
PingAM (ForgeRock) identity platform features into native Android applications. It covers
authentication orchestration, MFA, device management, OIDC/OAuth 2.0, and more.

- **Language**: Kotlin (JVM target 17)
- **Min SDK**: 29 (Android 10)
- **Compile / Target SDK**: 36 (Android 16)
- **Build system**: Gradle with Kotlin DSL (`build.gradle.kts`) + Version Catalog
  (`gradle/libs.versions.toml`)
- **UI**: Jetpack Compose
- **Async**: Kotlin Coroutines + Flow
- **HTTP**: Ktor (CIO engine)
- **DI**: Manual / constructor injection (no Hilt/Dagger)
- **Group ID**: `com.pingidentity.sdks`

---

## Repository Layout

```text
ping-android-sdk/
├── build-logic/               # Convention Gradle plugins (shared build config)
│   └── convention/
│       └── src/main/kotlin/   # AndroidLibraryConventionPlugin, JacocoPlugin, etc.
├── foundation/                # Core / shared foundation modules
│   ├── android/               # Android common utilities
│   ├── browser/               # Custom Tabs / Auth Tabs launcher
│   ├── davinci-plugin/        # Plugin contract for DaVinci connectors
│   ├── device/
│   │   ├── device-client/     # Device client metadata
│   │   ├── device-id/         # Unique device identifier
│   │   ├── device-profile/    # Collected device profile data
│   │   └── device-root/       # Root / integrity detection
│   ├── journey-plugin/        # Plugin contract for Journey callbacks
│   ├── logger/                # Logging interface + built-in loggers
│   ├── migration/             # Legacy-to-modern data migration helpers
│   ├── network/               # Ktor-based HTTP layer
│   ├── oidc/                  # OIDC / OAuth 2.0 client
│   ├── orchestrate/           # Authentication flow orchestration framework
│   ├── storage/               # Encrypted (DataStore + SQLCipher) & plain storage
│   ├── testrail/              # TestRail test-reporting integration
│   └── utils/                 # Shared utility functions
├── davinci/                   # DaVinci (PingOne) orchestration module
├── external-idp/              # Native social login (Google, Facebook)
├── journey/                   # PingAM Journey orchestration module
├── mfa/
│   ├── auth-migration/        # Auth data migration helpers
│   ├── binding/               # Device binding (biometric / PIN / none)
│   ├── binding-migration/     # Legacy device binding migration
│   ├── binding-ui/            # Jetpack Compose UI for device binding
│   ├── commons/               # Shared MFA types and utilities
│   ├── fido/                  # FIDO2 / WebAuthn
│   ├── oath/                  # TOTP / HOTP one-time passwords
│   └── push/                  # Push notification authentication
├── protect/                   # PingOne Protect fraud signals
├── recaptcha-enterprise/      # reCAPTCHA Enterprise integration
└── samples/
    ├── app/
    ├── authenticatorapp/
    ├── journeyapp/
    └── pingsampleapp/         # Combined Ping sample application
```

---

## Common Commands

### Build

```sh
# Assemble all modules
./gradlew assembleDebug

# Build a specific module
./gradlew :davinci:assembleDebug
```

### Unit Tests

```sh
# Run all unit tests with coverage (CI command)
./gradlew --rerun-tasks testDebugUnitTestCoverage --stacktrace --no-daemon

# Run unit tests for a specific module
./gradlew :foundation:oidc:testDebugUnitTest
```

### Lint & Static Analysis

```sh
./gradlew lint
```

### Documentation

```sh
# Generate KDoc HTML documentation for all published modules
./gradlew dokkaGenerate
```

---

## Architecture & Key Patterns

### Coroutines & Flow
All I/O and async work uses `suspend` functions and `Flow`. Never block the main thread. Use
`viewModelScope`, `lifecycleScope`, or a coroutine test dispatcher in tests.

### Module Plugin System
Each authentication module (DaVinci, Journey) uses a **plugin architecture**. Plugins are
registered via `module(PluginKey) { ... }` builder blocks. See `foundation/davinci-plugin` and
`foundation/journey-plugin` for contracts.

### Storage
- **`EncryptedDataStoreStorage`** — default token/session storage (AES-256-GCM via
  `SecretKeyEncryptor`).
- **`MemoryStorage`** — test / ephemeral use only.
- Storage is injected; never hard-code a storage implementation.

### Logging
Use the `Logger` interface from `foundation/logger`. Do **not** use `android.util.Log` directly.
The built-in levels are `NONE`, `STANDARD`, and `VERBOSE`.

### Migration
Legacy ForgeRock SDK data migration is handled by `foundation/migration`. Migration steps are
sequential and idempotent; each step is named and logged at `INFO` level.

### Error Handling
Prefer sealed `Result` / `OidcError` types over raw exceptions. Propagate errors up via `Flow`
or `Result` wrappers; catch only at the boundary (ViewModel / UI layer).

### Networking
All HTTP calls go through the Ktor-based `foundation/network` module. Do not add OkHttp or
Retrofit dependencies; extend the existing Ktor layer instead.

---

## Build-Logic Convention Plugins

| Plugin ID | Purpose |
|-----------|---------|
| `com.pingidentity.convention.android.library` | Standard Android library config (compileSdk, minSdk, JVM target, etc.) |
| `com.pingidentity.convention.centralPublish` | Maven Central publishing via Sonatype OSSRH |
| `com.pingidentity.convention.jacoco` | JaCoCo coverage reports |

All new Android library modules **must** apply `com.pingidentity.convention.android.library`.

---

## Dependency Management

All versions are declared in **`gradle/libs.versions.toml`** (Version Catalog). Do **not**
hardcode version strings in `build.gradle.kts` files. Reference catalog entries:

```kotlin
// In build.gradle.kts
dependencies {
    implementation(libs.ktor.client.core)
    testImplementation(libs.mockk)
}
```

### Forced Security Overrides (root `build.gradle.kts`)
Several transitive dependencies are force-pinned to patched versions for security:
- `io.netty:netty-codec*` → `4.1.125.Final`
- `com.google.protobuf:protobuf-*` → `4.29.2`
- `com.nimbusds:nimbus-jose-jwt` → `10.5`

Do **not** remove these overrides without a security review.

---

## Testing Guidelines

- **Unit tests**: JUnit 4, MockK (`io.mockk:mockk`), Robolectric for Android-specific classes.
- **Integration / UI tests**: Espresso, `androidx.test.runner`.
- **Coroutine tests**: use `kotlinx-coroutines-test` with `runTest { }`.
- **Mock HTTP**: use `ktor-client-mock` for network layer tests.
- Place tests in `src/test/` (unit) or `src/androidTest/` (instrumented).
- Every new public API must have corresponding unit tests.
- All tests must pass before submitting a PR:
  ```sh
  ./gradlew --rerun-tasks testDebugUnitTestCoverage --stacktrace --no-daemon
  ```

---

## Code Style

- Follow the [Kotlin official style guide](https://kotlinlang.org/docs/coding-conventions.html)
  (`kotlin.code.style=official` in `gradle.properties`).
- Follow the internal
  [Android Style Guide](https://github.com/ForgeRock/sdk-standards-of-practice/blob/main/code-style/android-styleguide.md)
  and
  [Android Security Guidelines](https://github.com/ForgeRock/sdk-standards-of-practice/blob/main/security/security-guidelines-android.md).
- Match the style of the existing codebase.
- Use `data class` for DTOs, `sealed class` / `sealed interface` for state and error models.
- Prefer `val` over `var`; prefer immutability.

---

## Pull Request Checklist

1. Target branch: `develop`
2. Link to the relevant JIRA ticket (e.g., `SDKS-XXXX`).
3. All unit tests pass.
4. New functionality is covered by tests.
5. No new `android.util.Log` usages (use the SDK Logger).
6. No hardcoded version strings in Gradle files.
7. Security-sensitive code reviewed against Android Security Guidelines.

---

## Copyright Header

Every new source file must include the following license header:

```kotlin
/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
```

---

© Copyright 2025-2026 Ping Identity Corporation. All Rights Reserved

