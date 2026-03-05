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
import com.pingidentity.pingidsdkv2.PingOneGeo
import com.pingidentity.pingidsdkv2.types.NotificationProvider
import com.pingidentity.pingonemfa.otp.OtpCodeInfo
import com.pingidentity.pingonemfa.push.PushApprovalService
import com.pingidentity.pingonemfa.push.PushNotification
import com.pingidentity.pingonemfa.util.AccountParser
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

object PingOneMFA {
    private val logger: Logger = Logger.logger
    private var isInitialized: Boolean = false
    private var lock = Mutex()

    //SDK must be initialized once and cannot handle parallel configure calls
    suspend fun initialize(): Result<Unit> = lock.withLock {
        if (isInitialized) {
            return Result.success(Unit)
        }
        suspendCancellableCoroutine { continuation ->
            try {
                PingOne.configure(
                    ContextProvider.context,
                    // for demonstration purposes we simply hardcode the North America geo
                    PingOneGeo.NORTH_AMERICA
                ) { error ->
                    continuation.resume(
                        error?.let {
                            logger.e("PingOne initialization failed: ${it.userInfo}")
                            Result.failure(PingOneMFAException(it.message))
                        } ?: run {
                            isInitialized = true
                            Result.success(Unit)
                        }
                    )
                }
            }catch (e: Exception){
                logger.e("PingOne initialization failed", e)
                continuation.resume(Result.failure(PingOneMFAException(e.message)))
            }
        }
    }

    /*
     * Registers push token with PingOne. Should be called each time the token is refreshed.
     */
    suspend fun register(pushToken: String) : Result<Unit> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                PingOne.setDeviceToken(
                    ContextProvider.context,
                    pushToken,
                    NotificationProvider.FCM
                ) { errors ->
                    val result =
                        errors
                            ?.firstOrNull { it != null }
                            ?.let { err ->
                                logger.e("PingOne push token registration failed: ${err.userInfo}")
                                Result.failure(PingOneMFAException(err.message))
                            }
                            ?: Result.success(Unit)

                    continuation.resume(result)

                }
            } catch (e : Exception) {
                logger.e("PingOne push token registration failed", e)
                continuation.resume(Result.failure(PingOneMFAException(e.message)))
            }
        }
    }

    /*
     * Starts pairing process with PingOne.
     */
    suspend fun pair(pairingKey: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            PingOne.pair(
                ContextProvider.context,
                pairingKey
            ) { _, error ->
                val result = error?.let { err ->
                    logger.e("PingOne pairing failed: ${err.userInfo}")
                    Result.failure(PingOneMFAException(err.message))
                } ?: Result.success(Unit)
                continuation.resume(result)
            }
        } catch (e: Exception) {
            logger.e("PingOne pairing failed", e)
            continuation.resume(Result.failure(PingOneMFAException(e.message)))
        }
    }

    /*
     * Retrieves all paired accounts from PingOne
     */
    suspend fun getAccounts(): Result<List<PingOneMfaAccount>> =
        suspendCancellableCoroutine { continuation ->
            try {
                PingOne.getInfo(
                    ContextProvider.context
                ) { deviceInfo, errors ->
                    val result = deviceInfo?.let {
                        Result.success(AccountParser().parseAccounts(it.toString()))
                    }?: run {
                        logger.e("PingOne getAccounts failed: ${errors.firstOrNull()?.userInfo}")
                        Result.failure(PingOneMFAException(errors.firstOrNull()?.message))
                    }
                    continuation.resume(result)
                }
            }catch (e: Exception){
                logger.e("PingOne getAccounts failed", e)
                continuation.resume(Result.failure(PingOneMFAException(e.message)))
            }
        }

    /*
     * Retrieves OTP code from PingOne.
     */
    suspend fun collectOtp(): Result<OtpCodeInfo> = suspendCancellableCoroutine { continuation ->
        try {
            PingOne.getOneTimePassCode(ContextProvider.context) { otpInfo, error ->
                val result = otpInfo?.let {
                    Result.success(
                        OtpCodeInfo(
                            otpInfo.passcode,
                            ((otpInfo.validUntil * 1000 - System.currentTimeMillis()) / 1000).toInt()
                        )
                    )
                }?: run {
                    logger.e("PingOne collectOtp failed: ${error?.userInfo}")
                    Result.failure(PingOneMFAException(error?.message))
                }
                continuation.resume(result)
            }
        } catch (e: Exception) {
            logger.e("PingOne collectOtp failed", e)
            continuation.resume(Result.failure(PingOneMFAException(e.message)))
        }
    }

    /*
     * Transforms received FCM Remote Message object from PingOne into PushNotification object
     */
    suspend fun collectPush(message: RemoteMessage): Result<PushNotification> =
        suspendCancellableCoroutine { continuation ->
            try {
                PingOne.processRemoteNotification(
                    ContextProvider.context,
                    message
                ) { notificationObject, error ->
                    val result = notificationObject?.let {
                        Result.success(
                            PushNotification(
                                notificationObject = notificationObject,
                                title = getTitleFromRemoteMessageData(message.data["aps"]),
                                message = getBodyFromRemoteMessageData(message.data["aps"])
                            )
                        )
                    }?: run {
                        logger.e("PingOne collectPush failed: ${error?.userInfo}")
                        Result.failure(PingOneMFAException(error?.message))
                    }
                    continuation.resume(result)
                }
            }catch (e: Exception){
                logger.e("PingOne collectPush failed", e)
                continuation.resume(Result.failure(PingOneMFAException(e.message)))
            }
        }

    /*
     * Retrieves mobile payload from PingOne.
     */
    suspend fun collectMobilePayload(): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            PingOne.generateMobilePayload(ContextProvider.context) { payload, error ->
                val result = payload?.let {
                    Result.success(payload)
                }?: run {
                    logger.e("PingOne collectMobilePayload failed: ${error?.userInfo}")
                    Result.failure(PingOneMFAException(error?.message))
                }
                continuation.resume(result)
            }
        }catch (e: Exception){
            logger.e("PingOne collectMobilePayload failed", e)
            continuation.resume(Result.failure(PingOneMFAException(e.message)))
        }
    }

    /*
     * Approves MFA push notification. Should be called from notification action if application is in the background.
     */
    fun approvePushNotificationFromBanner(notification: PushNotification){
        val appContext = ContextProvider.context
        val intent = Intent(appContext, PushApprovalService::class.java).apply {
            putExtra("notification", notification)
            putExtra("auth_method", "banner")
            putExtra("user_action", "approve")
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    /*
     * Denies MFA push notification. Should be called from notification action if application is in the background.
     */
    fun denyPushNotificationFromBanner(notification: PushNotification){
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