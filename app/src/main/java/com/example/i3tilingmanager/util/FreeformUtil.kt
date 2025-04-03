package com.example.i3tilingmanager.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Utility class for free form mode related operations.
 */
object FreeformUtil {

    private const val DEVELOPMENT_SETTINGS_ENABLE_FREEFORM = "enable_freeform_support"

    /**
     * Check if free form mode is enabled on the device.
     *
     * @param context The context to use.
     * @return True if free form mode is enabled, false otherwise.
     */
    fun isFreeformModeEnabled(context: Context): Boolean {
        return try {
            val value = Settings.Global.getInt(
                context.contentResolver,
                DEVELOPMENT_SETTINGS_ENABLE_FREEFORM,
                0
            )
            value == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Enable free form mode on the device.
     * Note: This requires WRITE_SECURE_SETTINGS permission which is only available
     * to system apps or via ADB.
     *
     * @param context The context to use.
     * @return True if the operation succeeded, false otherwise.
     */
    fun enableFreeformMode(context: Context): Boolean {
        return try {
            Settings.Global.putInt(
                context.contentResolver,
                DEVELOPMENT_SETTINGS_ENABLE_FREEFORM,
                1
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launch an application in free form mode with specified bounds.
     *
     * @param context The context to use.
     * @param packageName The package name of the application to launch.
     * @param bounds The bounds for the window.
     */
    fun launchAppInFreeform(context: Context, packageName: String, bounds: Rect) {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return

        // Set flags to launch in a new window
        launchIntent.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT

            // On Android 10+, we can specify the bounds directly
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val options = android.app.ActivityOptions.makeBasic()
                options.launchBounds = bounds
                context.startActivity(this, options.toBundle())
            } else {
                // For older Android versions, just launch the app
                context.startActivity(this)
            }
        }
    }

    /**
     * Get the bounds of the screen.
     *
     * @param context The context to use.
     * @return The screen bounds.
     */
    fun getScreenBounds(context: Context): Rect {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return Rect(bounds)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }
}