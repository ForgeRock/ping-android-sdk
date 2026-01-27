package com.pingidentity.pingonemfa.push

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * Service for handling push notifications actions. This service exists to solve ONE specific Android restriction:
 * Android does NOT allow network calls in the background (from notification actions in particular).
 */
internal class PushApprovalService : Service(){

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(p0: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createForegroundNotification())

        val notificationObject =
            intent?.getParcelableExtra("notification", PushNotification::class.java)
        val authMethod = intent?.getStringExtra("auth_method") ?: ""
        val userAction = intent?.getStringExtra("user_action") ?: ""
        if (notificationObject == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            try {
                if (userAction.equals("approve", ignoreCase = true)) {
                    approveWithClosedSdk(notificationObject, authMethod)
                }else{
                    denyWithClosedSdk(notificationObject)
                }
            } catch (e: Exception) {
                Log.e("MfaApprovalService", "approval failed: ${e.message}", e)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private fun createForegroundNotification(): Notification {
        val channelId = "mfa_approval_channel"
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                channelId,
                "MFA Approvals",
                NotificationManager.IMPORTANCE_MAX
            )
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Approving login…")
            .setContentText("Contacting server")
            .setSmallIcon(R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    private suspend fun approveWithClosedSdk(
        notification: PushNotification,
        auth: String
    ) = suspendCancellableCoroutine { cont ->
        try {
            notification.notificationObject.approve(
                this,
                auth,
                null
            ) { error ->
                if (!cont.isActive) return@approve
                if (error == null) cont.resume(Unit)
                else cont.resumeWithException(Exception(error.message ?: "Approval failed"))
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }
    private suspend fun denyWithClosedSdk(
        notification: PushNotification
    ) = suspendCancellableCoroutine { cont ->
        try {
            notification.notificationObject.deny(
                this){ error ->
                if (!cont.isActive) return@deny
                if (error == null) cont.resume(Unit)
                else cont.resumeWithException(Exception(error.message ?: "Deny action failed"))
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 7001
    }

}