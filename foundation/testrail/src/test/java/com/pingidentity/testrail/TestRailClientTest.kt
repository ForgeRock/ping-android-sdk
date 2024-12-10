package com.pingidentity.testrail

import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class TestRailClientTest {

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @Ignore
    fun testCreateRun() {
        val testRailClient = TestRailClient {
            username = "<username>"
            password = "<api_key>"
            projectId = "5"
        }

        testRailClient.prepareRun()
        testRailClient.addResult(21250, 1)
    }

    @TestRailCase(21250, 21252, 21257)
    @Test
    fun testMe() {
    }
}