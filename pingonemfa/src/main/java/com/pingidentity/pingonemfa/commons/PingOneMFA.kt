package com.pingidentity.pingonemfa.commons

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.RemoteMessage
import com.pingidentity.android.ContextProvider
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneGeo
import com.pingidentity.pingidsdkv2.types.NotificationProvider
import com.pingidentity.pingonemfa.otp.OtpCodeInfo
import com.pingidentity.pingonemfa.push.PushApprovalService
import com.pingidentity.pingonemfa.push.PushNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object PingOneMFA {

    private var isInitialized: Boolean = false
    private var lock = Mutex()

    //SDK must be initialized once and cannot handle parallel configure calls
    suspend fun initialize(): Unit = lock.withLock {
        if (isInitialized) {
            return
        }
        return suspendCancellableCoroutine { init ->
            PingOne.configure(
                ContextProvider.context,
                PingOneGeo.NORTH_AMERICA
            ) { error ->
                if (error == null) {
                    isInitialized = true
                    init.resume(Unit)
                }else{
                    init.resumeWithException(PingOneMFAException(error.message))
                }
            }
        }
    }

    /*
     * Registers push token with PingOne. Should be called each time the token is refreshed.
     */
    suspend fun register(pushToken: String) : Boolean = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            try {
                PingOne.setDeviceToken(
                    ContextProvider.context,
                    pushToken,
                    NotificationProvider.FCM
                ) { errors ->
                    val success = errors == null || errors.isEmpty() || errors.all { it == null }
                    if (cont.isActive) {
                        cont.resume(success)
                    }
                }
            } catch (_: Exception) {
                if (cont.isActive) {
                    cont.resume(false)
                }
            }
        }
    }

    /*
     * Starts pairing process with PingOne.
     */
    suspend fun pair(pairingKey: String): Result<Unit> = suspendCancellableCoroutine { cont ->
        try {
            PingOne.pair(
                ContextProvider.context,
                pairingKey
            ) { pairingInfo, error ->
                if (!cont.isActive) {
                    return@pair
                }
                if (error == null) {
                    cont.resume(Result.success(Unit))
                } else {
                    cont.resume(Result.failure(Exception(error.message)))
                }
            }
        } catch (e: Exception) {
            if (cont.isActive) {
                cont.resume(Result.failure(e))
            }
        }
    }

    /*
     * Retrieves all paired accounts from PingOne
     */
    suspend fun getAccounts(): Result<List<PingOneMfaAccount>> =
        suspendCancellableCoroutine { cont ->
            try {
                PingOne.getInfo(
                    ContextProvider.context
                ) { deviceInfo, errors ->
                    run {
                        if (!cont.isActive) return@getInfo
                        if (deviceInfo!= null){
                            val accounts = AccountParser().parseAccounts(deviceInfo)
                            cont.resume(Result.success(accounts))
                        }else{
                            cont.resume(Result.failure(PingOneMFAException(errors[0]?.message)))
                        }
                    }
                }
            }catch (e: Exception){
                if (cont.isActive) {
                    cont.resume(Result.failure(e))
                }
            }
        }

    suspend fun collectOtp(): Result<OtpCodeInfo> = suspendCancellableCoroutine { cont ->
        PingOne.getOneTimePassCode(ContextProvider.context) { otpInfo, error ->
            if (!cont.isActive) return@getOneTimePassCode
            val result = if (otpInfo != null) {
                Result.success(OtpCodeInfo(
                    otpInfo.passcode,
                    ((otpInfo.validUntil * 1000 - System.currentTimeMillis()) / 1000).toInt()
                ))
            } else {
                Result.failure(PingOneMFAException(error?.message))
            }
            cont.resume(result)
        }
    }

    /*
     * Transforms received FCM Remote Message object from PingOne into PushNotification object
     */
    suspend fun collectPush(message: RemoteMessage): Result<PushNotification> =
        suspendCancellableCoroutine { cont ->
            try {
                PingOne.processRemoteNotification(
                    ContextProvider.context,
                    message
                ) { notificationObject, error ->
                    if (!cont.isActive) return@processRemoteNotification
                    if (notificationObject != null) {
                        cont.resume(
                            Result.success(
                                PushNotification(
                                    notificationObject = notificationObject,
                                    title = getTitleFromRemoteMessageData(message.data["aps"]),
                                    message = getBodyFromRemoteMessageData(message.data["aps"])
                                )
                            )
                        )
                        return@processRemoteNotification
                    }
                    cont.resume(Result.failure(PingOneMFAException(error?.message)))
                }
            }catch (e: Exception){
                if (cont.isActive) {
                    cont.resume(Result.failure(PingOneMFAException(e.message)))
                }
            }
        }

    fun approvePushNotificationFromBanner(notification: PushNotification?){
        val appContext = ContextProvider.context
        val intent = Intent(appContext, PushApprovalService::class.java).apply {
            putExtra("notification", notification)
            putExtra("auth_method", "banner")
            putExtra("user_action", "approve")
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun denyPushNotificationFromBanner(notification: PushNotification?){
        val appContext = ContextProvider.context
        val intent = Intent(appContext, PushApprovalService::class.java).apply {
            putExtra("notification", notification)
            putExtra("auth_method", "banner")
            putExtra("user_action", "deny")
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun getTitleFromRemoteMessageData(data: String?): String?{
        return if (data == null) {
            null
        }else{
            JSONObject(data).getJSONObject("alert").getString("title")
        }
    }

    private fun getBodyFromRemoteMessageData(data: String?): String?{
        return if (data == null) {
            null
        }else{
            JSONObject(data).getJSONObject("alert").getString("body")
        }
    }
}