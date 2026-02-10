package com.pingidentity.pingonemfa.push

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import com.pingidentity.pingidsdkv2.NotificationObject
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test

class PushApprovalServiceTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var notificationObject: NotificationObject
    private lateinit var pushNotification: PushNotification

    private lateinit var service: PushApprovalService

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        notificationObject = mockk(relaxed = true)

        pushNotification = PushNotification(
            notificationObject = notificationObject,
            title = "title",
            message = "message"
        )

        service = spyk(PushApprovalService(dispatcher = testDispatcher), recordPrivateCalls = true)
        // CRITICAL: block Android notification creation
        every {
            service["createForegroundNotification"]()
        } returns mockk<Notification>(relaxed = true)

        // Prevent Android framework calls
        every { service.startForeground(any(), any()) } just Runs
        every { service.stopForeground(any<Int>()) } just Runs
        every { service.stopSelf(any()) } just Runs

        // Notification plumbing
        every {
            service.getSystemService(NotificationManager::class.java)
        } returns mockk(relaxed = true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onStartCommand approves notification when user_action is approve`() = runTest {
        every {
            notificationObject.approve(any(), any(), any(), any())
        } answers {
            val callback = args[3]
            callback!!
                .javaClass
                .getMethod("onComplete", Any::class.java)
                .invoke(callback, null)
        }

        val intent = spyk(Intent())

        every {
            intent.getParcelableExtra<PushNotification>("notification")
        } returns pushNotification

        every {
            intent.getStringExtra("auth_method")
        } returns "banner"

        every {
            intent.getStringExtra("user_action")
        } returns "approve"

        service.onStartCommand(intent, 0, 1)

        testDispatcher.scheduler.advanceUntilIdle()

        verify {
            notificationObject.approve(
                service,
                "banner",
                null,
                any()
            )
        }
    }

    @Test
    fun `onStartCommand denies notification when user_action is deny`() = runTest {
        every {
            notificationObject.deny(any(), any())
        } answers {
            val callback = args[1]
            callback!!
                .javaClass
                .getMethod("onComplete", Any::class.java)
                .invoke(callback, null)
        }

        val intent = spyk(Intent())

        every {
            intent.getParcelableExtra<PushNotification>("notification")
        } returns pushNotification

        every {
            intent.getStringExtra("auth_method")
        } returns "banner"

        every {
            intent.getStringExtra("user_action")
        } returns "deny"

        service.onStartCommand(intent, 0, 1)

        testDispatcher.scheduler.advanceUntilIdle()
        verify {
            notificationObject.deny(
                service,
                any()
            )
        }

    }
}