# AGENTS.md

This file provides guidance to AI coding agents (Claude Code, Cursor, GitHub Copilot, Gemini, etc.) when working with code in this repository. It follows the open AGENTS.md convention.

> **Note:** CLAUDE.md in this repository is a one-line redirect to AGENTS.md. Edit AGENTS.md only.
>
> **Plugin users:** Developers using the Ping SDK Agents plugin (ping-sdk-agents) receive additional implementation-level guidance via android-architect, android-engineer, android-code-reviewer, and related agents. AGENTS.md is the repo-level foundation that both plugin users and bare-agent users share — it is complete enough to stand alone without the plugin.

## Overview

The **Ping Orchestration SDK for Android** is a Kotlin/Coroutines library that enables mobile apps to authenticate against PingAM, Ping AIC, and PingOne DaVinci. It is distributed to Maven Central under the `com.pingidentity.sdks:*` group. The SDK requires Android API level 29 (`minSdk = 29`) and targets modern Kotlin coroutine patterns throughout — no RxJava, no callbacks in new code. If you are an AI agent starting work in this repository, read this file before touching any code.

## Toolchain

| Item | Value |
|------|-------|
| JDK | 17 |
| Kotlin | 2.2.x (exact: see `gradle/libs.versions.toml` → `kotlin`) |
| AGP | see `gradle/libs.versions.toml` → `agp` |
| `minSdk` | 29 |
| `compileSdk` | 36 |
| `targetSdk` | 36 |
| Coroutines | `kotlinx-coroutines-core` (see `libs.versions.toml` → `coroutines`) |
| HTTP | Ktor + CIO (see `libs.versions.toml` → `ktor`) |

All version strings live in `gradle/libs.versions.toml`. Do not hardcode versions in `build.gradle.kts` files — add or update entries in the TOML and reference them via the `libs.<alias>` accessor.

## Build & Test Commands

Run all commands from the repository root using the Gradle wrapper.

```sh
# Full build
./gradlew clean build

# All unit tests with coverage (matches CI)
./gradlew --rerun-tasks testDebugUnitTestCoverage --stacktrace --no-daemon

# Single-module tests — replace <module> with the Gradle path, e.g. :davinci
./gradlew :<module>:testDebugUnitTestCoverage --no-daemon

# Common module examples
./gradlew :foundation:orchestrate:testDebugUnitTestCoverage --no-daemon
./gradlew :foundation:oidc:testDebugUnitTestCoverage --no-daemon
./gradlew :davinci:testDebugUnitTestCoverage --no-daemon
./gradlew :journey:testDebugUnitTestCoverage --no-daemon
./gradlew :mfa:oath:testDebugUnitTestCoverage --no-daemon
./gradlew :mfa:push:testDebugUnitTestCoverage --no-daemon
./gradlew :mfa:binding:testDebugUnitTestCoverage --no-daemon
./gradlew :protect:testDebugUnitTestCoverage --no-daemon

# Generate API reference docs (Dokka output → build/dokka/)
./gradlew dokkaGenerate
```

Always add tests for new functionality and ensure all existing tests pass before opening a PR. See `CONTRIBUTING.md` for the full contribution workflow.

## CI/CD

The project uses GitHub Actions with the following workflows under `.github/workflows/`:

| Workflow | Purpose |
|---|---|
| `ci.yaml` | Main pipeline — orchestrates build, unit tests, and BrowserStack E2E testing |
| `build-and-test.yaml` | Unit tests across all modules |
| `integration-tests.yaml` | Instrumented tests on emulators |
| `browserstack-*-run.yaml` | Device farm testing on real Android devices (BrowserStack) — DaVinci, Journey, OATH, Push |
| `browserstack-*-results.yaml` | BrowserStack result collection workflows |
| `generate-docs.yaml` / `docs.yaml` | Dokka API reference generation and publishing |
| `mend-sca-scan.yaml` | Dependency security scanning (Mend SCA) |
| `mend-sast-scan.yaml` | Static application security testing (Mend SAST) |
| `package-sizes.yml` | Track AAR size regressions |

