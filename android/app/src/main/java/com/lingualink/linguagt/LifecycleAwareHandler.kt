package com.lingualink.linguagt

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Handler that automatically cancels pending operations when lifecycle is destroyed
 * 
 * Uses composition pattern instead of inheritance to avoid issues with final methods
 * in Handler class (post/postDelayed became final in recent Android versions)
 */
class LifecycleAwareHandler(
    lifecycleOwner: LifecycleOwner,
    looper: Looper = Looper.getMainLooper()
) : DefaultLifecycleObserver {

    private val handler = Handler(looper)
    private val pendingRunnables = mutableSetOf<Runnable>()

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    /**
     * Post a runnable to be executed on the handler's thread
     * Automatically tracked and cancelled on lifecycle destroy
     */
    fun post(r: Runnable): Boolean {
        pendingRunnables.add(r)
        val wrappedRunnable = Runnable {
            try {
                r.run()
            } finally {
                pendingRunnables.remove(r)
            }
        }
        return handler.post(wrappedRunnable)
    }

    /**
     * Post a delayed runnable to be executed on the handler's thread
     * Automatically tracked and cancelled on lifecycle destroy
     */
    fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        pendingRunnables.add(r)
        val wrappedRunnable = Runnable {
            try {
                r.run()
            } finally {
                pendingRunnables.remove(r)
            }
        }
        return handler.postDelayed(wrappedRunnable, delayMillis)
    }

    /**
     * Remove all callbacks for a specific runnable
     */
    fun removeCallbacks(r: Runnable) {
        handler.removeCallbacks(r)
        pendingRunnables.remove(r)
    }

    /**
     * Remove all pending callbacks and messages
     */
    fun removeCallbacksAndMessages() {
        handler.removeCallbacksAndMessages(null)
        pendingRunnables.clear()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // Cancel all pending operations when lifecycle is destroyed
        pendingRunnables.forEach { handler.removeCallbacks(it) }
        pendingRunnables.clear()
    }
}
