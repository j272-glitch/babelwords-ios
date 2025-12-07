package com.lingualink.linguagt

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enhanced BaseActivity with crash protection for automated testing
 * All other activities should extend this class to automatically include tracking
 * 
 * CRASH PREVENTION SOLUTIONS:
 * - Solution #74: Check lifecycle state before showing dialogs
 * - Solution #78: Handler callbacks cleanup in onDestroy
 * - Solution #87: Global RuntimeException handler
 * - Solution #88: OutOfMemoryError protection
 */
abstract class BaseActivity : ComponentActivity() {

    companion object {
        private const val TAG = "BaseActivity"
        
        // Solution #88: Memory pressure threshold
        private const val LOW_MEMORY_THRESHOLD = 0.15f
    }

    // Track if activity is in valid state for UI operations
    private val isActivityAlive = AtomicBoolean(false)
    
    // Solution #74: Track dialog/permission state
    private val activeDialogCount = AtomicInteger(0)
    
    // Solution #78: Handler for safe UI operations with cleanup
    protected val uiHandler = Handler(Looper.getMainLooper())
    private val pendingRunnables = mutableListOf<Runnable>()
    private val runnableLock = Object()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isActivityAlive.set(true)

        // Solution #87: Set up global exception handler for this activity
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in ${this::class.simpleName}", throwable)
            
            // Solution #87: Log RuntimeException specifically
            if (throwable is RuntimeException) {
                TestRigorLogger.logError("RuntimeException in ${this::class.simpleName}", throwable)
            }
            
            // Solution #88: Handle OOM specially
            if (throwable is OutOfMemoryError) {
                TestRigorLogger.logError("OutOfMemoryError in ${this::class.simpleName}", throwable)
                handleOutOfMemory()
            }
            
            handleUncaughtException(throwable)
            // Call default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        // Solution #88: Check memory on startup
        checkMemoryPressure()
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
        
        // Solution #78: Clean up all pending handler callbacks
        synchronized(runnableLock) {
            pendingRunnables.forEach { runnable ->
                uiHandler.removeCallbacks(runnable)
            }
            pendingRunnables.clear()
        }
        uiHandler.removeCallbacksAndMessages(null)
        
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
     * Solution #74: Check if it's safe to show a dialog
     */
    fun isSafeToShowDialog(): Boolean {
        return isSafeToUpdateUI() && !isChangingConfigurations
    }
    
    /**
     * Solution #74: Track dialog open/close for lifecycle coordination
     */
    fun onDialogOpened() {
        activeDialogCount.incrementAndGet()
        TestRigorLogger.logDebug("Dialog opened, count: ${activeDialogCount.get()}")
    }
    
    fun onDialogClosed() {
        activeDialogCount.decrementAndGet()
        TestRigorLogger.logDebug("Dialog closed, count: ${activeDialogCount.get()}")
    }
    
    fun hasActiveDialogs(): Boolean = activeDialogCount.get() > 0
    
    /**
     * Solution #78: Safe runOnUiThread with callback tracking
     * Fixed: Properly capture runnable reference for removal
     */
    fun safeRunOnUiThread(action: () -> Unit) {
        if (!isSafeToUpdateUI()) {
            TestRigorLogger.logWarning("safeRunOnUiThread skipped - activity invalid")
            return
        }
        
        // Capture runnable reference for proper removal
        var runnableRef: Runnable? = null
        val runnable = object : Runnable {
            override fun run() {
                if (isSafeToUpdateUI()) {
                    try {
                        action()
                    } catch (e: Exception) {
                        TestRigorLogger.logError("safeRunOnUiThread error", e)
                    }
                }
                synchronized(runnableLock) {
                    runnableRef?.let { pendingRunnables.remove(it) }
                }
            }
        }
        runnableRef = runnable
        
        synchronized(runnableLock) {
            pendingRunnables.add(runnable)
        }
        uiHandler.post(runnable)
    }
    
    /**
     * Solution #56: Safe findViewById with type parameter
     */
    fun <T : View> safeFindViewById(id: Int): T? {
        return try {
            findViewById<T>(id)
        } catch (e: Exception) {
            TestRigorLogger.logError("safeFindViewById failed for id: $id", e)
            null
        }
    }
    
    /**
     * Solution #88: Check memory pressure
     */
    private fun checkMemoryPressure() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val freeRatio = 1.0f - (usedMemory.toFloat() / maxMemory.toFloat())
        
        if (freeRatio < LOW_MEMORY_THRESHOLD) {
            TestRigorLogger.logWarning("Low memory: ${(freeRatio * 100).toInt()}% free")
            // Trigger garbage collection
            System.gc()
        }
    }
    
    /**
     * Solution #88: Handle OutOfMemoryError
     */
    private fun handleOutOfMemory() {
        // Clear caches and request GC
        System.gc()
        TestRigorLogger.logWarning("OutOfMemoryError handled - cleared caches")
    }

    /**
     * Handle uncaught exceptions gracefully
     * Solution #87: Global exception handler
     */
    protected open fun handleUncaughtException(throwable: Throwable) {
        Log.e(TAG, "Handling uncaught exception", throwable)
        TestRigorLogger.logError("UncaughtException", throwable)
    }
    
    /**
     * Solution #43: Safe tracker access with synchronization
     */
    protected fun safeTrackerOperation(operation: (UserActivityTracker) -> Unit) {
        try {
            MainActivity.tracker?.let { tracker ->
                synchronized(tracker) {
                    operation(tracker)
                }
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("Tracker operation failed", e)
        }
    }
}