**Test organisation:**
- Unit tests live under `<module>/src/test/kotlin/` and run with `testDebugUnitTestCoverage`.
- Instrumented tests live under `<module>/src/androidTest/kotlin/` and require an emulator/device (`connectedDebugAndroidTest`).
- E2E BrowserStack tests require device farm credentials and run only in CI.
- TestRail integration is controlled by the `TESTRAIL_ENABLE` env var — leave it unset locally to skip reporting.

## Module Layout

```text
ping
├── foundation/                    # Core platform layers
│   ├── android/                   # Android common utilities
│   ├── browser/                   # Browser launcher / Custom Tabs support
│   ├── davinci-plugin/            # Plugin contract for DaVinci integration (no `:davinci` dependency)
│   ├── device/
│   │   ├── device-client/         # Device client information
│   │   ├── device-id/             # Unique device identifier
│   │   ├── device-profile/        # Device profile collection
│   │   └── device-root/           # Device root / integrity detection
│   ├── journey-plugin/            # Plugin contract for Journey integration (no `:journey` dependency)
│   ├── logger/                    # Logging interface and built-in loggers
│   ├── migration/                 # Legacy-to-modern data migration helpers
│   ├── network/                   # Ktor-based HTTP network layer
│   ├── oidc/                      # OIDC / OAuth 2.0 interface
│   ├── orchestrate/               # Authentication flow orchestration framework
│   ├── storage/                   # Encrypted and plain DataStore abstractions
│   ├── testrail/                  # TestRail integration for test reporting
│   └── utils/                     # Shared utility functions
├── davinci/                       # Orchestrate authentication with PingOne DaVinci
├── external-idp/                  # Native social login (Google, Facebook, Apple)
├── journey/                       # Orchestrate authentication with PingAM Journey
├── mfa/
│   ├── auth-migration/            # Authentication data migration helpers
│   ├── binding/                   # Device binding (biometric / PIN / none)
│   ├── binding-migration/         # Legacy device binding data migration
│   ├── binding-ui/                # Jetpack Compose UI for device binding (UI module exception)
│   ├── commons/                   # Shared MFA types and utilities
│   ├── fido/                      # FIDO2 / WebAuthn
│   ├── oath/                      # TOTP / HOTP one-time passwords
│   └── push/                      # Push notification authentication
├── protect/                       # PingOne Protect fraud signals
├── recaptcha-enterprise/          # reCAPTCHA Enterprise integration
└── samples/
    └── pingsampleapp/             # Combined Ping sample application
```

The authoritative module list is `settings.gradle.kts`. Reference modules via typesafe accessors (e.g., `projects.foundation.orchestrate`), never via `project(":foundation:orchestrate")` string literals.

Source for each library module lives at `<module>/src/main/kotlin/`. Tests are at `<module>/src/test/kotlin/`. Each module ships its own `build.gradle.kts` and a `README.md` with usage examples.

## Architecture

### Dependency hierarchy

```text
foundation:logger, foundation:storage           ← no internal deps (leaf)
foundation:network      ← foundation:logger
foundation:utils        ← foundation:logger
foundation:browser      ← foundation:logger
foundation:orchestrate  ← foundation:storage, foundation:network
foundation:oidc         ← foundation:orchestrate, foundation:browser
foundation:davinci-plugin, foundation:journey-plugin  ← foundation:orchestrate
davinci   ← foundation:oidc, foundation:davinci-plugin
journey   ← foundation:oidc, foundation:journey-plugin, foundation:device:device-profile
external-idp            ← foundation:browser, foundation:davinci-plugin, foundation:journey-plugin
protect                 ← foundation:davinci-plugin, foundation:journey-plugin
recaptcha-enterprise    ← foundation:davinci-plugin, foundation:journey-plugin
mfa:commons             ← foundation:network, foundation:logger
mfa:oath, mfa:push      ← mfa:commons
mfa:fido                ← mfa:commons, foundation:davinci-plugin, foundation:journey-plugin
mfa:binding             ← mfa:commons, foundation:device:device-id, foundation:journey-plugin
mfa:binding-ui          ← mfa:binding  (only module allowed to import Compose)
```

