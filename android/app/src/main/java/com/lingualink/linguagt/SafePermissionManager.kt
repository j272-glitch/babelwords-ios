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
 * UPDATED: Added cleanupPendingRequests, improved null safety and edge case handling
 * for automated testing scenarios
 */
class SafePermissionManager(private val activity: Activity) {

    companion object {
        private const val TAG = "SafePermissionManager"
        private const val PERMISSION_REQUEST_CODE = 100
    }
    
    // Track pending request state for cleanup
    private var hasPendingRequest = false

    /**
     * Check microphone permission safely
     */
    fun hasMicrophonePermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking microphone permission", e)
            TestRigorLogger.logError("hasMicrophonePermission", e)
            false
        }
    }

    /**
     * Request microphone permission safely
     * TESTRIGOR FIX: Short-circuit if already granted or activity invalid
     */
    fun requestMicrophonePermission(onResult: (Boolean) -> Unit) {
        try {
            // TESTRIGOR FIX: Early return if already granted
            if (hasMicrophonePermission()) {
                TestRigorLogger.logDebug("Mic permission already granted - short-circuit")
                onResult(true)
                return
            }
            
            // Check if activity is in valid state
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "Cannot request permission - activity invalid")
                TestRigorLogger.logWarning("Cannot request permission - activity finishing/destroyed")
                onResult(false)
                return
            }
            
            // Check if system might be auto-denying (e.g., too many requests)
            if (hasPendingRequest) {
                TestRigorLogger.logWarning("Permission request already pending - avoiding duplicate")
                return
            }

            hasPendingRequest = true
            
            // Request permission
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error requesting microphone permission", e)
            TestRigorLogger.logError("requestMicrophonePermission", e)
            hasPendingRequest = false
            onResult(false)
        }
    }

    /**
     * Handle permission request result safely
     * TESTRIGOR FIX: Handle empty permissions array to prevent ArrayIndexOutOfBoundsException
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onResult: (Boolean) -> Unit
    ) {
        try {
            hasPendingRequest = false
            
            if (requestCode == PERMISSION_REQUEST_CODE) {
                // TESTRIGOR FIX: Handle empty arrays
                if (permissions.isEmpty() || grantResults.isEmpty()) {
                    TestRigorLogger.logWarning("Empty permission result - treating as denied")
                    onResult(false)
                    return
                }
                
                val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                
                // Log any permission name mismatches for TestRigor diagnostics
                if (permissions.isNotEmpty() && permissions[0] != Manifest.permission.RECORD_AUDIO) {
                    TestRigorLogger.logPermissionMismatch(
                        expected = Manifest.permission.RECORD_AUDIO,
                        received = permissions[0]
                    )
                }
                
                onResult(granted)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling permission result", e)
            TestRigorLogger.logError("handlePermissionResult", e)
            onResult(false)
        }
    }
    
    /**
     * Clean up pending permission requests
     * TESTRIGOR FIX: Called when WebView cancels permission request
     */
    fun cleanupPendingRequests() {
        TestRigorLogger.logDebug("Cleaning up pending permission requests")
        hasPendingRequest = false
    }
}
