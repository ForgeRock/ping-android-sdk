---
name: review-conventions
description: Use when reviewing code in this repo — describes what to check and what to flag
---

Security & encryption: tokens/keys only via EncryptedDataStoreStorage (AES-256-GCM), never plain prefs.
Crypto uses KeyStore (RSA-OAEP, AES/GCM, HMAC-SHA256) — verify StrongBox and biometric invalidation configs.
All HTTP through foundation/network HttpClient — never OkHttp or Retrofit.

Async: all I/O is suspend + Flow. No main-thread blocking (.blocking(), runBlocking()).
ViewModels use viewModelScope; tests use runTest{} with test dispatcher.

Errors: sealed Result / OidcError return types, not raw exceptions. Propagate via Result or Flow; catch only at ViewModel/UI boundary.

Logging: foundation/logger Logger only — no android.util.Log.

Module plugins: DaVinci/Journey registered via module(PluginKey){} blocks. Plugins injected, not hard-coded.

Kotlin style: prefer val, data class for DTOs, sealed class/interface for state.

Build: all versions in gradle/libs.versions.toml — no hardcoded strings. Security force-pins (netty, protobuf, nimbus-jose-jwt) must not be removed. New Android libs must apply com.pingidentity.convention.android.library.

Tests: new public APIs need unit tests. Mock HTTP with ktor-client-mock.

Each new file needs the copyright header (2025-2026, MIT license).
Each PR needs a JIRA ticket link (SDKS-XXXX).
