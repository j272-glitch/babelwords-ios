package com.lingualink.linguagt

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.Toast

/**
 * Create a crash-safe click listener for automated testing
 */
class SafeClickListener(
    private val activity: Activity,
    private val action: () -> Unit
) : View.OnClickListener {
    
    override fun onClick(v: View?) {
        try {
            // Check activity state
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w("SafeClickListener", "Ignoring click - activity is finishing/destroyed")
                return
            }
            
            // Execute the action
            action()
            
        } catch (e: Exception) {
            Log.e("SafeClickListener", "Click handler exception", e)
            
            // Show user-friendly error
            try {
                Toast.makeText(
                    activity,
                    "Action unavailable",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (toastError: Exception) {
                Log.e("SafeClickListener", "Could not show error toast", toastError)
            }
        }
    }
}