- `foundation/*` modules are the lowest layer; they have no dependency on `davinci` or `journey`.
- Cross-cutting modules (e.g., `external-idp`, `mfa/*`) that need to hook into an orchestration flow depend on `foundation:davinci-plugin` or `foundation:journey-plugin` — never on `:davinci` or `:journey` directly. This keeps the graph acyclic.
- Feature modules (`mfa/*`, `protect`, `recaptcha-enterprise`) sit above the plugin contracts.

### Extension model

Behavior is added to `Workflow` via `Module.of(::Config) { ... }`, not by subclassing `Workflow`. Each module declares a config class and registers lifecycle hooks (`initialize`, `start`, `next`, `response`, `signOff`). Hosts wire modules in via `WorkflowConfig.module(MyModule) { ... }`. Shared state across handlers goes through `SharedContext` (keyed via a `Keys` object).

```kotlin
val MyModule = Module.of(::MyConfig) {
    start { request ->
        request.header("X-My-Header", config.option)
        request
    }
    next { _, request -> request }
}
```

For DaVinci, form fields are modeled as `Collector` implementations. Register new collectors via `CollectorFactory.register("TYPE") { MyCollector() }` inside the module's setup block. Do not register them at the top level.

### Node sealed interface — outcome model

All authentication flow outcomes are expressed as a sealed `Node` interface. Use exhaustive `when` to handle every branch:

```text
ContinueNode   — flow in progress; call next() to proceed
SuccessNode    — authentication succeeded
ErrorNode      — server-reported error; user may retry
FailureNode    — unrecoverable error (network failure, unexpected response, etc.)
```

This replaced the legacy ForgeRock `NodeListener` callback model (`onSuccess` / `onException` / `onCallbackReceived`). Do not introduce new `NodeListener`-style callbacks.

## Kotlin & Coroutine Conventions

- All I/O-bound public APIs are `suspend` functions. Never wrap in `runBlocking` in library code.
- Use structured concurrency (`coroutineScope`, `supervisorScope`). No `GlobalScope`. No fire-and-forget `CoroutineScope(Dispatchers.IO).launch { ... }`.
- Use `Mutex` for guarded state mutation. Avoid `synchronized(...)` blocks.
- Reserve `Flow` for genuinely reactive streams. One-shot results are `suspend fun` returning a value, not `Flow<T>`.
- Long-running loops (e.g., polling) must call `coroutineContext.ensureActive()` before each iteration to be cancellation-cooperative.
- Use `Dispatchers.IO` for storage and network work; `Dispatchers.Default` for CPU-bound computation. Never hardcode a dispatcher in a public API — accept or propagate from caller context.
- Prefer `internal` for module-private APIs; declare `public` explicitly on the documented API surface.

## Networking / Persistence / Logging

**Networking.** All HTTP goes through `foundation:network`'s Ktor + CIO factory. Do not instantiate a raw `HttpClient` for ad-hoc calls. Do not introduce OkHttp or Retrofit.

**Persistence.** Use AndroidX `DataStore` via `foundation:storage` abstractions (`encryptedDataStorage`, `StorageDelegate`). Do not introduce Room, SQLDelight, Realm, or raw `SharedPreferences` outside of designated migration modules (`foundation:migration`, `mfa:binding-migration`, `mfa:auth-migration`).

**Logging.** Use `foundation:logger` (`Logger.i(...)`, `Logger.e(..., throwable)`). Never log:
- Tokens, authorization codes, or credentials of any kind
- Passwords or the value of any password-type collector
- PII: email addresses, phone numbers, names, device identifiers
- Full request or response bodies for authentication flows

