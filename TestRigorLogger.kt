package com.lingualink.linguagt

import android.util.Log

/**
 * Enhanced logging for TestRigor debugging
 */
object TestRigorLogger {
    
    private const val TAG = "TESTRIGOR"
    
    fun logClick(elementName: String) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "CLICK: $elementName")
        Log.d(TAG, "Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "Time: ${System.currentTimeMillis()}")
        Log.d(TAG, "========================================")
    }
    
    fun logUIUpdate(viewId: String, newValue: String) {
        Log.d(TAG, "UI_UPDATE: $viewId -> $newValue")
    }
    
    fun logError(operation: String, error: Throwable) {
        Log.e(TAG, "========================================")
        Log.e(TAG, "ERROR in $operation")
        Log.e(TAG, "Message: ${error.message}")
        Log.e(TAG, "Stack trace:")
        error.printStackTrace()
        Log.e(TAG, "========================================")
    }
    
    fun logActivityState(activityName: String, state: String, isFinishing: Boolean, isDestroyed: Boolean) {
        Log.d(TAG, "ACTIVITY_STATE: $activityName | $state | finishing=$isFinishing | destroyed=$isDestroyed")
    }
    
    fun logAdEvent(event: String) {
        Log.d(TAG, "AD_EVENT: $event | Time: ${System.currentTimeMillis()}")
    }
    
    fun logError(operation: String, error: Throwable?) {
        Log.e(TAG, "========================================")
        Log.e(TAG, "ERROR in $operation")
        if (error != null) {
            Log.e(TAG, "Message: ${error.message}")
            Log.e(TAG, "Stack trace:")
            error.printStackTrace()
        }
        Log.e(TAG, "========================================")
    }
}
