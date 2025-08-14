package com.lingualink.translator

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.util.Log

/**
 * BaseActivity class that provides common TesterMobLib tracking functionality
 * All other activities should extend this class to automatically include tracking
 */
abstract class BaseActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "BaseActivity"
    }
    
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "Activity started: ${this::class.simpleName}")
        MainActivity.tracker?.startTracking()
    }
    
    override fun onStop() {
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
}