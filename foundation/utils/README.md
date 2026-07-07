[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Utils Module

> **Internal module.** This module is not intended for direct use by application developers. It provides shared
> utilities consumed by other modules within the Ping Android SDK.

## Overview

The `utils` module contains lightweight helpers and common types used across the SDK:

- **`Result<Success, Failure>`** — A sealed interface representing either a successful value or a failure, used for
  explicit error handling throughout the SDK.
- **`ApiException`** — An exception type that carries an HTTP status code and response body, thrown when API calls
  return error responses.
- **`PingDsl`** — A `@DslMarker` annotation used to scope DSL builder functions and prevent implicit receiver leakage.
- **`FunctionExt`** — Extension functions for function composition (e.g., `andThen`).
- **`JSON`** — Extension functions for `JSONArray` iteration.
- **`LocaleListExtensions`** — Extension functions for locale list handling.
- **`TestModeDetector`** — Utility to detect if the code is running in a test environment.

## License

This software may be modified and distributed under the terms of the MIT license. See the LICENSE file for details.

© Copyright 2025-2026 Ping Identity Corporation. All rights reserved.
