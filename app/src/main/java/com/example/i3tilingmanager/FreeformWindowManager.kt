package com.i3droid

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

/**
 * Manages launching applications in freeform window mode,
 * handling window positioning and sizing.
 */
class FreeformWindowManager(private val context: Context) {

    companion object {
        private const val TAG = "FreeformWM"

        // Constants for window sizes
        private const val DEFAULT_WIDTH_DP = 360
        private const val DEFAULT_HEIGHT_DP = 640
        private const val MARGIN_DP = 25
    }

    // Keep track of open windows to manage positioning
    private val openWindows = mutableListOf<Rect>()

    /**
     * Check if the device supports freeform mode
     */
    fun hasFreeformSupport(): Boolean {
        // Check if on API 24 (Nougat) or higher
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        return try {
            // Check if the device has the freeform feature flag
            val hasFreeformFeature = context.packageManager.hasSystemFeature(
                "android.software.freeform_window_management"
            )

            // Check if enable_freeform_support is enabled in Global settings
            val freeformEnabled = Settings.Global.getInt(
                context.contentResolver,
                "enable_freeform_support",
                0
            ) != 0

            // On Nougat, also check force_resizable_activities
            val forceResizable = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                Settings.Global.getInt(
                    context.contentResolver,
                    "force_resizable_activities",
                    0
                ) != 0
            } else false

            hasFreeformFeature || freeformEnabled || forceResizable
        } catch (e: Exception) {
            Log.e(TAG, "Error checking freeform support", e)
            false
        }
    }

    /**
     * Start the freeform hack to enable freeform mode
     */
    fun startFreeformHack() {
        if (!hasFreeformSupport()) {
            Log.e(TAG, "Freeform mode not supported on this device")
            return
        }

        if (FreeformHelper.getInstance().isFreeformActive) {
            Log.d(TAG, "Freeform hack already active")
            return
        }

        val intent = Intent(context, InvisibleFreeformActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }

        try {
            // Position the invisible activity in the corner
            val options = ActivityOptions.makeBasic()
            val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay
            val metrics = DisplayMetrics()
            display.getMetrics(metrics)

            // Place activity in bottom-right corner
            val bounds = Rect(
                metrics.widthPixels - 1,
                metrics.heightPixels - 1,
                metrics.widthPixels,
                metrics.heightPixels
            )
            options.launchBounds = bounds

            context.startActivity(intent, options.toBundle())
            Log.d(TAG, "Started freeform hack activity")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start freeform hack", e)
        }
    }

    /**
     * Stop the freeform hack
     */
    fun stopFreeformHack() {
        val intent = Intent(InvisibleFreeformActivity.ACTION_FINISH)
        context.sendBroadcast(intent)
    }

    /**
     * Convert dp to pixels
     */
    private fun dpToPx(dp: Int): Int {
        val displayMetrics = context.resources.displayMetrics
        val px = (dp * displayMetrics.density).toInt()
        return px
    }

    /**
     * Launch an app in freeform mode
     *
     * @param packageName Package name of the app to launch
     * @param activityName Activity name of the app to launch
     */
    fun launchAppInFreeformMode(packageName: String, activityName: String) {
        Log.d(TAG, "Attempting to launch $packageName/$activityName in freeform mode")

        if (!hasFreeformSupport()) {
            Log.e(TAG, "Freeform mode not supported on this device")
            return
        }

        // First ensure freeform hack is active
        if (!FreeformHelper.getInstance().isFreeformActive) {
            startFreeformHack()
            // Wait a bit for the freeform hack to initialize
            Handler(Looper.getMainLooper()).postDelayed({
                actuallyLaunchApp(packageName, activityName)
            }, 300)
        } else {
            actuallyLaunchApp(packageName, activityName)
        }
    }

    private fun actuallyLaunchApp(packageName: String, activityName: String) {
        // Create intent to launch the app
        val intent = Intent().apply {
            component = ComponentName(packageName, activityName)
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Critical flags for freeform mode
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            }
        }

        // Create a bounds rectangle for the app window
        val windowRect = getNextWindowBounds()

        if (windowRect.width() <= 0 || windowRect.height() <= 0) {
            Log.e(TAG, "Calculated bounds are invalid: $windowRect")
            return
        }

        // Create activity options with bounds
        val options = ActivityOptions.makeBasic()

        // FIXED: Don't try to use reflection to set windowing mode
        // Instead, rely solely on setting bounds which works better across Android versions

        // Set the launch bounds
        options.launchBounds = windowRect

        // Start the activity with options
        try {
            context.startActivity(intent, options.toBundle())
            Log.d(TAG, "Successfully launched app in freeform mode")

            // Add to list of open windows
            openWindows.add(windowRect)

            // Keep list at reasonable size
            if (openWindows.size > 10) {
                openWindows.removeAt(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching activity in freeform mode", e)
        }
    }

    /**
     * Determine the position for the next window
     * Uses a cascading layout pattern
     */
    private fun getNextWindowBounds(): Rect {
        val displayMetrics = context.resources.displayMetrics

        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val windowWidth = dpToPx(DEFAULT_WIDTH_DP)
        val windowHeight = dpToPx(DEFAULT_HEIGHT_DP)

        // Ensure calculated size isn't larger than screen
        val finalWindowWidth = windowWidth.coerceAtMost(screenWidth - 100)
        val finalWindowHeight = windowHeight.coerceAtMost(screenHeight - 100)

        // Calculate offset for cascading windows
        val marginPx = dpToPx(MARGIN_DP)

        val maxOffsetX = (screenWidth - finalWindowWidth).coerceAtLeast(1)
        val maxOffsetY = (screenHeight - finalWindowHeight).coerceAtLeast(1)

        val offsetX = (openWindows.size * marginPx) % maxOffsetX
        val offsetY = (openWindows.size * marginPx) % maxOffsetY

        return Rect(
            offsetX,
            offsetY,
            offsetX + finalWindowWidth,
            offsetY + finalWindowHeight
        )
    }

    /**
     * Reset the window tracking list
     */
    fun resetWindows() {
        openWindows.clear()
    }
}