/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.RemoteMessage
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.types.NotificationProvider
import com.pingidentity.pingonemfa.otp.OtpCodeInfo
import com.pingidentity.pingonemfa.push.PushApprovalService
import com.pingidentity.pingonemfa.push.PushNotification
import com.pingidentity.pingonemfa.util.AccountParser
import com.pingidentity.pingonemfa.util.ErrorParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

/**
 * Entry point for all PingOne MFA operations.
 *
 * This is a singleton that wraps the native PingOne MFA SDK (`pingidsdkv2`) behind a
 * coroutine-friendly API. All native callback-based operations are bridged to `suspend`
 * functions that return [Result] — callers never need a try/catch.
 *
 * ## Lifecycle
 * Call [initialize] once at application startup before invoking any other function.
 * The call is guarded by a mutex and is idempotent — repeated calls after a successful
 * initialization return [Result.success] immediately.
 *
 * ## Error handling
 * All `suspend` functions return [Result.failure] wrapping a [PingOneMFAException] on error.
 * The native [com.pingidentity.pingidsdkv2.PingOneSDKError] type is never exposed.
 */
object PingOneMFA {
    private val logger: Logger = Logger.logger
    @Volatile
    private var isInitialized: Boolean = false
    private val lock = Mutex()

    /**
     * Configures the PingOne MFA SDK for the given service [geo] region.
     *
     * Must be called once at application startup before any other [PingOneMFA] call.
     * Subsequent calls after a successful initialization return [Result.success] immediately
     * without re-entering the native SDK. The call is mutex-guarded so parallel invocations
     * are safe — only one configure call will reach the native SDK.
     */
    suspend fun initialize(geo: Geo): Result<Unit> = lock.withLock {
        if (isInitialized) {
            return Result.success(Unit)
        }
        suspendCancellableCoroutine { continuation ->
            try {
                PingOne.configure(
                    ContextProvider.context,
                    geo.toPingOneGeo()
                ) { error ->
                    continuation.resume(
                        error?.let {
                            logger.e("PingOne initialization failed: ${it.userInfo}")
                            Result.failure(PingOneMFAException(it))
                        } ?: run {
                            isInitialized = true
                            Result.success(Unit)
                        }
                    )
                }
            } catch (e: Exception) {
                logger.e("PingOne initialization failed", e)
                continuation.resume(Result.failure(PingOneMFAException(e)))
            }
        }
    }

