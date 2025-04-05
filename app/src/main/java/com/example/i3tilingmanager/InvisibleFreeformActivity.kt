package com.i3droid

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.WindowManager

/**
 * Invisible activity used to enable freeform mode
 * This is a critical component required for freeform mode to work properly
 */
class InvisibleFreeformActivity : Activity() {

    companion object {
        const val TAG = "InvisibleFreeform"
        const val ACTION_FINISH = "com.i3droid.ACTION_FINISH_FREEFORM"
    }

    private var inFreeformMode = false

    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        // Make the activity invisible but still maintain its window
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )

        // Register receiver to listen for finish commands
        // FIX: Add RECEIVER_NOT_EXPORTED flag for Android 14+ compatibility
        registerReceiver(
            finishReceiver,
            IntentFilter(ACTION_FINISH),
            Context.RECEIVER_NOT_EXPORTED
        )

        // Set freeform mode as active
        inFreeformMode = true
        FreeformHelper.getInstance().isFreeformActive = true
    }

    override fun onResume() {
        super.onResume()
        // Notify that we're in freeform mode
        FreeformHelper.getInstance().isInFreeformWorkspace = true
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(finishReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }

        // Clean up
        FreeformHelper.getInstance().isFreeformActive = false
        FreeformHelper.getInstance().isInFreeformWorkspace = false
    }
}