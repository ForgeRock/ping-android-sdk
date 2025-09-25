/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.utils

import android.util.Log

/**
 * Helper class for detecting test environments.
 * This provides multiple detection methods to ensure test environments are properly detected.
 * Also supports explicit test mode enablement for more reliable test execution.
 */
object TestModeDetector {
    private const val TAG = "TestModeDetector"
    
    // Set via JVM property in test runs
    private const val TEST_MODE_PROPERTY = "pingid.test.mode"
    
    // For explicit test mode setting
    private var testModeExplicitlyEnabled = false
    
    /**
     * Determine if the app is running in a test environment.
     * This method uses multiple detection strategies for higher reliability.
     *
     * @return True if in a test environment, false otherwise.
     */
    fun isRunningInTestEnvironment(): Boolean {
        // First check: Explicit enablement
        if (testModeExplicitlyEnabled) {
            Log.d(TAG, "Test mode explicitly enabled")
            return true
        }
        
        // First check: Test frameworks
        val hasTestFrameworks = hasTestFrameworks()
        
        // Second check: Thread check
        val hasTestThreads = hasTestThreads()
        
        // Third check: System property set by tests
        val hasTestProperty = System.getProperty(TEST_MODE_PROPERTY) != null
        
        // Fourth check: Check for debuggable build or testing flags
        val hasTestBuildFlags = hasTestBuildFlags()
        
        val isTestEnvironment = hasTestFrameworks || hasTestThreads || hasTestProperty || hasTestBuildFlags
        
        if (isTestEnvironment) {
            Log.d(TAG, "Test environment detected: hasTestFrameworks=$hasTestFrameworks, " +
                    "hasTestThreads=$hasTestThreads, hasTestProperty=$hasTestProperty, " +
                    "hasTestBuildFlags=$hasTestBuildFlags")
        }
        
        return isTestEnvironment
    }
    
    /**
     * Explicitly enable test mode.
     * This allows test classes to force test mode when necessary.
     */
    fun enableTestMode() {
        testModeExplicitlyEnabled = true
        Log.d(TAG, "Test mode explicitly enabled")
    }
    
    /**
     * Explicitly disable test mode.
     * This allows test classes to reset the test mode state.
     */
    fun disableTestMode() {
        testModeExplicitlyEnabled = false
        Log.d(TAG, "Test mode explicitly disabled")
    }
    
    /**
     * Check for presence of common test frameworks in the classpath.
     *
     * @return True if test frameworks are detected, false otherwise.
     */
    private fun hasTestFrameworks(): Boolean {
        return try {
            // Check for JUnit
            Class.forName("org.junit.Test") != null
        } catch (e: ClassNotFoundException) {
            try {
                // Check for AndroidX Test
                Class.forName("androidx.test.runner.AndroidJUnitRunner") != null
            } catch (e: ClassNotFoundException) {
                try {
                    // Check for Robolectric
                    Class.forName("org.robolectric.RobolectricTestRunner") != null
                } catch (e: ClassNotFoundException) {
                    false
                }
            }
        }
    }
    
    /**
     * Check for presence of test-related threads.
     *
     * @return True if test threads are detected, false otherwise.
     */
    private fun hasTestThreads(): Boolean {
        val currentThread = Thread.currentThread()
        val threadName = currentThread.name.lowercase()
        
        // Check for test runner threads
        return threadName.contains("test") && 
                (threadName.contains("runner") || 
                threadName.contains("worker") ||
                threadName.contains("junit") ||
                threadName.contains("instrumentation"))
    }
    
    /**
     * Check for presence of test build flags.
     *
     * @return True if test build flags are detected, false otherwise.
     */
    private fun hasTestBuildFlags(): Boolean {
        return try {
            // This will be true in debug builds
            val debuggable = Class.forName("android.os.Debug")
                .getMethod("isDebuggerConnected")
                .invoke(null) as Boolean
            
            // Look for testing property
            val testingProp = System.getProperty("testing") != null
            
            debuggable || testingProp
        } catch (e: Exception) {
            false
        }
    }
}
