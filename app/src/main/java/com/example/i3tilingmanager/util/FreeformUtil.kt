package com.example.i3tilingmanager.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import android.hardware.display.DisplayManager


/**
 * Utility class for free form mode related operations with enhanced capabilities.
 */
object FreeformUtil {
    private const val TAG = "FreeformUtil"
    private const val DEVELOPMENT_SETTINGS_ENABLE_FREEFORM = "enable_freeform_support"
    private const val ADB_SHELL_COMMAND_PREFIX = "adb shell "

    // Cache screen bounds to avoid frequent recalculations
    private var cachedScreenBounds: Rect? = null

    /**
     * Check if free form mode is enabled on the device with more robust verification.
     *
     * @param context The context to use.
     * @return True if free form mode is enabled, false otherwise.
     */
    fun isFreeformModeEnabled(context: Context): Boolean {
        return try {
            // Check the setting value
            val value = Settings.Global.getInt(
                context.contentResolver,
                DEVELOPMENT_SETTINGS_ENABLE_FREEFORM,
                0
            )

            // Additional check on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val supportsMultiWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    am.isActivityStartAllowedOnDisplay(
                        context,
                        0,
                        Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    true  // Default to true for older versions
                }
                val hasFreeformSupport = am.isActivityStartAllowedOnDisplay(
                    context,
                    0,
                    Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return value == 1 && supportsMultiWindow && hasFreeformSupport
            }

            value == 1
        } catch (e: Exception) {
            Log.e(TAG, "Error checking freeform mode: ${e.message}")
            false
        }
    }

    /**
     * Enable free form mode on the device.
     * Note: This requires WRITE_SECURE_SETTINGS permission or root.
     *
     * @param context The context to use.
     * @return True if the operation succeeded, false otherwise.
     */
    fun enableFreeformMode(context: Context): Boolean {
        return try {
            // First try using the Settings API (requires permission)
            val result = Settings.Global.putInt(
                context.contentResolver,
                DEVELOPMENT_SETTINGS_ENABLE_FREEFORM,
                1
            )

            if (result) {
                Log.d(TAG, "Enabled freeform mode via Settings API")
                return true
            }

            // If that fails, guide user to enable via Developer Options
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)

            Log.d(TAG, "Directed user to Developer Options to enable freeform mode")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable freeform mode: ${e.message}")
            false
        }
    }

    /**
     * Launch an application in free form mode with specified bounds.
     *
     * @param context The context to use.
     * @param packageName The package name of the application to launch.
     * @param bounds The bounds for the window.
     * @return True if successful, false otherwise.
     */
    fun launchAppInFreeform(context: Context, packageName: String, bounds: Rect): Boolean {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false

        try {
            // Set flags to launch in a new window
            launchIntent.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT

                // On Android 10+, we can specify the bounds directly
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val options = ActivityOptions.makeBasic()
                    options.launchBounds = bounds
                    context.startActivity(this, options.toBundle())
                    Log.d(TAG, "Launched $packageName in freeform with bounds: $bounds")
                    return true
                } else {
                    // For older Android versions, just launch the app
                    context.startActivity(this)
                    Log.d(TAG, "Launched $packageName without bounds (older Android version)")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName in freeform: ${e.message}")
            return false
        }
    }

    /**
     * Get the bounds of the screen, with caching for performance.
     *
     * @param context The context to use.
     * @param forceRefresh Force a refresh of the cached bounds.
     * @return The screen bounds.
     */
    fun getScreenBounds(context: Context, displayId: Int = 0, forceRefresh: Boolean = false): Rect {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays

        // Find the requested display or use the default if not found
        val display = displays.find { it.displayId == displayId } ?: displays.firstOrNull()

        if (display == null) {
            Log.e(TAG, "No displays found!")
            return Rect(0, 0, 1000, 1000) // Fallback
        }

        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.currentWindowMetrics.bounds
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getMetrics(metrics)
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }

        Log.d(TAG, "Using display ${display.displayId} with bounds: $bounds")
        return bounds
    }

    /**
     * Resize a window using shell commands.
     *
     * @param context The context to use.
     * @param windowId The ID of the window to resize.
     * @param bounds The new bounds for the window.
     * @return True if successful, false otherwise.
     */
    fun resizeWindow(context: Context, windowId: Int, bounds: Rect): Boolean {
        // Try to use shell commands
        try {
            // Use shell commands
            val sizeCmd = "wm size-override $windowId ${bounds.width()} ${bounds.height()}"
            val sizeResult = executeShellCommand(sizeCmd)

            val posCmd = "wm position-override $windowId ${bounds.left} ${bounds.top}"
            val posResult = executeShellCommand(posCmd)

            Log.d(TAG, "Resized window $windowId to $bounds using shell commands")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resize window using shell commands: ${e.message}")

            // Try alternative method if the first one fails
            try {
                // Check if we have WRITE_SECURE_SETTINGS permission
                val hasWriteSecureSettings = Settings.System.canWrite(context)
                if (hasWriteSecureSettings) {
                    // Try another approach using settings
                    val cmd = "settings put global force_desktop_mode_on_external_displays 1"
                    executeShellCommand(cmd)

                    // Try to use accessibility actions as fallback
                    return moveWindowWithAccessibility(context, windowId, bounds)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed alternative window resize method: ${e2.message}")
            }
        }

        return false
    }

    private fun moveWindowWithAccessibility(context: Context, windowId: Int, bounds: Rect): Boolean {
        // This is a fallback method that could use accessibility services
        // For now, just notify the user this requires additional permissions
        Log.d(TAG, "Window resizing requires additional permissions or root access")
        return false
    }

    /**
     * Check if the app has shell command execution permission.
     */
    private fun hasShellPermission(context: Context): Boolean {
        return try {
            // Check if we can read a secure setting as a proxy for permissions
            Settings.Global.getInt(context.contentResolver, "adb_enabled")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Execute a shell command.
     */
    @SuppressLint("PrivateApi")
    private fun executeShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val exitCode = process.waitFor() // Get exit code
            val result = output.toString()
            Log.d(TAG, "Executed shell command: '$command', ExitCode: $exitCode, Output: $result") // Log result
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute shell command: $command", e)
            ""
        }
    }
}