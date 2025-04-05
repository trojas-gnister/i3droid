package com.i3droid

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.Log // <-- Add this import
import android.view.WindowManager

/**
 * Manages launching applications in freeform window mode,
 * handling window positioning and sizing.
 */
class FreeformWindowManager(private val context: Context) {

    // Constants for window sizes
    private val DEFAULT_WIDTH_DP = 360
    private val DEFAULT_HEIGHT_DP = 640
    private val MARGIN_DP = 25

    // Keep track of open windows to manage positioning
    private val openWindows = mutableListOf<Rect>()

    /**
     * Convert dp to pixels
     */
    private fun dpToPx(dp: Int): Int {
        val displayMetrics = context.resources.displayMetrics
        // Use density directly for more standard conversion
        val px = (dp * displayMetrics.density).toInt()
        Log.d("FreeformWM", "dpToPx: $dp dp -> $px px (density=${displayMetrics.density})")
        return px
    }

    /**
     * Launch an app in freeform mode
     *
     * @param packageName Package name of the app to launch
     * @param activityName Activity name of the app to launch
     */
    fun launchAppInFreeformMode(packageName: String, activityName: String) {
        Log.d("FreeformWM", "--- Attempting to launch $packageName/$activityName ---")
        // Create intent to launch the app
        val intent = Intent().apply {
            component = ComponentName(packageName, activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }

        // Create a bounds rectangle for the app window
        val windowRect = getNextWindowBounds()
        Log.d("FreeformWM", "Calculated launchBounds: $windowRect")


        if (windowRect.width() <= 0 || windowRect.height() <= 0) {
            Log.e("FreeformWM", "Calculated bounds are invalid! Not launching.")
            return // Prevent launching with invalid bounds
        }

        // Create activity options with bounds
        val options = ActivityOptions.makeBasic()

        // Set the launch bounds (for freeform mode)
        options.launchBounds = windowRect

        // Start the activity with options
        try {
            Log.d("FreeformWM", "Calling startActivity with options...")
            context.startActivity(intent, options.toBundle())
            Log.d("FreeformWM", "startActivity called successfully.")

            // Add to list of open windows *only after successful call*
            openWindows.add(windowRect)

            // Prevent list from growing too large
            if (openWindows.size > 10) {
                openWindows.removeAt(0)
            }
        } catch (e: Exception) {
            Log.e("FreeformWM", "Error launching activity: ${e.message}", e)
        }
        Log.d("FreeformWM", "--- Launch attempt finished ---")
    }

    /**
     * Determine the position for the next window
     * Uses a cascading layout pattern
     */
    private fun getNextWindowBounds(): Rect {
        Log.d("FreeformWM", "Calculating next window bounds...")
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        // It's generally safer to get metrics from the default display via WindowManager
        // on newer APIs, but context.resources.displayMetrics often works too.
        // Let's stick to context resources for now as it was in the original code.
        // windowManager.defaultDisplay.getMetrics(metrics) // Alternative
        val displayMetrics = context.resources.displayMetrics
        Log.d("FreeformWM", "Screen Metrics: width=${displayMetrics.widthPixels}, height=${displayMetrics.heightPixels}, density=${displayMetrics.density}, xdpi=${displayMetrics.xdpi}")


        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val windowWidth = dpToPx(DEFAULT_WIDTH_DP)
        val windowHeight = dpToPx(DEFAULT_HEIGHT_DP)
        Log.d("FreeformWM", "Target window size: width=$windowWidth px, height=$windowHeight px")


        // Ensure calculated size isn't larger than screen
        val finalWindowWidth = windowWidth.coerceAtMost(screenWidth)
        val finalWindowHeight = windowHeight.coerceAtMost(screenHeight)
        if (windowWidth > screenWidth || windowHeight > screenHeight) {
            Log.w("FreeformWM", "Calculated window size ($windowWidth x $windowHeight) larger than screen ($screenWidth x $screenHeight). Coercing.")
        }

        // Calculate offset for cascading windows
        val marginPx = dpToPx(MARGIN_DP)
        Log.d("FreeformWM", "Cascade margin: $marginPx px")

        // Ensure we have space to calculate offset, avoid division by zero or negative modulo
        val maxOffsetX = screenWidth - finalWindowWidth
        val maxOffsetY = screenHeight - finalWindowHeight

        val offsetX = if (maxOffsetX > 0) (openWindows.size * marginPx) % maxOffsetX else 0
        val offsetY = if (maxOffsetY > 0) (openWindows.size * marginPx) % maxOffsetY else 0
        Log.d("FreeformWM", "Calculated offset: x=$offsetX, y=$offsetY (based on ${openWindows.size} open windows)")


        return Rect(
            offsetX,
            offsetY,
            (offsetX + finalWindowWidth).coerceAtMost(screenWidth), // Ensure right edge doesn't exceed screen
            (offsetY + finalWindowHeight).coerceAtMost(screenHeight) // Ensure bottom edge doesn't exceed screen
        )
    }

    /**
     * Reset the window tracking list
     */
    fun resetWindows() {
        Log.d("FreeformWM", "Resetting open windows list.")
        openWindows.clear()
    }
}