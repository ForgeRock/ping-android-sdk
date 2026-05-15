---
name: ios-to-android-port
description: >
  Ports a feature from the Ping iOS SDK (https://github.com/ForgeRock/ping-ios-sdk) to this
  Android SDK. Use this skill whenever the user wants to port, migrate, translate, or implement
  an iOS SDK feature on Android — even if they just say "implement this iOS branch on Android",
  "port SDKS-XXXX from iOS", "apply the iOS changes to Android", or similar. This is the
  primary way new features get added to this Android SDK.
---

# iOS → Android SDK Feature Port

## What this skill does

Given an iOS feature branch or PR, produce a draft Android implementation following the
established patterns of this repository. The output is a first draft for the engineer to
review and refine — not necessarily production-ready on its own.

## Repos

- **iOS SDK**: https://github.com/ForgeRock/ping-ios-sdk (Swift, SPM)
- **Android SDK**: current working directory (Kotlin, Gradle)

## Module mapping

| iOS module | Android module |
|---|---|
| `Oidc/` | `foundation/oidc/` |
| `Davinci/` | `davinci/` |
| `Journey/` | `journey/` |
| `Network/` | `foundation/network/` |
| `Storage/` | `foundation/storage/` |
| `Browser/` | `foundation/browser/` |
| `Orchestrate/` | `foundation/orchestrate/` |
| `Logger/` | `foundation/logger/` |
| `DeviceClient/` | `foundation/device/device-client/` |
| `DeviceId/` | `foundation/device/device-id/` |
| `DeviceProfile/` | `foundation/device/device-profile/` |

Class and file names are intentionally kept in sync between platforms.

## Step 1 — Fetch the iOS diff

Use the GitHub API to get the diff for the feature branch or PR:

**For a branch:**
```
https://api.github.com/repos/ForgeRock/ping-ios-sdk/compare/develop...{branch-name}
```

**For a PR:**
```
https://api.github.com/repos/ForgeRock/ping-ios-sdk/pulls/{pr-number}/files
```

If the user gives you a JIRA ticket (e.g., `SDKS-4235`), search for a branch whose name
contains that ticket number:
```
https://api.github.com/repos/ForgeRock/ping-ios-sdk/branches?per_page=100
```

Fetch the full content of each changed file (raw GitHub URLs work):
```
https://raw.githubusercontent.com/ForgeRock/ping-ios-sdk/{branch}/path/to/File.swift
```

## Step 2 — Understand the iOS changes

Read through the Swift diff carefully. For each changed or new file, identify:

- **Purpose**: what problem does this class/function solve?
- **Dependencies**: what other iOS types/modules does it use?
- **Patterns**: sealed types, async/await, Codable, Result, protocols, extensions

Look for the *intent*, not just the syntax. The Android implementation should express the
same semantics in idiomatic Kotlin — not be a line-by-line translation.

## Step 3 — Map to Android equivalents

Apply these translation patterns:

| Swift | Kotlin |
|---|---|
| `async func` | `suspend fun` |
| `AsyncStream` / `AsyncSequence` | `Flow<T>` |
| `struct` + `Codable` | `@Serializable data class` |
| `sealed enum` with associated values | `sealed class` with subclasses |
| `enum` (simple) | `enum class` |
| `Result<T, Error>` | `Result<T>` or custom sealed error type |
| `protocol` | `interface` |
| `extension` | extension function / companion object |
| `@Published var` / `ObservableObject` | `StateFlow` / `ViewModel` |
| `URLSession` / `Alamofire` | Ktor HTTP client (via `foundation/network`) |
| `UserDefaults` / Keychain | `EncryptedDataStoreStorage` / `MemoryStorage` |
| `DispatchQueue` | coroutine dispatcher |
| `class Foo: Bar, Baz` | `class Foo(…) : Bar, Baz` |
| `guard let x = y else { return }` | `val x = y ?: return` |
| `if let` / `guard let` | `?.let { }` / `?: run { }` |
| `weak var delegate` | callback lambda or `WeakReference` |
| `init()` | `constructor()` / `init { }` block |
| `static func` | `companion object` function |
| `@objc` / `open` | `open` class / `@JvmOverloads` |

## Step 4 — Read the existing Android code

Before writing anything, read the Android files that are most likely to change:

1. Map each iOS file to its Android counterpart using the module mapping above.
2. Read those existing files to understand their current shape — naming, existing fields,
   coding conventions already in place.
3. Check `Constants.kt` in each affected module for existing constants to reuse.
4. Check `build.gradle.kts` for the affected module to understand existing dependencies.

Do not add dependencies that aren't already present unless the iOS feature truly requires
something new. When in doubt, look at how existing similar features are built in the repo.

## Step 5 — Implement

Write the Android code following these rules:

- **Follow the existing patterns in this repo** — look at neighbouring files and match style.
- **Prefer `val` over `var`; prefer immutability.**
- **Use `sealed class` / `sealed interface` for state and error models.**
- **All I/O is suspend / Flow** — never block.
- **HTTP goes through `foundation/network`** — no OkHttp, no Retrofit, no new Ktor deps.
- **Logging via `Logger` interface** — never `android.util.Log` directly.
- **Storage via `EncryptedDataStoreStorage`** — never hard-code a storage impl.
- **No hardcoded version strings in Gradle** — use the version catalog.
- **Copyright header on every new file:**

```kotlin
/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
```

For sample app UI, use Jetpack Compose.

## Step 6 — Write tests

Every new public API needs unit tests. Look at the iOS test files for inspiration on what
scenarios to cover, then write equivalent tests in Kotlin:

- JUnit 4 + MockK for mocking
- `runTest { }` from `kotlinx-coroutines-test` for suspend functions and Flows
- `ktor-client-mock` for HTTP layer tests
- Tests go in `src/test/kotlin/...` (unit) or `src/androidTest/kotlin/...` (instrumented)

Match the test file naming: `FooTest.kt` for `Foo.kt`.

## Step 7 — Report your work

After writing all files, give the user a summary:

1. **Files created / modified** — list each with a one-line description
2. **Deviations from iOS** — anything you implemented differently and why
3. **Things to review** — areas where you made a judgment call or where the iOS intent
   was ambiguous
4. **Not implemented** — iOS-side things you skipped (UI-only, iOS-specific APIs, etc.)
5. **Run the tests** — tell the user what command to run:
   ```sh
   ./gradlew :foundation:oidc:testDebugUnitTest
   ```
   (adjust module path as appropriate)

## What NOT to do

- Don't port iOS-specific APIs: `UIKit`, `AVFoundation`, `ASWebAuthenticationSession`,
  `SecRandomCopyBytes`, etc. Use Android equivalents.
- Don't add Hilt/Dagger — DI is manual/constructor injection.
- Don't add OkHttp or Retrofit — use Ktor.
- Don't use `android.util.Log` — use the SDK Logger.
- Don't hardcode version strings in Gradle.
- Don't remove the forced security dependency overrides in the root `build.gradle.kts`.
- Don't port UI samples if the user only asked for the SDK feature.
