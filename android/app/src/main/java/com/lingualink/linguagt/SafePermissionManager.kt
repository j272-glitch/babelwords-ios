package com.lingualink.linguagt

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * TestRigor-safe permission manager that won't crash during automated testing
 */
class SafePermissionManager(private val activity: Activity) {

    companion object {
        private const val TAG = "SafePermissionManager"
        private const val PERMISSION_REQUEST_CODE = 100
    }

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
            false
        }
    }

    /**
     * Request microphone permission safely
     */
    fun requestMicrophonePermission(onResult: (Boolean) -> Unit) {
        try {
            // Check if activity is in valid state
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "Cannot request permission - activity invalid")
                onResult(false)
                return
            }

            // Check if already granted
            if (hasMicrophonePermission()) {
                onResult(true)
                return
            }

            // Request permission
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error requesting microphone permission", e)
            onResult(false)
        }
    }

    /**
     * Handle permission request result safely
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
                onResult(granted)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling permission result", e)
            onResult(false)
        }
    }
}
