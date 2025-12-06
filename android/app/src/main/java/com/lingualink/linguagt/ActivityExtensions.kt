package com.lingualink.linguagt

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Extension functions for safe UI operations during automated testing
 * 
 * TESTRIGOR FIX: Route through Handler for safer execution to avoid IllegalStateException
 */

/**
 * Safely post to UI thread with activity lifecycle check
 * TESTRIGOR FIX: Uses Handler to avoid potential IllegalStateException when WebView is mid-destruction
 */
fun Activity.safeRunOnUiThread(action: () -> Unit) {
    if (isFinishing || isDestroyed) {
        Log.w("SafeActivity", "safeRunOnUiThread skipped - activity finishing/destroyed")
        return
    }
    
    // TESTRIGOR FIX: Route through Handler for safer execution
    val handler = Handler(Looper.getMainLooper())
    handler.post {
        if (!isFinishing && !isDestroyed) {
            try {
                action()
            } catch (e: Exception) {
                Log.e("SafeActivity", "Error in safeRunOnUiThread", e)
                TestRigorLogger.logError("safeRunOnUiThread", e)
            }
        } else {
            Log.w("SafeActivity", "safeRunOnUiThread action skipped - activity became invalid")
        }
    }
}

/**
 * Safely post delayed action with lifecycle check
 */
fun Activity.safePostDelayed(delayMillis: Long, action: () -> Unit) {
    if (!isFinishing && !isDestroyed) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                try {
                    action()
                } catch (e: Exception) {
                    Log.e("SafeActivity", "Error in delayed action", e)
                }
            }
        }, delayMillis)
    }
}

/**
 * Safely find view by ID with null check
 */
inline fun <reified T : android.view.View> Activity.safeFindViewById(id: Int): T? {
    return if (!isFinishing && !isDestroyed) {
        try {
            findViewById<T>(id)
        } catch (e: Exception) {
            Log.e("SafeActivity", "Error finding view $id", e)
            null
        }
    } else {
        null
    }
}

/**
 * Check if environment is ready for automated testing
 */
fun Activity.checkTestReadiness(): Boolean {
    val checks = mapOf(
        "Activity alive" to (!isFinishing && !isDestroyed),
        "Window attached" to (window?.decorView?.isAttachedToWindow == true),
        "Main thread" to (Looper.myLooper() == Looper.getMainLooper()),
        "Has window focus" to hasWindowFocus()
    )

    checks.forEach { (name, passed) ->
        Log.d("TestReadiness", "$name: ${if (passed) "✓" else "✗"}")
    }

    return checks.all { it.value }
}
