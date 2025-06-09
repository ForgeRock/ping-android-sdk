/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.storage

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
        
        // To be safe, we'll consider it a test if any of the checks are true
        val result = hasTestFrameworks || hasTestThreads || hasTestProperty || hasTestBuildFlags
        
        if (result) {
            Log.d(TAG, "Test environment detected (frameworks=$hasTestFrameworks, threads=$hasTestThreads, property=$hasTestProperty, buildFlags=$hasTestBuildFlags)")
        }
        
        return result
    }
    
    /**
     * Check for the presence of common test frameworks.
     */
    private fun hasTestFrameworks(): Boolean {
        return try {
            // Check for Espresso
            Class.forName("androidx.test.espresso.Espresso")
            true
        } catch (e1: ClassNotFoundException) {
            try {
                // Check for standard instrumentation
                Class.forName("androidx.test.platform.app.InstrumentationRegistry")
                true
            } catch (e2: ClassNotFoundException) {
                try {
                    // Check for JUnit
                    Class.forName("org.junit.Assert")
                    true
                } catch (e3: ClassNotFoundException) {
                    false
                }
            }
        }
    }
    
    /**
     * Check for the presence of certain thread patterns that indicate a test environment.
     */
    private fun hasTestThreads(): Boolean {
        val threadNames = Thread.getAllStackTraces().keys.map { it.name.lowercase() }
        
        return threadNames.any { name ->
            name.contains("test") || 
            name.contains("junit") || 
            name.contains("mockito") || 
            name.contains("instrumentation")
        }
    }

    /**
     * Checks for various build flags that indicate test environments.
     */
    private fun hasTestBuildFlags(): Boolean {
        return try {
            // Check for android.test packages
            Class.forName("android.test.InstrumentationTestRunner")
            true
        } catch (e1: ClassNotFoundException) {
            try {
                // Check for Instrumentation RunWith annotations
                Class.forName("android.app.Instrumentation")
                true
            } catch (e2: ClassNotFoundException) {
                false
            }
        }
    }

    /**
     * Force test mode in the current process.
     * This should be called at the beginning of test setup.
     */
    fun enableTestMode() {
        System.setProperty(TEST_MODE_PROPERTY, "true")
        testModeExplicitlyEnabled = true
        Log.d(TAG, "Test mode explicitly enabled by API call")
    }
    
    /**
     * Check if test mode has been explicitly enabled.
     */
    fun isTestModeExplicitlyEnabled(): Boolean {
        return testModeExplicitlyEnabled
    }
    
    /**
     * Disable test mode. Primarily for testing purposes.
     */
    fun disableTestMode() {
        System.clearProperty(TEST_MODE_PROPERTY)
        testModeExplicitlyEnabled = false
    }
}
