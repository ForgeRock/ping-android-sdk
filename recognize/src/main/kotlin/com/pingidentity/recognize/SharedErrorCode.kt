/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize

// The shared Recognize error model (version 6.0.0) and the Android native → shared mapping,
// as agreed across the web, Android, and iOS Recognize SDKs. Keep the tables below in sync
// with the shared model whenever it changes.

/**
 * The shared cross-platform Recognize error model, used identically by the web, Android,
 * and iOS Recognize SDKs.
 *
 * Each constant carries the stable numeric `code` and the stable `name` (the constant's
 * SCREAMING_SNAKE_CASE name) reported to the server in the `clientErrorCode` and `clientError`
 * input slots — the same values the iOS and JavaScript SDKs report, so server-side routing
 * behaves identically on every platform.
 *
 * [code] and `name` are mapped from the native Keyless SDK error code via [resolve]; native
 * codes without an official mapping fall back to [SDK_ERROR], and the original native code
 * remains available on the error as `sdkCode`. Native codes are an SDK detail: apps branch
 * on the shared model directly — compare `RecognizeException.name` / `code`, or use these
 * public constants as compile-time-checked values, e.g. `SharedErrorCode.CORE_USER_NOT_ENROLLED`.
 *
 * Only the shared codes the Keyless Android SDK can actually produce are declared here. The
 * shared model also defines web-only codes (`SDK_WEB_ASSEMBLY_*`, `CAMERA_NOT_FOUND`,
 * `CAMERA_NOT_SUPPORTED`, `CORE_NOT_ENOUGH_CIRCUITS`, `CORE_CUSTOMER_NOT_FOUND`, and the
 * `SERVER_*` family) that no Android native code maps to.
 */
enum class SharedErrorCode(val code: Int) {
    SDK_ERROR(1000),
    SDK_NOT_CONFIGURED(1001),
    SDK_INVALID_CONFIGURATION(1002),
    SDK_LOGGING_CONFIGURATION_FAILED(1003),
    SDK_STORAGE_FAILED(1004),
    SDK_USER_CANCELLED(1005),
    SDK_TIMEOUT(1006),
    SDK_NO_NETWORK_CONNECTION(1007),
    SDK_DYNAMIC_LINKING_PAYLOAD_MALFORMED(1008),
    SDK_ARTIFACT_RETRIEVE_FAILED(1009),
    SDK_INVALID_CLIENT_STATE(1010),
    SDK_OUTDATED_APP(1012),
    SDK_INVALID_CUSTOMER_PROPERTIES(1016),
    CAMERA_ERROR(2000),
    CAMERA_PERMISSION_DENIED(2002),
    CORE_ERROR(3000),
    CORE_NOT_ENOUGH_API_KEY_SEATS(3001),
    CORE_USER_ALREADY_ENROLLED(3002),
    CORE_USER_NOT_ENROLLED(3003),
    CORE_FACE_NOT_MATCHING(3004),
    CORE_SECRET_NOT_FOUND(3006),
    CORE_USER_LOCKED_OUT(3007),
    BIOM_ERROR(4000),
    BIOM_REJECTED(4001),
    BIOM_GENUINE_PRESENCE_NOT_ESTABLISHED(4002),
    BIOM_LIVENESS_ENVIRONMENT_AWARE_NOT_SUPPORTED(4003),
    BIOM_DEVICE_ENVIRONMENT_AWARE_NOT_SUPPORTED(4004),
    SECURITY_ERROR(6000),
    SECURITY_DEVICE_NOT_GENUINE(6001),
}

/**
 * Official mapping from the native Keyless SDK error code to [SharedErrorCode],
 * transcribed from the client-errors map (v6.0.0) shared across platforms.
 */
internal val nativeToShared: Map<Int, SharedErrorCode> = mapOf(
    10000 to SharedErrorCode.SDK_ERROR,
    10001 to SharedErrorCode.SDK_ARTIFACT_RETRIEVE_FAILED,
    10003 to SharedErrorCode.SDK_LOGGING_CONFIGURATION_FAILED,
    10004 to SharedErrorCode.CAMERA_ERROR,
    10005 to SharedErrorCode.SDK_STORAGE_FAILED,
    10006 to SharedErrorCode.SDK_INVALID_CUSTOMER_PROPERTIES,
    10100 to SharedErrorCode.BIOM_ERROR,
    10200 to SharedErrorCode.CORE_ERROR,
    20000 to SharedErrorCode.CORE_USER_NOT_ENROLLED,
    20001 to SharedErrorCode.CORE_USER_ALREADY_ENROLLED,
    20002 to SharedErrorCode.SDK_NOT_CONFIGURED,
    20010 to SharedErrorCode.SDK_INVALID_CONFIGURATION,
    20013 to SharedErrorCode.CORE_NOT_ENOUGH_API_KEY_SEATS,
    20021 to SharedErrorCode.BIOM_LIVENESS_ENVIRONMENT_AWARE_NOT_SUPPORTED,
    20022 to SharedErrorCode.BIOM_DEVICE_ENVIRONMENT_AWARE_NOT_SUPPORTED,
    20023 to SharedErrorCode.SDK_INVALID_CLIENT_STATE,
    20150 to SharedErrorCode.SDK_DYNAMIC_LINKING_PAYLOAD_MALFORMED,
    20300 to SharedErrorCode.CORE_SECRET_NOT_FOUND,
    30000 to SharedErrorCode.BIOM_GENUINE_PRESENCE_NOT_ESTABLISHED,
    30001 to SharedErrorCode.SDK_TIMEOUT,
    30003 to SharedErrorCode.SDK_USER_CANCELLED,
    30004 to SharedErrorCode.CORE_FACE_NOT_MATCHING,
    30005 to SharedErrorCode.SDK_NO_NETWORK_CONNECTION,
    30007 to SharedErrorCode.CORE_USER_LOCKED_OUT,
    30008 to SharedErrorCode.BIOM_REJECTED,
    30009 to SharedErrorCode.CAMERA_PERMISSION_DENIED,
    30010 to SharedErrorCode.SDK_OUTDATED_APP,
    40000 to SharedErrorCode.SECURITY_ERROR,
    40002 to SharedErrorCode.SECURITY_DEVICE_NOT_GENUINE,
)

/**
 * Resolves a native Keyless Android code to the shared error model.
 *
 * Unmapped native codes fall back to [SharedErrorCode.SDK_ERROR] — matching the web SDK's
 * fallback vocabulary. This also covers native codes absent from the shared mapping (e.g.
 * Keyless `20024` userProfileError, which the shared model does not define yet).
 */
internal fun resolve(nativeCode: Int): SharedErrorCode = nativeToShared[nativeCode] ?: SharedErrorCode.SDK_ERROR
