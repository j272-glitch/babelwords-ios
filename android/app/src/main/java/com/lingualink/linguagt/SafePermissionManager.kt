package com.lingualink.linguagt

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * TestRigor-safe permission manager that won't crash during automated testing
 * 
 * TESTRIGOR ENHANCED VERSION
 * - Test mode support (auto-grants permissions)
 * - Enhanced logging
 * - Better state management
 * - Callback cleanup
 * 
 * CRASH PREVENTION SOLUTIONS:
 * - Solution #17: Synchronized callback access
 * - Solution #53: Initialize before onCreate completes
 * - Solution #84: Uncaught exception wrapper
 */
class SafePermissionManager(
    private val activity: Activity,
    private val testMode: Boolean = false
) {

    companion object {
        private const val TAG = "SafePermissionManager"
        const val PERMISSION_REQUEST_CODE = 100
        
        // Solution #19: Maximum retry attempts
        private const val MAX_RETRIES = 3
    }

    // Solution #17: Lock for synchronized callback access
    private val callbackLock = Object()
    
    // Track pending permission callback - only one active request at a time
    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null

    // Track request timing and count for TestRigor logging
    private var requestStartTime: Long = 0L
    private var requestCount: Int = 0
    private var lastRequestResult: Boolean = false
    
    // Solution #40: Track request state for race prevention
    private var isRequestInProgress = false
    private var retryCount = 0

    init {
        if (testMode) {
            Log.d(TAG, "⚠️ TEST MODE ENABLED - Permissions will be auto-granted")
        }
    }

    /**
     * Check microphone permission safely
     * 
     * TESTRIGOR: In test mode, always returns true
     */
    fun hasMicrophonePermission(): Boolean {
        // TESTRIGOR: Auto-grant in test mode
        if (testMode) {
            Log.d(TAG, "TEST MODE: hasMicrophonePermission() -> true (auto-granted)")
            return true
        }

        return try {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking microphone permission", e)
            false
        }
    }

    /**
     * Request microphone permission safely
     * 
     * TESTRIGOR: In test mode, immediately grants without showing dialog
     * 
     * CRASH PREVENTION SOLUTIONS:
     * - Solution #17: Synchronized callback access
     * - Solution #40: Race prevention with isRequestInProgress
     * - Solution #84: Exception wrapper for all operations
     */
    fun requestMicrophonePermission(callback: (Boolean) -> Unit) {
        TestRigorLogger.logDebug("SafePermissionManager: requestMicrophonePermission called")

        // Solution #84: Wrap all operations in try-catch
        try {
            if (testMode) {
                Log.d(TAG, "TEST MODE: Auto-granting microphone permission")
                TestRigorLogger.logMilestone("TEST MODE: Auto-granted microphone")
                safeInvokeCallback(callback, true)
                return
            }

            // Check if activity is in valid state
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "Cannot request permission - activity invalid")
                TestRigorLogger.logWarning("SafePermissionManager: Activity invalid, cannot request permission.")
                safeInvokeCallback(callback, false)
                return
            }

            // Check if already granted
            if (hasMicrophonePermission()) {
                Log.d(TAG, "Microphone permission already granted")
                TestRigorLogger.logMilestone("Microphone permission already granted.")
                safeInvokeCallback(callback, true)
                return
            }
            
            // Solution #40: Prevent concurrent requests
            synchronized(callbackLock) {
                if (isRequestInProgress) {
                    Log.w(TAG, "Request already in progress - queueing callback")
                    TestRigorLogger.logWarning("SafePermissionManager: Request in progress, replacing callback.")
                }
                
                // Store callback for later (replacing any existing one)
                if (pendingPermissionCallback != null) {
                    Log.w(TAG, "Replacing pending permission callback - duplicate request")
                    TestRigorLogger.logWarning("SafePermissionManager: Replacing pending permission callback.")
                }
                pendingPermissionCallback = callback
                isRequestInProgress = true
            }

            requestStartTime = System.currentTimeMillis()
            requestCount++

            TestRigorLogger.logDebug("SafePermissionManager: Starting permission request #$requestCount")

            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )

            Log.d(TAG, "Requested microphone permission")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permission", e)
            TestRigorLogger.logError("SafePermissionManager: Request failed", e)
            synchronized(callbackLock) {
                pendingPermissionCallback = null
                isRequestInProgress = false
            }
            lastRequestResult = false
            safeInvokeCallback(callback, false)
        }
    }
    
    /**
     * Solution #84: Safe callback invocation with exception handling
     */
    private fun safeInvokeCallback(callback: (Boolean) -> Unit, result: Boolean) {
        try {
            callback(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking callback", e)
            TestRigorLogger.logError("SafePermissionManager: Callback failed", e)
        }
    }

    /**
     * Handle permission request result safely
     * 
     * TESTRIGOR: Works normally even in test mode (for edge cases)
     * 
     * CRASH PREVENTION SOLUTIONS:
     * - Solution #17: Synchronized callback access
     * - Solution #40: Clear request state on result
     * - Solution #84: Exception wrapper
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        callback: (Boolean) -> Unit
    ) {
        TestRigorLogger.logDebug("SafePermissionManager: handlePermissionResult called (code=$requestCode)")

        if (requestCode != PERMISSION_REQUEST_CODE) {
            Log.w(TAG, "Unknown permission request code: $requestCode")
            TestRigorLogger.logWarning("Unknown permission request code: $requestCode")
            return
        }

        // Solution #17: Synchronized access to callback
        val storedCallback: ((Boolean) -> Unit)?
        synchronized(callbackLock) {
            storedCallback = pendingPermissionCallback
            pendingPermissionCallback = null
            isRequestInProgress = false
            retryCount = 0
        }

        if (storedCallback == null) {
            Log.w(TAG, "No pending callback for permission result")
            TestRigorLogger.logWarning("SafePermissionManager: No pending callback!")
            return
        }

        // Solution #84: Wrap result processing in try-catch
        try {
            val isGranted = grantResults.isNotEmpty() && 
                           grantResults[0] == PackageManager.PERMISSION_GRANTED

            val duration = System.currentTimeMillis() - requestStartTime
            lastRequestResult = isGranted

            Log.d(TAG, "Microphone permission ${if (isGranted) "granted" else "denied"} (${duration}ms)")
            TestRigorLogger.logPermission(
                "RECORD_AUDIO", 
                isGranted, 
                true, 
                "Duration: ${duration}ms"
            )

            safeInvokeCallback(storedCallback, isGranted)
            safeInvokeCallback(callback, isGranted)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling permission result", e)
            TestRigorLogger.logError("SafePermissionManager: Error processing result", e)
            safeInvokeCallback(storedCallback, false)
            safeInvokeCallback(callback, false)
        }
    }

    /**
     * Clean up pending permission requests
     * 
     * TESTRIGOR FIX: Called when WebView cancels permission request
     * to prevent memory leaks and state inconsistencies during automated testing
     * 
     * CRASH PREVENTION SOLUTIONS:
     * - Solution #17: Synchronized cleanup
     * - Solution #82: Clear callback references
     * - Solution #84: Exception wrapper
     */
    fun cleanupPendingRequests() {
        // Solution #84: Wrap all cleanup in try-catch
        try {
            val callback: ((Boolean) -> Unit)?
            
            // Solution #17: Synchronized access
            synchronized(callbackLock) {
                callback = pendingPermissionCallback
                pendingPermissionCallback = null
                isRequestInProgress = false
                retryCount = 0
            }
            
            if (callback != null) {
                Log.d(TAG, "Cleaning up pending permission callback")

                // Notify pending callback that permission was cancelled
                safeInvokeCallback(callback, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up pending requests", e)
            TestRigorLogger.logError("SafePermissionManager: Cleanup failed", e)
        }
    }

    /**
     * TESTRIGOR FIX: Check if there's a pending permission request
     * Used to prevent duplicate permission dialogs
     */
    fun hasPendingRequest(): Boolean {
        return pendingPermissionCallback != null
    }

    /**
     * TESTRIGOR FIX: Get request statistics for debugging
     */
    fun getRequestStats(): String {
        return "requests=$requestCount, lastResult=$lastRequestResult, hasPending=${pendingPermissionCallback != null}"
    }
}