Log structural facts: HTTP method, URL host (not path segments that may contain secrets), HTTP status code, timing.

## Branching & Commits

- Always branch off `develop`. Never commit directly to `develop` or `main`.
- All commits must be GPG-signed: `git commit -S`.
- Commit message format: `[TYPE] Short description of the changes`

| Type | When to use |
|------|-------------|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation changes only |
| `refactor` | Code restructuring with no behaviour change |
| `test` | Adding or modifying tests |

- Pull Requests target the `develop` branch. Fill out the PR template with what changed, why, and a link to the JIRA ticket.

## Dependency Pins

The root `build.gradle.kts` contains a `resolutionStrategy { force(...) }` block that pins several transitive dependencies to security-patched versions. These pins must not be relaxed without an explicit, reviewed instruction:

| Dependency | Pinned version | Reason |
|-----------|---------------|--------|
| `com.fasterxml.jackson.module:jackson-module-kotlin` | `2.15.0` | WS-2022-0468 (Dokka transitive) |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-xml` | `2.15.0` | WS-2022-0468 (Dokka transitive) |
| `com.fasterxml.jackson.core:jackson-databind` | `2.15.0` | WS-2022-0468 (Dokka transitive) |
| `io.netty:netty-codec` | `4.1.125.Final` | Security vulnerabilities (Mend SCA, Nov 2025) |
| `io.netty:netty-codec-http` | `4.1.125.Final` | Security vulnerabilities (Mend SCA, Nov 2025) |
| `io.netty:netty-codec-http2` | `4.1.125.Final` | Security vulnerabilities (Mend SCA, Nov 2025) |
| `io.netty:netty-all` | `4.1.125.Final` | Security vulnerabilities (Mend SCA, Nov 2025) |
| `com.google.protobuf:protobuf-java` | `4.29.2` | CVE-2024-7254 and related CVEs |
| `com.google.protobuf:protobuf-kotlin` | `4.29.2` | CVE-2024-7254 and related CVEs |
| `com.google.protobuf:protobuf-javalite` | `4.29.2` | CVE-2024-7254 and related CVEs |
| `com.google.protobuf:protobuf-kotlin-lite` | `4.29.2` | CVE-2024-7254 and related CVEs |
| `com.google.android.gms:play-services-basement` | `18.0.2` | CVE-2022-2390 |
| `com.nimbusds:nimbus-jose-jwt` | `10.5` | CVE-2025-53864 |

If an agent-proposed dependency upgrade would silently override one of these pins, surface it explicitly in the PR description for reviewer sign-off.

## Out of Scope for AI Edits

The following changes require explicit human instruction and should not be made autonomously:

- Adding per-module `AGENTS.md` files — the root file is the single source of agent context; per-module context comes from each module's `README.md`.
- Introducing backwards-compatibility shims, re-export wrappers, or `@Deprecated(replaceWith = ...)` chains without a complete migration plan.
- Adding Compose, AppCompat, or Material dependencies to any module other than `mfa:binding-ui`.
- Adding any new third-party dependency without first adding it to `gradle/libs.versions.toml` and referencing it via the `libs.<alias>` accessor.
- Using `runBlocking` in library code (tests are the only permitted exception).
- Using `GlobalScope` anywhere.
- Skipping commit signing (`--no-verify` or `-c commit.gpgsign=false`).
- Force-pushing to `develop` or `main`.

## Where to Look Next

- `README.md` — Quick project overview and module index with links to per-module quick start guides.
- `CONTRIBUTING.md` — Full contribution guidelines: environment setup, code standards, PR process.
- `MIGRATION.md` — ForgeRock Android SDK → Ping SDK migration guide (sealed `Node` vs. `NodeListener`, storage migration, etc.).
- Per-module `README.md` files — Module-specific setup, configuration examples, and API surface (e.g., `davinci/README.md`, `journey/README.md`, `mfa/oath/README.md`).

---

&copy; Copyright 2025-2026 Ping Identity Corporation. All rights reserved.
