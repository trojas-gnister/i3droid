package com.example.i3tilingmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.i3tilingmanager.I3TilingManagerApplication
import com.example.i3tilingmanager.service.TilingManagerService
import com.example.i3tilingmanager.util.AccessibilityUtil

/**
 * Broadcast receiver that starts the tiling manager service when the device boots up.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    private val TAG = "BootCompletedReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed")
            
            // Get application instance and settings
            val app = context.applicationContext as? I3TilingManagerApplication
            if (app != null && app.appSettings.autoStartOnBoot.value) {
                // Check if the accessibility service is enabled
                val isServiceEnabled = AccessibilityUtil.isAccessibilityServiceEnabled(
                    context,
                    TilingManagerService::class.java
                )
                
                if (isServiceEnabled) {
                    Log.d(TAG, "Starting tiling manager service")
                    // The service will start automatically since it's enabled in accessibility settings
                } else {
                    Log.d(TAG, "Tiling manager service is not enabled in accessibility settings")
                }
            }
        }
    }
}
