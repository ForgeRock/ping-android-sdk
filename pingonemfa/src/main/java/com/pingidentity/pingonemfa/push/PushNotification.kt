package com.pingidentity.pingonemfa.push

import android.content.Context
import android.os.Parcelable
import com.pingidentity.android.ContextProvider
import com.pingidentity.pingidsdkv2.NotificationObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.util.UUID
import kotlin.coroutines.resume

/*
 * Simple model for a push notification. Implements Parcelable so it can be passed between components.
 */
@Parcelize
data class PushNotification(
    val id: String = UUID.randomUUID().toString(),
    val notificationObject: NotificationObject,
    val title: String?,
    val message: String?,
    val sentAt: Long = System.currentTimeMillis(),
    val respondedAt: Long? = null
): Parcelable {

    suspend fun approveNotification(
        context: Context,
        authenticationMethod: String,
        numberChallenge: Int? = null) : Result<Unit> = suspendCancellableCoroutine { cont ->
            try {
                notificationObject.approve(
                    context,
                    authenticationMethod,
                    numberChallenge
                ) { error ->
                    if (!cont.isActive) return@approve
                    if (error == null) {
                        cont.resume(Result.success(Unit))
                    } else {
                        println(error.userInfo.toString())
                        cont.resume(Result.failure(Exception(error.userInfo.toString())))
                    }
                }
            } catch (e: Exception) {
                if (cont.isActive) {
                    cont.resume(Result.failure(e))
                }
            }
        }

    suspend fun denyNotification(context: Context) : Result<Unit> = suspendCancellableCoroutine { cont ->
            try {
                notificationObject.deny(
                    context
                ) { error ->
                    if (!cont.isActive) return@deny
                    if (error == null) {
                        cont.resume(Result.success(Unit))
                    } else {
                        cont.resume(Result.failure(Exception(error.userInfo.toString())))
                    }
                }
            } catch (e: Exception) {
                if (cont.isActive) {
                    cont.resume(Result.failure(e))
                }
            }
        }

    fun requiresBiometric() : Boolean{
        return !isChallenge()
    }

    fun isChallenge() : Boolean{
        return notificationObject.numberMatchingType!=null
    }

    fun getNumbersChallenge(): IntArray? {
        return notificationObject.numberMatchingOptions
    }

    fun getPushType () : PushType {
        return when {
            notificationObject.isTest -> PushType.DRY
            notificationObject.numberMatchingType != null -> PushType.CHALLENGE
            else -> PushType.DEFAULT
        }
    }
}
