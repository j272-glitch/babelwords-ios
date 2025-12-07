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
 */
class SafePermissionManager(
    private val activity: Activity,
    private val testMode: Boolean = false
) {

    companion object {
        private const val TAG = "SafePermissionManager"
        const val PERMISSION_REQUEST_CODE = 100
    }

    // Track pending permission callback - only one active request at a time
    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null

    // Track request timing and count for TestRigor logging
    private var requestStartTime: Long = 0L
    private var requestCount: Int = 0
    private var lastRequestResult: Boolean = false

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
     */
    fun requestMicrophonePermission(callback: (Boolean) -> Unit) {
        TestRigorLogger.logDebug("SafePermissionManager: requestMicrophonePermission called")

        if (testMode) {
            Log.d(TAG, "TEST MODE: Auto-granting microphone permission")
            TestRigorLogger.logMilestone("TEST MODE: Auto-granted microphone")
            callback(true)
            return
        }

        // Check if activity is in valid state
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Cannot request permission - activity invalid")
            TestRigorLogger.logWarning("SafePermissionManager: Activity invalid, cannot request permission.")
            callback(false)
            return
        }

        // Check if already granted
        if (hasMicrophonePermission()) {
            Log.d(TAG, "Microphone permission already granted")
            TestRigorLogger.logMilestone("Microphone permission already granted.")
            callback(true)
            return
        }

        // Store callback for later (replacing any existing one)
        if (pendingPermissionCallback != null) {
            Log.w(TAG, "Replacing pending permission callback - duplicate request")
            TestRigorLogger.logWarning("SafePermissionManager: Replacing pending permission callback.")
        }
        pendingPermissionCallback = callback

        try {
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
            pendingPermissionCallback = null
            lastRequestResult = false
            callback(false)
        }
    }

    /**
     * Handle permission request result safely
     * 
     * TESTRIGOR: Works normally even in test mode (for edge cases)
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

        val storedCallback = pendingPermissionCallback
        pendingPermissionCallback = null

        if (storedCallback == null) {
            Log.w(TAG, "No pending callback for permission result")
            TestRigorLogger.logWarning("SafePermissionManager: No pending callback!")
            return
        }

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

            storedCallback(isGranted)
            callback(isGranted) // Also execute the immediate callback
        } catch (e: Exception) {
            Log.e(TAG, "Error handling permission result", e)
            TestRigorLogger.logError("SafePermissionManager: Error processing result", e)
            storedCallback(false)
            callback(false)
        }
    }

    /**
     * Clean up pending permission requests
     * 
     * TESTRIGOR FIX: Called when WebView cancels permission request
     * to prevent memory leaks and state inconsistencies during automated testing
     */
    fun cleanupPendingRequests() {
        try {
            if (pendingPermissionCallback != null) {
                Log.d(TAG, "Cleaning up pending permission callback")

                // Notify pending callback that permission was cancelled
                try {
                    pendingPermissionCallback?.invoke(false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying pending callback", e)
                }

                // Clear the callback
                pendingPermissionCallback = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up pending requests", e)
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