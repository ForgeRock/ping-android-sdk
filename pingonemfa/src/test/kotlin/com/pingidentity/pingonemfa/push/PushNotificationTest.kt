package com.pingidentity.pingonemfa.push

import android.content.Context
import com.pingidentity.pingidsdkv2.NotificationObject
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneSDKError
import com.pingidentity.pingonemfa.commons.PingOneMFAException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushNotificationTest {
    private val context = mockk<Context>(relaxed = true)
    private val notificationObject = mockk<NotificationObject>(relaxed = true)

    @Test
    fun `approveNotification returns success when callback error is null`() = runTest {
        every {
            notificationObject.approve(any(), any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKCallback>(3)
            callback.onComplete(null)
        }

        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
        )

        val result = push.approveNotification(context, "banner")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `approveNotification returns error when callback error is not null`() = runTest {
        every {
            notificationObject.approve(any(), any(), any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKCallback>(3)
            callback.onComplete(PingOneSDKError(1003, "mock"))
        }
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
        )
        val result = push.approveNotification(context, "banner")
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertTrue { result.exceptionOrNull()?.message == "mock" }
    }
    @Test
    fun `approveNotification returns failure when exception is thrown`() = runTest {
        every {
            notificationObject.approve(any(), any(), any(), any())
        } throws RuntimeException("Simulated Network Error")

        val push = PushNotification(
            notificationObject = notificationObject,
            title = null,
            message = null
        )

        val result = push.approveNotification(context, "banner")

        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is Exception }
        assertTrue { result.exceptionOrNull()?.message == "Simulated Network Error" }
    }

    @Test
    fun `denyNotification returns success when callback error is null`() = runTest {
        every {
            notificationObject.deny(any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKCallback>(1)
            callback.onComplete(null)
        }
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
        )
        val result = push.denyNotification(context)
        assertTrue(result.isSuccess)

    }
    @Test
    fun `denyNotification returns error when callback error is not null`() = runTest {
        every {
            notificationObject.deny(any(), any())
        } answers {
            val callback = arg<PingOne.PingOneSDKCallback>(1)
            callback.onComplete(PingOneSDKError(1003, "mock"))
        }
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
        )
        val result = push.denyNotification(context)
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is PingOneMFAException }
        assertTrue { result.exceptionOrNull()?.message == "mock" }
    }

    @Test
    fun `denyNotification returns failure when exception is thrown`() = runTest {
        every {
            notificationObject.deny(any(), any())
        } throws RuntimeException("Simulated Network Error")

        val push = PushNotification(
            notificationObject = notificationObject,
            title = null,
            message = null
        )
        val result = push.denyNotification(context)
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is Exception }
        assertTrue { result.exceptionOrNull()?.message == "Simulated Network Error" }
    }

    @Test
    fun `requiresBiometric returns true when notificationObject numberMatchingType is null`() {
        every { notificationObject.numberMatchingType } returns null
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
            )
        assertTrue(push.requiresBiometric())
    }

    @Test
    fun `requiresBiometric returns false when notificationObject numberMatchingType is not null`() {
        every { notificationObject.numberMatchingType } returns "mock"
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
            )
        assertTrue(!push.requiresBiometric())
    }

    @Test
    fun `isChallenge returns true when notificationObject numberMatchingType is not null`() {
        every { notificationObject.numberMatchingType } returns "mock"
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
        )
        assertTrue(push.isChallenge())
    }

    @Test
    fun `isChallenge returns false when notificationObject numberMatchingType is null`(){
        every { notificationObject.numberMatchingType } returns null
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
        )
        assertTrue(!push.isChallenge())

    }

    @Test
    fun `getNumbersChallenge returns correct values when notificationObject numberMatchingType is not null`(){
        every { notificationObject.numberMatchingType } returns "mock"
        every { notificationObject.numberMatchingOptions } returns intArrayOf(1,2,3)
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
            )
        assertTrue(push.getNumbersChallenge()?.contentEquals(intArrayOf(1,2,3)) ?: false)
    }

    @Test
    fun `getNumbersChallenge returns empty array when notificationObject numberMatchingType is null`(){
        every { notificationObject.numberMatchingType } returns null
        val push = PushNotification(
            notificationObject = notificationObject,
            title = "t",
            message = "m"
            )
        assertTrue(notificationObject.numberMatchingOptions == null || notificationObject.numberMatchingOptions.isEmpty())
    }

    @Test
    fun `getPushType returns DRY when isTest`() {
        every { notificationObject.isTest } returns true

        val push = PushNotification(
            notificationObject = notificationObject,
            title = null,
            message = null
        )

        assertEquals(PushType.DRY, push.getPushType())
    }
    @Test
    fun `getPushType returns CHALLENGE when numberMatchingType present`() {
        every { notificationObject.isTest } returns false
        every { notificationObject.numberMatchingType } returns "type"

        val push = PushNotification(
            notificationObject = notificationObject,
            title = null,
            message = null
        )

        assertEquals(PushType.CHALLENGE, push.getPushType())
    }
    @Test
    fun `getPushType returns DEFAULT otherwise`() {
        every { notificationObject.isTest } returns false
        every { notificationObject.numberMatchingType } returns null

        val push = PushNotification(
            notificationObject = notificationObject,
            title = null,
            message = null
        )

        assertEquals(PushType.DEFAULT, push.getPushType())
    }
}