    /**
     * Registers or refreshes the FCM push [pushToken] with PingOne.
     *
     * Should be called each time Firebase delivers a new token via
     * `FirebaseMessagingService.onNewToken`, and immediately after [initialize] succeeds.
     *
     * The native SDK may attempt to register the token across multiple PingOne regions.
     * Returns [Result.success] when all registrations succeed (or the errors array contains only
     * nulls — which the SDK uses to indicate no real errors). Returns [Result.failure] wrapping a
     * [PingOneMFAException] if any region rejects the token; [PingOneMFAException.internalErrorsList]
     * will contain one [Error] per failed region for diagnostic logging.
     */
    suspend fun setDeviceToken(pushToken: String) : Result<Unit> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                PingOne.setDeviceToken(
                    ContextProvider.context,
                    pushToken,
                    NotificationProvider.FCM
                ) { errors ->
                    if (errors.isNullOrEmpty() || errors.all { it == null }) {
                        continuation.resume(Result.success(Unit))
                    } else {
                        logger.e("PingOne push token registration failed: ${errors.firstOrNull { it != null }?.userInfo}")
                        continuation.resume(Result.failure(PingOneMFAException(errors)))
                    }
                }
            } catch (e : Exception) {
                logger.e("PingOne push token registration failed", e)
                continuation.resume(Result.failure(PingOneMFAException(e)))
            }
        }
    }

    /**
     * Pairs the device with a PingOne MFA account using [pairingKey].
     *
     * The pairing key is typically obtained by scanning a QR code or from a DaVinci flow.
     * On success, the account becomes available via [getDeviceInfo].
     */
    suspend fun pair(pairingKey: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            PingOne.pair(
                ContextProvider.context,
                pairingKey
            ) { _, error ->
                val result = error?.let { err ->
                    logger.e("PingOne pairing failed: ${err.userInfo}")
                    Result.failure(PingOneMFAException(err))
                } ?: Result.success(Unit)
                continuation.resume(result)
            }
        } catch (e: Exception) {
            logger.e("PingOne pairing failed", e)
            continuation.resume(Result.failure(PingOneMFAException(e)))
        }
    }

    /**
     * Returns metadata for all currently paired PingOne MFA accounts.
     *
     * PingOne MFA supports multiple service regions. The native SDK queries all regions and
     * aggregates the results. If some regions succeed and others fail, the SDK may return both
     * account data and error context in the same callback.
     *
     * On [Result.success], the value is a [Pair] where:
     * - `first` — the aggregated list of [PingOneMfaAccount] objects across all regions.
     * - `second` — an optional `List<Error>` with per-region diagnostic errors from the SDK.
     *   Non-null only when the SDK returned partial error context alongside valid data.
     *   **Developer use only** — log these for debugging but do not surface them to users;
     *   the account list is still valid when this list is present.
     *
     * On [Result.failure], all regions failed or no data was returned. The [PingOneMFAException]
     * contains per-region failure details in [PingOneMFAException.internalErrorsList].
     *
     * ```kotlin
     * PingOneMFA.getDeviceInfo()
     *     .onSuccess { (accounts, diagnosticErrors) ->
     *         showAccounts(accounts)
     *         diagnosticErrors?.forEach { err ->
     *             Log.w("MFA", "Region diagnostic: code=${err.code} info=${err.userInfo}")
     *         }
     *     }
     *     .onFailure { e ->
     *         Log.e("MFA", "Failed to load accounts: ${e.message}")
     *         (e as? PingOneMFAException)?.internalErrorsList?.forEach { err ->
     *             Log.e("MFA", "Region failure: code=${err.code} info=${err.userInfo}")
     *         }
     *     }
     * ```
     */
    suspend fun getDeviceInfo(): Result<Pair<List<PingOneMfaAccount>, List<Error>?>> =
        suspendCancellableCoroutine { continuation ->
            try {
                PingOne.getInfo(
                    ContextProvider.context
                ) { deviceInfo, errors ->
                    val result = when {
                        // Data available — return it along with any diagnostic errors from the SDK.
                        deviceInfo != null && !deviceInfo.isEmpty -> Result.success(
                            Pair(
                                AccountParser.parseAccounts(deviceInfo.toString()),
                                ErrorParser.fromPingOneSDKErrors(errors)
                            )
                        )
                        // No data and at least one real error — treat as failure.
                        errors.any { it != null } -> {
                            logger.e("PingOne getDeviceInfo failed: ${errors.firstOrNull { it != null }?.userInfo}")
                            Result.failure(PingOneMFAException(errors))
                        }
                        // Neither data nor errors — SDK misbehaved; avoid hanging the coroutine.
                        else -> {
                            logger.e("PingOne getDeviceInfo failed: no data and no error")
                            Result.failure(PingOneMFAException(Exception("getDeviceInfo failed: no error details provided")))
                        }
                    }
                    continuation.resume(result)
                }
            } catch (e: Exception) {
                logger.e("PingOne getDeviceInfo failed", e)
                continuation.resume(Result.failure(PingOneMFAException(e)))
            }
        }

    /**
     * Returns the current one-time passcode and its remaining validity window.
     *
     * [OtpCodeInfo.secondsRemaining] is a snapshot at call time, clamped to `0` if already
     * expired. Re-call this function when the countdown reaches zero to receive the next code.
     */
    suspend fun getOneTimePasscode(): Result<OtpCodeInfo> = suspendCancellableCoroutine { continuation ->
        try {
            PingOne.getOneTimePassCode(ContextProvider.context) { otpInfo, error ->
                val result = otpInfo?.let {
                    Result.success(
                        OtpCodeInfo(
                            otpInfo.passcode,
                            maxOf(
                                0,
                                ((otpInfo.validUntil * 1000 - System.currentTimeMillis()) / 1000).toInt()
                            )
                        )
                    )
                } ?: run {
                    /*
                     * otpInfo is null but error may also be null if the SDK misbehaves;
                     * fall back to a generic exception so the coroutine is never left hanging
                     */
                    logger.e("PingOne getOneTimePasscode failed: ${error?.userInfo}")
                    Result.failure(error?.let {
                        PingOneMFAException(it)
                    } ?: PingOneMFAException(Exception("getOneTimePasscode failed: no error details provided"))
                    )
                }
                continuation.resume(result)
            }
        } catch (e: Exception) {
            logger.e("PingOne getOneTimePasscode failed", e)
            continuation.resume(Result.failure(PingOneMFAException(e)))
        }
    }

    /**
     * Converts an incoming FCM [message] into a typed [PushNotification].
     *
     * Call this from `FirebaseMessagingService.onMessageReceived` when the message data
     * contains the `"PingOne"` key. The resulting [PushNotification] provides [PushNotification.getPushType],
     * [PushNotification.approveNotification], [PushNotification.denyNotification], and
     * [PushNotification.isCancelAuthentication] for the full push response lifecycle.
     *
     * Returns `Result.success(null)` when the native SDK returns both nulls — this indicates
     * the message was a "silent" (extra verification) PingOne MFA push and should be silently ignored by the caller.
     */
    suspend fun processRemoteNotification(message: RemoteMessage): Result<PushNotification?> =
        suspendCancellableCoroutine { continuation ->
            try {
                PingOne.processRemoteNotification(
                    ContextProvider.context,
                    message
                ) { notificationObject, error ->
                    val result = when {
                        notificationObject != null -> Result.success(
                            PushNotification(
                                notificationObject = notificationObject,
                                /*
                                 * Parse title and message from the "aps" field in the FCM data
                                 * payload, which contains the original FCM payload sent by PingOne.
                                 */
                                title = getTitleFromRemoteMessageData(message.data["aps"]),
                                message = getBodyFromRemoteMessageData(message.data["aps"])
                            )
                        )
                        error != null -> {
                            logger.e("PingOne processRemoteNotification failed: ${error.userInfo}")
                            Result.failure(PingOneMFAException(error))
                        }
                        // Both null — a silent PingOne MFA message; no action needed.
                        else -> Result.success(null)
                    }
                    continuation.resume(result)
                }
            } catch (e: Exception) {
                logger.e("PingOne processRemoteNotification failed", e)
                continuation.resume(Result.failure(PingOneMFAException(e)))
            }
        }

    /**
     * Generates a cryptographic mobile payload string from the native PingOne MFA SDK.
     *
     * The payload is intended for submission to a server-side authentication flow (e.g. DaVinci).
     * Phase 1 exposes only the raw payload string — binding it to a Collector or continuation
     * node is the responsibility of the calling layer.
     */
    suspend fun generateMobilePayload(): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            PingOne.generateMobilePayload(ContextProvider.context) { payload, error ->
                val result = payload?.let {
                    Result.success(payload)
                } ?: run {
                    /*
                     * payload is null but error may also be null if the SDK misbehaves;
                     * fall back to a generic exception so the coroutine is never left hanging
                     */
                    logger.e("PingOne generateMobilePayload failed: ${error?.userInfo}")
                    Result.failure(error?.let {
                        PingOneMFAException(it)
                    } ?: PingOneMFAException(Exception("generateMobilePayload failed: no error details provided"))
                    )
                }
                continuation.resume(result)
            }
        } catch (e: Exception) {
            logger.e("PingOne generateMobilePayload failed", e)
            continuation.resume(Result.failure(PingOneMFAException(e)))
        }
    }

    /**
     * Approves the push authentication request represented by [notification] when the app is in
     * the background (e.g. the user tapped Approve on the system notification banner).
     *
     * Starts [PushApprovalService] as a foreground service so the network call is permitted
     * under Android's background execution restrictions. The outcome is not surfaced back to
     * the UI — add a custom broadcast or shared state if your app needs to react to it.
     */
    fun approvePushNotificationFromBanner(notification: PushNotification) {
        val appContext = ContextProvider.context
        val intent = Intent(appContext, PushApprovalService::class.java).apply {
            putExtra("notification", notification)
            putExtra("auth_method", "banner")
            putExtra("user_action", "approve")
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    /**
     * Denies the push authentication request represented by [notification] when the app is in
     * the background (e.g. the user tapped Deny on the system notification banner).
     *
     * Starts [PushApprovalService] as a foreground service so the network call is permitted
     * under Android's background execution restrictions. The outcome is not surfaced back to
     * the UI — add a custom broadcast or shared state if your app needs to react to it.
     */
    fun denyPushNotificationFromBanner(notification: PushNotification) {
        val appContext = ContextProvider.context
        val intent = Intent(appContext, PushApprovalService::class.java).apply {
            putExtra("notification", notification)
            putExtra("auth_method", "banner")
            putExtra("user_action", "deny")
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun getTitleFromRemoteMessageData(data: String?): String? =
        data?.let {
            Json.parseToJsonElement(it)
                .jsonObject["alert"]
                ?.jsonObject
                ?.get("title")
                ?.jsonPrimitive
                ?.contentOrNull
        }

    private fun getBodyFromRemoteMessageData(data: String?): String? =
        data?.let {
            Json.parseToJsonElement(it)
                .jsonObject["alert"]
                ?.jsonObject
                ?.get("body")
                ?.jsonPrimitive
                ?.contentOrNull
        }

}
