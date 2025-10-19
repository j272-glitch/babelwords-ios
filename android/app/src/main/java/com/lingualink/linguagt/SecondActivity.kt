package com.lingualink.translator

import android.os.Bundle

/**
 * Example of another activity that extends BaseActivity
 * This automatically includes TesterMobLib tracking functionality
 */
class SecondActivity : BaseActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Your activity-specific initialization code here
        // The tracking methods (onStart, onStop, onResume, onPause) are automatically inherited from BaseActivity
        
        // Example: Set content view, initialize UI, etc.
        // setContentView(R.layout.activity_second)
    }
    
    // No need to override onStart() and onStop() - they're handled by BaseActivity
    // But you can still override them if you need activity-specific behavior:
    
    /*
    override fun onStart() {
        super.onStart() // This calls BaseActivity.onStart() which handles tracking
        // Add any SecondActivity-specific onStart code here
    }
    
    override fun onStop() {
        super.onStop() // This calls BaseActivity.onStop() which handles tracking
        // Add any SecondActivity-specific onStop code here
    }
    */
}