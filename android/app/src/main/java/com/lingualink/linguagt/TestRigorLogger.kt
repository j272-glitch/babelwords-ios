package com.lingualink.linguagt

import android.util.Log

/**
 * Enhanced logging for TestRigor debugging
 * 
 * FIXED: Removed duplicate logError method to prevent JVM signature clash
 * Now uses only nullable Throwable parameter which is more flexible
 * 
 * UPDATED: Added missing logging methods (logPermission, logMilestone, logDebug, logWebView, logWarning)
 * to resolve compilation errors in MainActivity.kt
 * 
 * UPDATED v2: Modified logPermission and logWebView to accept multiple parameters
 * to fix "Too many arguments" compilation errors
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

    /**
     * Log an error with optional throwable
     * 
     * FIXED: Only one logError method with nullable Throwable
     * This prevents JVM signature clash between:
     * - logError(String, Throwable) and 
     * - logError(String, Throwable?)
     * 
     * Both compile to the same JVM signature, causing a platform declaration clash.
     * 
     * @param operation Description of the operation that failed
     * @param error Optional throwable (can be null for non-exception errors like FormError)
     */
    fun logError(operation: String, error: Throwable?) {
        Log.e(TAG, "========================================")
        Log.e(TAG, "ERROR in $operation")
        if (error != null) {
            Log.e(TAG, "Message: ${error.message}")
            Log.e(TAG, "Stack trace:")
            error.printStackTrace()
        } else {
            Log.e(TAG, "Message: No exception details available")
        }
        Log.e(TAG, "========================================")
    }

    fun logActivityState(activityName: String, state: String, isFinishing: Boolean, isDestroyed: Boolean) {
        Log.d(TAG, "ACTIVITY_STATE: $activityName | $state | finishing=$isFinishing | destroyed=$isDestroyed")
    }

    fun logAdEvent(event: String) {
        Log.d(TAG, "AD_EVENT: $event | Time: ${System.currentTimeMillis()}")
    }

    /**
     * Log permission-related events
     * Used for tracking permission requests and results
     * 
     * @param permission The permission being requested (e.g., "microphone", "camera")
     * @param granted Whether the permission was granted
     * @param isResult Whether this log entry represents a permission result (true) or a request check (false)
     * @param details Optional additional details about the permission event
     */
    fun logPermission(permission: String, granted: Boolean, isResult: Boolean, details: String = "") {
        val status = if (granted) "✅ GRANTED" else "❌ DENIED"
        val type = if (isResult) "RESULT" else "CHECK"
        val detailsStr = if (details.isNotEmpty()) " ($details)" else ""
        Log.d(TAG, "PERMISSION $type: $permission -> $status$detailsStr")
    }

    /**
     * Log milestone events
     * Used for tracking significant application lifecycle events
     */
    fun logMilestone(message: String) {
        Log.i(TAG, "========================================")
        Log.i(TAG, "MILESTONE: $message")
        Log.i(TAG, "Thread: ${Thread.currentThread().name}")
        Log.i(TAG, "Time: ${System.currentTimeMillis()}")
        Log.i(TAG, "========================================")
    }

    /**
     * Log debug information
     * Used for general debugging messages
     */
    fun logDebug(message: String) {
        Log.d(TAG, "DEBUG: $message")
    }

    /**
     * Log WebView-related events
     * Used for tracking WebView operations and state changes
     * 
     * @param action The WebView action/event (e.g., "loaded", "navigating", "error")
     * @param url Optional URL associated with the event
     */
    fun logWebView(action: String, url: String? = null) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "WEBVIEW: $action")
        if (url != null && url.isNotEmpty()) {
            Log.d(TAG, "URL: $url")
        }
        Log.d(TAG, "Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "Time: ${System.currentTimeMillis()}")
        Log.d(TAG, "========================================")
    }

    /**
     * Log warning messages
     * Used for non-critical issues that should be investigated
     */
    fun logWarning(message: String) {
        Log.w(TAG, "========================================")
        Log.w(TAG, "WARNING: $message")
        Log.w(TAG, "Thread: ${Thread.currentThread().name}")
        Log.w(TAG, "Time: ${System.currentTimeMillis()}")
        Log.w(TAG, "========================================")
    }
}