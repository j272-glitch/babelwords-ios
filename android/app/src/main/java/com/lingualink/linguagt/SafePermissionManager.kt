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
    fun requestMicrophonePermission(onResult: (Boolean) -> Unit) {
        try {
            // TESTRIGOR: Auto-grant in test mode
            if (testMode) {
                Log.d(TAG, "TEST MODE: Auto-granting microphone permission")
                onResult(true)
                return
            }

            // Check if activity is in valid state
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "Cannot request permission - activity invalid")
                onResult(false)
                return
            }

            // Check if already granted
            if (hasMicrophonePermission()) {
                Log.d(TAG, "Microphone permission already granted")
                onResult(true)
                return
            }

            // Store callback for later (replacing any existing one)
            if (pendingPermissionCallback != null) {
                Log.w(TAG, "Replacing pending permission callback - duplicate request")
            }
            pendingPermissionCallback = onResult

            // Request permission
            Log.d(TAG, "Requesting microphone permission via Android system")
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting microphone permission", e)
            pendingPermissionCallback = null
            onResult(false)
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
        onResult: (Boolean) -> Unit
    ) {
        try {
            if (requestCode == PERMISSION_REQUEST_CODE) {
                val granted = grantResults.isNotEmpty() && 
                             grantResults[0] == PackageManager.PERMISSION_GRANTED

                Log.d(TAG, "Permission result: granted=$granted")

                // Execute the stored callback if it exists
                pendingPermissionCallback?.let { callback ->
                    callback(granted)
                    pendingPermissionCallback = null
                }

                // Also execute the immediate callback
                onResult(granted)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling permission result", e)
            pendingPermissionCallback = null
            onResult(false)
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
}