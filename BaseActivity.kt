package com.lingualink.linguagt

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enhanced BaseActivity with crash protection for automated testing
 * All other activities should extend this class to automatically include tracking
 */
abstract class BaseActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "BaseActivity"
    }
    
    // Track if activity is in valid state for UI operations
    private val isActivityAlive = AtomicBoolean(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isActivityAlive.set(true)
        
        // Set up global exception handler for this activity
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in ${this::class.simpleName}", throwable)
            handleUncaughtException(throwable)
            // Call default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    override fun onStart() {
        super.onStart()
        isActivityAlive.set(true)
        Log.d(TAG, "Activity started: ${this::class.simpleName}")
        MainActivity.tracker?.startTracking()
    }
    
    override fun onStop() {
        isActivityAlive.set(false)
        super.onStop()
        Log.d(TAG, "Activity stopped: ${this::class.simpleName}")
        MainActivity.tracker?.stopTracking()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Activity resumed: ${this::class.simpleName}")
        // Track activity resume
        MainActivity.tracker?.trackActivity("${this::class.simpleName}_resumed")
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Activity paused: ${this::class.simpleName}")
        // Track activity pause
        MainActivity.tracker?.trackActivity("${this::class.simpleName}_paused")
    }
    
    override fun onDestroy() {
        isActivityAlive.set(false)
        super.onDestroy()
        Log.d(TAG, "Activity destroyed: ${this::class.simpleName}")
    }
    
    /**
     * Check if it's safe to perform UI operations
     */
    fun isSafeToUpdateUI(): Boolean {
        return !isFinishing && !isDestroyed && isActivityAlive.get()
    }
    
    /**
     * Handle uncaught exceptions gracefully
     */
    protected open fun handleUncaughtException(throwable: Throwable) {
        Log.e(TAG, "Handling uncaught exception", throwable)
        TestRigorLogger.logError("UncaughtException", throwable)
    }
}
