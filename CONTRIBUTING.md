# Contributing to the Ping Orchestration SDK for Android

Thank you for considering contributing to the Ping Orchestration SDK for Android! We appreciate your time and effort.

This document outlines the guidelines for contributing to this project. Please read it before getting started.

## 1. Setting Up Your Development Environment

### Prerequisites

- A GitHub account with Git installed locally.
- The latest stable version of [Android Studio](https://developer.android.com/studio).
- Android API level 29 or higher.
- A PingOne and Ping AIC tenants or PingAM instance — see the [documentation](https://developer.pingidentity.com/orchsdks) for setup instructions.

### Fork and Clone the Repository

1. **Fork the repository** on GitHub.
2. **Clone your fork** locally:
    ```sh
    git clone https://github.com/YOUR_USERNAME/ping-android-sdk.git
    cd ping-android-sdk
    ```
3. **Open the project** in Android Studio. Gradle will resolve dependencies automatically.
4. **Verify the build** before making any changes:
    ```sh
    ./gradlew clean build
    ```

## 2. Project Structure

The SDK is organised into focused modules. For a full description of each module, see the [Modules](./README.md#modules) section in the README.

## 3. Building and Testing

Before submitting any changes, ensure the project builds and all tests pass.

### Run all unit tests

```sh
./gradlew --rerun-tasks testDebugUnitTestCoverage --stacktrace --no-daemon
```

### Run tests for a specific module

You can target individual modules to speed up your feedback loop. Replace `<module>` with the Gradle path shown in the project structure above (e.g. `:davinci`, `:foundation:oidc`, `:mfa:oath`).

```sh
./gradlew :<module>:testDebugUnitTestCoverage --stacktrace --no-daemon
```

Some common examples:

```sh
# Core orchestration and OIDC
./gradlew :foundation:orchestrate:testDebugUnitTestCoverage --no-daemon
./gradlew :foundation:oidc:testDebugUnitTestCoverage --no-daemon

# DaVinci and Journey
./gradlew :davinci:testDebugUnitTestCoverage --no-daemon
./gradlew :journey:testDebugUnitTestCoverage --no-daemon

# MFA
./gradlew :mfa:oath:testDebugUnitTestCoverage --no-daemon
./gradlew :mfa:push:testDebugUnitTestCoverage --no-daemon
./gradlew :mfa:binding:testDebugUnitTestCoverage --no-daemon

# Protect
./gradlew :protect:testDebugUnitTestCoverage --no-daemon
```

Ensure that all existing tests pass and that you add new tests for any new functionality you introduce.

## 4. Build API Reference Documentation

The project uses [Dokka](https://kotlinlang.org/docs/dokka-introduction.html) to generate API reference documentation. Run the following command from the root of the project:

```sh
./gradlew dokkaGenerate
```

The generated HTML output will be placed in `build/dokka/`.

## 5. Standards of Practice

This project follows the internal standards maintained by the Ping Identity SDK team. Please review and adhere to these guidelines before submitting any code:

- [Android Style Guide](https://github.com/ForgeRock/sdk-standards-of-practice/blob/main/code-style/android-styleguide.md)
- [Android Security Guidelines](https://github.com/ForgeRock/sdk-standards-of-practice/blob/main/security/security-guidelines-android.md)

In general, try to match the style of the existing code in the project.

## 6. Submitting a Pull Request

### 1. Create a new branch

Always branch off from `develop`. Avoid committing directly to `develop` or `main`.

```sh
git checkout -b feature/my-new-feature
```

### 2. Make and commit your changes

Write clean, readable code. Add tests for new functionality and update documentation where relevant.

Commits must be **signed**. See the [GitHub docs](https://docs.github.com/en/authentication/managing-commit-signature-verification/signing-commits) for setup instructions.

Use the following commit message format:

```
[TYPE] Short description of the changes
```

| Type | When to use |
|------|-------------|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation changes only |
| `refactor` | Code restructuring with no behaviour change |
| `test` | Adding or modifying tests |

Example:

```sh
git commit -S -m "feat: add TOTP token refresh support"
```

### 3. Push and open a Pull Request

```sh
git push origin feature/my-new-feature
```

Open a Pull Request targeting the `develop` branch of the original repository. Fill out the PR template, which includes:

- A clear description of **what** was changed and **why**.
- A link to the related JIRA ticket, if applicable.
- Any relevant context, screenshots, or notes about breaking changes.

Your PR will be reviewed by the project maintainers. Be prepared to address feedback and keep your branch up to date with `develop`.

## License

By contributing to the Ping Identity Android SDK, you agree that your contributions will be licensed under the [MIT License](LICENSE) that covers the project.

&copy; Copyright 2025-2026 Ping Identity Corporation. All Rights Reserved.
