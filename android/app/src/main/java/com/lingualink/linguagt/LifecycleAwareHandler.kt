package com.lingualink.linguagt

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handler that automatically cancels pending operations when lifecycle is destroyed
 * 
 * Uses composition pattern instead of inheritance to avoid issues with final methods
 * in Handler class (post/postDelayed became final in recent Android versions)
 * 
 * CRASH PREVENTION SOLUTIONS:
 * - Solution #39: Check lifecycle before posting
 * - Solution #42: Cancel delayed callbacks on destroy
 * - Solution #46: Serialize through single handler
 */
class LifecycleAwareHandler(
    private val lifecycleOwner: LifecycleOwner,
    looper: Looper = Looper.getMainLooper()
) : DefaultLifecycleObserver {

    private val handler = Handler(looper)
    
    // Track ALL wrapped runnables for proper cleanup (handles multiple posts of same runnable)
    // Using a list to track all pending wrappers, not just one per original
    private val allPendingWrappers = mutableListOf<Runnable>()
    private val runnableLock = Object()
    
    // Solution #39: Track if lifecycle is valid
    private val isLifecycleValid = AtomicBoolean(true)

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }
    
    /**
     * Solution #39: Check if lifecycle is still valid for posting
     */
    fun isValid(): Boolean = isLifecycleValid.get()

    /**
     * Post a runnable to be executed on the handler's thread
     * Automatically tracked and cancelled on lifecycle destroy
     * 
     * Solution #39: Check lifecycle before posting
     * Fixed: Track each wrapped runnable individually to handle multiple posts of same runnable
     */
    fun post(r: Runnable): Boolean {
        // Solution #39: Check lifecycle validity
        if (!isLifecycleValid.get()) {
            TestRigorLogger.logWarning("LifecycleAwareHandler: post skipped - lifecycle invalid")
            return false
        }
        
        // Create wrapper and track it
        var wrapperRef: Runnable? = null
        val wrappedRunnable = object : Runnable {
            override fun run() {
                // Solution #39: Double-check before executing
                if (!isLifecycleValid.get()) {
                    TestRigorLogger.logWarning("LifecycleAwareHandler: runnable skipped - lifecycle invalid")
                    synchronized(runnableLock) {
                        wrapperRef?.let { allPendingWrappers.remove(it) }
                    }
                    return
                }
                
                try {
                    r.run()
                } catch (e: Exception) {
                    TestRigorLogger.logError("LifecycleAwareHandler: runnable error", e)
                } finally {
                    synchronized(runnableLock) {
                        wrapperRef?.let { allPendingWrappers.remove(it) }
                    }
                }
            }
        }
        wrapperRef = wrappedRunnable
        
        synchronized(runnableLock) {
            allPendingWrappers.add(wrappedRunnable)
        }
        
        return handler.post(wrappedRunnable)
    }

    /**
     * Post a delayed runnable to be executed on the handler's thread
     * Automatically tracked and cancelled on lifecycle destroy
     * 
     * Solution #42: Cancel delayed callbacks on destroy
     * Fixed: Track each wrapped runnable individually to handle multiple posts of same runnable
     */
    fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        // Solution #39: Check lifecycle validity
        if (!isLifecycleValid.get()) {
            TestRigorLogger.logWarning("LifecycleAwareHandler: postDelayed skipped - lifecycle invalid")
            return false
        }
        
        // Create wrapper and track it
        var wrapperRef: Runnable? = null
        val wrappedRunnable = object : Runnable {
            override fun run() {
                // Solution #39: Double-check before executing
                if (!isLifecycleValid.get()) {
                    TestRigorLogger.logWarning("LifecycleAwareHandler: delayed runnable skipped - lifecycle invalid")
                    synchronized(runnableLock) {
                        wrapperRef?.let { allPendingWrappers.remove(it) }
                    }
                    return
                }
                
                try {
                    r.run()
                } catch (e: Exception) {
                    TestRigorLogger.logError("LifecycleAwareHandler: delayed runnable error", e)
                } finally {
                    synchronized(runnableLock) {
                        wrapperRef?.let { allPendingWrappers.remove(it) }
                    }
                }
            }
        }
        wrapperRef = wrappedRunnable
        
        synchronized(runnableLock) {
            allPendingWrappers.add(wrappedRunnable)
        }
        
        return handler.postDelayed(wrappedRunnable, delayMillis)
    }

    /**
     * Remove all callbacks for a specific runnable
     * Note: This removes the original runnable from handler, not our wrappers
     * For full cleanup, use removeCallbacksAndMessages()
     */
    fun removeCallbacks(r: Runnable) {
        handler.removeCallbacks(r)
    }

    /**
     * Remove all pending callbacks and messages
     */
    fun removeCallbacksAndMessages() {
        synchronized(runnableLock) {
            // Remove all tracked wrapped runnables from handler
            allPendingWrappers.forEach { handler.removeCallbacks(it) }
            allPendingWrappers.clear()
        }
        handler.removeCallbacksAndMessages(null)
    }
    
    /**
     * Solution #46: Get pending runnable count for debugging
     */
    fun getPendingCount(): Int {
        synchronized(runnableLock) {
            return allPendingWrappers.size
        }
    }
    
    /**
     * Lifecycle callbacks
     */
    override fun onStart(owner: LifecycleOwner) {
        isLifecycleValid.set(true)
    }
    
    override fun onStop(owner: LifecycleOwner) {
        // Keep valid until destroy - just pause operations
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // Solution #42: Mark lifecycle invalid first
        isLifecycleValid.set(false)
        
        // Cancel all pending operations when lifecycle is destroyed
        synchronized(runnableLock) {
            // Remove all tracked wrapped runnables from handler
            val count = allPendingWrappers.size
            allPendingWrappers.forEach { handler.removeCallbacks(it) }
            allPendingWrappers.clear()
            if (count > 0) {
                TestRigorLogger.logDebug("LifecycleAwareHandler: Removed $count pending wrappers")
            }
        }
        handler.removeCallbacksAndMessages(null)
        
        TestRigorLogger.logDebug("LifecycleAwareHandler: Destroyed, cleared all callbacks")
    }
}
