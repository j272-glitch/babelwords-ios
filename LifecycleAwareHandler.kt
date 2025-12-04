package com.lingualink.linguagt

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Handler that automatically cancels pending operations when lifecycle is destroyed
 */
class LifecycleAwareHandler(
    lifecycleOwner: LifecycleOwner,
    looper: Looper = Looper.getMainLooper()
) : Handler(looper), DefaultLifecycleObserver {
    
    private val pendingRunnables = mutableSetOf<Runnable>()
    
    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }
    
    override fun post(r: Runnable): Boolean {
        pendingRunnables.add(r)
        val wrappedRunnable = Runnable {
            try {
                r.run()
            } finally {
                pendingRunnables.remove(r)
            }
        }
        return super.post(wrappedRunnable)
    }
    
    override fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        pendingRunnables.add(r)
        val wrappedRunnable = Runnable {
            try {
                r.run()
            } finally {
                pendingRunnables.remove(r)
            }
        }
        return super.postDelayed(wrappedRunnable, delayMillis)
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        // Cancel all pending operations
        pendingRunnables.forEach { removeCallbacks(it) }
        pendingRunnables.clear()
    }
}
